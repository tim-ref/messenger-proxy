#
# Modified by akquinet GmbH on 13.01.2025
#
# Originally from https://github.com/matrix-org/sytest
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#
# See the License for the specific language governing permissions and
# limitations under the License.

# adapted from: https://github.com/matrix-org/sytest/blob/develop/lib/SyTest/Homeserver/Synapse.pm

package SyTest::Homeserver::MessengerProxy;

use strict;
use warnings;
use 5.010;
use base qw(SyTest::Homeserver);

use Carp;
use Socket qw(pack_sockaddr_un);

use Future::Utils qw(try_repeat);

use IO::Async::Process;
use IO::Async::FileStream;

use Cwd qw(getcwd);
use File::Basename qw(dirname);
use File::Path qw(remove_tree make_path);
use File::Slurper qw(write_binary);
use List::Util qw(any);
use POSIX qw(strftime WIFEXITED WEXITSTATUS);

use JSON;

use SyTest::Federation::Client

*SyTest::Federation::Client::SUPPORTED_ROOM_VERSIONS = sub {
    return [
        "9",
        "10",
    ];
};

use SyTest::SSL qw(ensure_ssl_key create_ssl_cert);

sub _init {
    my $self = shift;
    my ($args) = @_;

    $self->{$_} = delete $args->{$_} for qw(
        synapse_dir messenger_proxy_dir extra_args python
    );

    $self->{paths} = {};

    $self->SUPER::_init($args);

    my $idx = $self->{hs_index};
    $self->{ports} = {
        synapse                    => main::alloc_port("synapse[$idx]"),
        synapse_metrics            => main::alloc_port("synapse[$idx].metrics"),

        nginx_reverse_proxy        => main::alloc_port("nginx_reverse_proxy[$idx]"),

        messenger_proxy_inbound    => main::alloc_port("messenger_proxy[$idx].inbound"),
        messenger_proxy_outbound   => main::alloc_port("messenger_proxy[$idx].outbound"),
        messenger_proxy_health     => main::alloc_port("messenger_proxy[$idx].health"),
        messenger_proxy_prometheus => main::alloc_port("messenger_proxy[$idx].prometheus"),
        messenger_proxy_contactManagement => main::alloc_port("messenger_proxy[$idx].contactManagement"),
    };
}

sub configure {
    my $self = shift;
    my %params = @_;

    $self->SUPER::configure(%params);
}

sub start {
    my $self = shift;

    my $hs_index = $self->{hs_index};
    my $port = $self->{ports}{synapse};
    my $output = $self->{output};

    my $hs_dir = $self->{hs_dir};

    my %db_configs = $self->_get_dbconfigs(
        type => 'sqlite',
        args => {
            database => ":memory:", #"$hs_dir/homeserver.db (comment from Sytest)",
        },
    );

    # convert sytest db args onto synapse db args (comment from Sytest)
    for my $db (keys %db_configs) {
        my %db_config = %{$db_configs{$db}};

        $db_configs{$db}{name} = 'sqlite3';
    }

    # Clean up the media_store directory each time, or else it fills up with
    # thousands of automatically-generated avatar images
    if (-d "$hs_dir/media_store") {
        remove_tree("$hs_dir/media_store");
    }

    if (-d "$hs_dir/uploads") {
        remove_tree("$hs_dir/uploads");
    }

    my $cwd = getcwd;
    my $log = "$hs_dir/homeserver.log";

    my $listeners = [ $self->generate_listeners ];
    my $bind_host = $self->{bind_host};

    my $macaroon_secret_key = "";
    my $form_secret = "";
    my $registration_shared_secret = "reg_secret";

    -d "$hs_dir/certificates" or make_path "$hs_dir/certificates";

    # Don't generate certificates if we don't use them
    # $self->{paths}{cert_file} = "$hs_dir/certificates/synapse.crt";
    # $self->{paths}{key_file} = "$hs_dir/certificates/synapse.key";
    #
    # ensure_ssl_key($self->{paths}{key_file});
    # create_ssl_cert($self->{paths}{cert_file}, $self->{paths}{key_file}, $bind_host);

    # make it possible to use a custom log config file
    my $log_config_file = "$hs_dir/log.config";
    if (!-f $log_config_file) {
        $log_config_file = $self->configure_logger("homeserver");
    }

    # config.yaml values are set like homeserver.yaml in production
    my $config_path = $self->{paths}{config} = $self->write_yaml_file("config.yaml" => {
        server_name                                         => $self->server_name,
        serve_server_wellknown                              => JSON::true,
        soft_file_limit                                     => 0,
        presence                                            => {
           enabled => JSON::true,
        },
        require_auth_for_profile_requests                   => JSON::false,
        limit_profile_requests_to_users_who_share_rooms     => JSON::false,
        include_profile_data_on_invite                      => JSON::true,
        default_room_version                                => "10",
        gc_min_interval                                     => ["1s", "10s", "30s"],
        filter_timeline_limit                               => 100,
        block_non_admin_invites                             => JSON::false,
        enable_search                                       => JSON::true,
        dummy_events_threshold                              => 10,
        delete_stale_devices_after                          => "1y",

        hs_disable                                          => JSON::false,
        hs_disabled_message                                 => "Reason for why the HS is blocked",
        log_config                                          => $log_config_file,
        public_baseurl                                      => $self->public_baseurl,
        allow_per_room_profiles                             => JSON::true,
        redaction_retention_period                          => "7d",
        user_ips_max_age                                    => "28d",
        request_token_inhibit_3pid_errors                   => JSON::false,
        allow_profile_lookup_over_federation                => JSON::true,
        event_cache_size                                    => "10K",

        # We don't use TLS on the connection from the proxy to synapse
        # We configure synapse to use a TLS cert which is signed by our dummy CA...
        #tls_certificate_path => $self->{paths}{cert_file},
        #tls_private_key_path => $self->{paths}{key_file},

        # ... and configure it to trust that CA for federation connections...
        federation_custom_ca_list                           => [
            "$cwd/keys/ca.crt",
        ],

        # ... but synapse currently lacks such an option for non-federation
        # connections. Instead we just turn of cert checking for them like
        # this:
        use_insecure_ssl_client_just_for_testing_do_not_use => JSON::true,
        federation_verify_certificates                      => JSON::true,
        federation_client_minimum_tls_version               => 1,

        # raise limits for rate limiting
        rc_messages_per_second                              => 1000,
        rc_message_burst_count                              => 1000,
        rc_registration                                     => {
            per_second  => 1000,
            burst_count => 1000,
        },
        rc_login                                            => {
            address         => {
                per_second  => 1000,
                burst_count => 1000,
            },
            account         => {
                per_second  => 1000,
                burst_count => 1000,
            },
            failed_attempts => {
                per_second  => 1000,
                burst_count => 1000,
            }
        },

        rc_federation                                       => {
            sleep_limit => 100,
            window_size => 1000,
        },

        rc_joins                                            => {
            local  => {
                per_second  => 1000,
                burst_count => 1000,
            },
            remote => {
                per_second  => 1000,
                burst_count => 1000,
            },
        },

        federation_rr_transactions_per_room_per_second      => 50,
        enable_registration                                 => JSON::true, # these values must be set to true because Sytest uses registration
        enable_registration_without_verification            => JSON::true, # these values must be set to true because Sytest uses registration
        enable_3pid_lookup                                  => JSON::true,
        enable_set_displayname                              => JSON::true, # these value must be set to true because Sytest uses changing of the display name
        enable_set_avatar_url                               => JSON::true,
        registration_requires_token                         => JSON::false,
        enable_3pid_changes                                 => JSON::true,
        autocreate_auto_join_rooms                          => JSON::true,
        autocreate_auto_join_rooms_federated                => JSON::true,
        autocreate_auto_join_room_preset                    => "public_chat",
        auto_join_mxid_localpart                            => "system",
        auto_join_rooms_for_guests                          => JSON::true,
        inhibit_user_in_use_error                           => JSON::false,
        databases                                           => \%db_configs,
        macaroon_secret_key                                 => $macaroon_secret_key,
        form_secret                                         => $form_secret,
        registration_shared_secret                          => $registration_shared_secret,

        refreshable_access_token_lifetime                   => "5m",

#       signing_key_path                                    => ... # path is constructed per default in the right way
        key_refresh_interval                                => "1d",
        suppress_key_server_warning                         => JSON::false,

        encryption_enabled_by_default_for_room_type         => "all",

        user_directory                                      => {
            enabled             => JSON::true,
            search_all_users    => JSON::true,
            prefer_local_users  => JSON::true,
        },

        push                                                => {
           include_content            => JSON::true,
           group_unread_count_by_room => JSON::true,
        },

        pid_file                                            => "$hs_dir/homeserver.pid",

        allow_guest_access                                  => JSON::false,

        # Metrics are always useful (comment from Sytest)
        enable_metrics                                      => JSON::true,
        enable_legacy_metrics                               => JSON::false,
        report_stats                                        => JSON::true,

        track_puppeted_user_ips                             => JSON::false,
        track_appservice_user_ips                           => JSON::false,

        listeners                                           => $listeners,

        # we reduce the number of bcrypt rounds to make generating users
        # faster, but note that python's bcrypt complains if rounds < 4,
        # so this is effectively the minimum. (comment from Sytest)
        bcrypt_rounds                                       => 4,

        # We remove the ip range blacklist which by default blocks federation
        # connections to local homeservers, of which sytest uses extensively (comment from Sytest)
        ip_range_blacklist                                  => [],
        federation_ip_range_blacklist                       => [],
        url_preview_ip_range_blacklist                      => [],

        send_federation                                     => JSON::true,
        enable_media_repo                                   => JSON::true,

        url_preview_enabled                                 => JSON::false,
        max_spider_size                                     => "10M",
        oembed                                              => {
            disable_default_providers => JSON::false,
        },

        media_store_path                                    => "$hs_dir/media_store",
        max_upload_size                                     => "120M",
        max_image_pixels                                    => "32M",
        dynamic_thumbnails                                  => JSON::false,
        media_retention                                     => {
            local_media_lifetime                            => "90d",
            remote_media_lifetime                           => "14d",
        },
        uploads_path                                        => "$hs_dir/uploads_path",

        allow_public_rooms_over_federation                  => JSON::false,
        allow_public_rooms_without_auth                     => JSON::false,

        user_agent_suffix                                   => "homeserver[" . $self->{hs_index} . "]",

        require_membership_for_aliases                      => JSON::true,

        limit_usage_by_mau                                  => JSON::false,
        max_mau_value                                       => 0,
        mau_trial_days                                      => 0,
        mau_limit_alerting                                  => JSON::true,
        mau_stats_only                                      => JSON::false,

        stats                                               => {
            enabled => JSON::true,
        },

        server_notices                                      => {
            system_mxid_localpart       => "notices",
            system_mxid_display_name    => "Server Notices",
            system_mxid_avatar_url      => "",
            room_name                   => "Server Notices",
        },

        enable_room_list_search                             => JSON::true,

        redis                                               => {},

        background_updates                                  => {
            background_update_duration_ms => 100,
            sleep_enabled                 => JSON::true,
            sleep_duration_ms             => 1000,
            min_batch_size                => 1,
            default_batch_size            => 100,
        },

        ### DISCLAIMER: From this point on these are necessary properties from the               ###
        ### original Sytest project which are not reflected from the productive homeserver.yaml  ###

        # Disable caching of sync responses to make tests easier. (comment from Sytest)
        caches                                              => {
            sync_response_cache_duration => 0,
        },

        $self->{recaptcha_config} ? (
            recaptcha_siteverify_api => $self->{recaptcha_config}->{siteverify_api},
            recaptcha_public_key     => $self->{recaptcha_config}->{public_key},
            recaptcha_private_key    => $self->{recaptcha_config}->{private_key},
        ) : (),

        $self->{smtp_server_config} ? (
            email => {
                smtp_host  => $self->{smtp_server_config}->{host},
                smtp_port  => $self->{smtp_server_config}->{port},
                notif_from => 'synapse@localhost',
            },
        ) : (),

        map {
            defined $self->{$_} ? ($_ => $self->{$_}) : ()
        } qw(
            replication_torture_level
            cas_config
            app_service_config_files
        ),
    });

    $self->{paths}{nginx_cert_file} = "$hs_dir/certificates/nginx.crt";
    $self->{paths}{nginx_cert_key_file} = "$hs_dir/certificates/nginx.key";

    ensure_ssl_key($self->{paths}{nginx_cert_key_file});
    create_ssl_cert($self->{paths}{nginx_cert_file}, $self->{paths}{nginx_cert_key_file}, $bind_host);

    my $nginx_config_path = $self->{paths}{nginx_config} = $self->write_file_abs("/etc/nginx/sites-enabled/server-$hs_index.conf" => "
upstream messenger_proxy_$hs_index {
    server localhost:$self->{ports}{messenger_proxy_inbound} max_fails=0;
}

# the nginx is necessary because the proxy is not designed to except HTTPS on incoming CONNECT requests
server {
    listen $self->{ports}{nginx_reverse_proxy} ssl http2;
    server_name _;

    ssl_certificate $self->{paths}{nginx_cert_file};
    ssl_certificate_key $self->{paths}{nginx_cert_key_file};

    location / {
        proxy_pass http://messenger_proxy_$hs_index;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Forwarded-Host \$http_host;
        proxy_set_header X-Forwarded-Server \$http_host;
        proxy_set_header X-Forwarded-Port $self->{ports}{nginx_reverse_proxy};
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    proxy_http_version 1.1;
}
    ");

    my $messenger_proxy_log = "$hs_dir/messenger-proxy.log";
    chomp(my $local_path = `pwd`);
    # use the SyTest CA directly
    $self->{paths}{messenger_proxy_cert_file} = "$local_path/keys/ca.crt";
    $self->{paths}{messenger_proxy_cert_key_file} = "$local_path/keys/ca.key";
    $self->{paths}{messenger_proxy_cert_private_key_file} = "$local_path/keys/ca.pem";

    my $messenger_proxy_config_path = $self->{paths}{messenger_proxy_config} = $self->write_yaml_file("application.yml" => {
        "prometheusClient" => {
            port                 => $self->{ports}{messenger_proxy_prometheus},
            enableDefaultExports => JSON::true
        },
        "healthEndpoint"   => {
            port => $self->{ports}{messenger_proxy_health}
        },
        "outboundProxy"    => {
            port              => $self->{ports}{messenger_proxy_outbound},
            enforceDomainList => JSON::false,
            baseDirectory     => "$hs_dir/certificates",
            caCertificateFile => $self->{paths}{messenger_proxy_cert_file},
            caPrivateKeyFile  => $self->{paths}{messenger_proxy_cert_private_key_file},
            domainWhiteList   => "",
            ssoDomain         => "" # this is not needed
        },
        "inboundProxy"     => {
            homeserverUrl                    => "http://$bind_host:$port",
            synapseHealthEndpoint            => "/health",
            synapsePort                      => $port,
            port                             => $self->{ports}{messenger_proxy_inbound},
            enforceDomainList                => JSON::false,
            accessTokenToUserIdCacheDuration => "1h"
        },
        "registrationServiceConfig"   => {
            baseUrl                         => "http://localhost",
            servicePort                     => "8080",
            healthPort                      => "8081",
            federationListEndpoint          => "/backend/federation",
            invitePermissionCheckEndpoint   => "/backend/vzd/invite",
            readinessEndpoint               => "/actuator/health/readiness"
        },
        "federationListCache"   => {
            baseDirectory           => "$hs_dir/federationList",
            file        => "$hs_dir/federationList/federationList.json",
            metaFile      => "$hs_dir/federationList/federationList-meta.json",
            updateIntervalMinutes => 1
        },
        "logInfoConfig" => {
            url          => "http://localhost:9020/add-performance-data",
            professionId => "",
            telematikId  => "",
            instanceId   => "",
            homeFQDN     => "home.de"
        },
        "contactManagement" => {
            port         => $self->{ports}{messenger_proxy_contactManagement}
        },
        "invitePermissionConfig" => {
            regApiUrl    => "http://localhost:8080/backend/vzd/invite"
        },
        "database" => {
             jdbcUrl         => "jdbc:postgresql://$bind_host:5432/pg$hs_index",
             dbUser          => "postgres",
             dbPassword      => ""
        },
        actuatorConfig => {
          port => $self->{ports}{messenger_proxy_health},
          basePath => "/actuator"
        },
        logLevelResetConfig => {
          logLevelResetDelayInSeconds => 5,
          resetLogLevel => "INFO"
        },
        timAuthorizationCheckConfiguration => {
          concept => "CLIENT",
          inviteRejectionPolicy => "ALLOW_ALL"
        }
    });

    $self->{paths}{log} = $log;
    $self->{paths}{messenger_proxy_log} = $messenger_proxy_log;

    {
        # create or truncate (comment from Sytest)
        open my $tmph1, ">", $log or die "Cannot open $log for writing - $!";
        open my $tmph2, ">", $messenger_proxy_log or die "Cannot open $messenger_proxy_log for writing - $!";
    }

    my @synapse_command = $self->_generate_base_synapse_command();

    $output->diag("Generating config for port $port");

    my @config_command = (
        @synapse_command, "--generate-config", "--report-stats=no",
        "--server-name", $self->server_name
    );

    my $env = {
        "PATH"                            => $ENV{PATH},
        "PYTHONDONTWRITEBYTECODE"         => "Don't write .pyc files",
        "SYNAPSE_TEST_PATCH_LOG_CONTEXTS" => 1,
        "HTTPS_PROXY"                     => $self->messenger_proxy_url_insecure,
        "HTTP_PROXY"                      => $self->messenger_proxy_url_insecure
    };

    my $nginx_reverse_proxy_env = {
        "PATH" => $ENV{PATH},
    };

    my $messenger_proxy_env = {
        "PATH"                    => $ENV{PATH},
        "JAVA_HOME"               => $ENV{JAVA_HOME},
        "CONFIGURATION_FILE_PATH" => $messenger_proxy_config_path,
    };

    my $loop = $self->loop;

    $output->diag(
        "Creating config for server $hs_index with command "
            . join(" ", @config_command),
    );

    return $self->_run_command(
        setup   => [ env => $env ],
        command => [ @config_command ],
    )->then(sub {
        $output->diag(
            "Starting synapse server $hs_index for port $port"
        );

        return $self->_start_synapse(env => $env);
    })->then(sub {
        $output->diag(
            "Starting messenger-proxy server $hs_index on health port " . $self->{ports}{messenger_proxy_health} . " outbound port " . $self->{ports}{messenger_proxy_outbound} . " inbound port " . $self->{ports}{messenger_proxy_inbound} . " contact management port " . $self->{ports}{messenger_proxy_contactManagement}
        );

        return $self->_start_messenger_proxy(log => $messenger_proxy_log, env => $messenger_proxy_env);
    })->then(sub {
        $output->diag(
            "Starting nginx server $hs_index for port " . $self->{ports}{nginx_reverse_proxy}
        );

        $self->_start_nginx(env => $nginx_reverse_proxy_env);
    })->on_done(sub {
        $output->diag("Started synapse + messenger-proxy + nginx $hs_index");
    });
}

sub _generate_base_synapse_command {
    my $self = shift;
    my %params = @_;

    my $app = $params{app} // "synapse.app.homeserver";

    my @synapse_command = ($self->{python});

    push @synapse_command,
        "-m", $app,
        "--config-path" => $self->{paths}{config};

    my @command = (
        @synapse_command,
        @{$self->{extra_args}},
    );

    return @command
}

sub _start_synapse {
    my $self = shift;
    my %params = @_;

    my $env = $params{env};

    my @synapse_command = $self->_generate_base_synapse_command();
    my $idx = $self->{hs_index};

    return $self->_start_process_and_await_notify(
        setup   => [ env => $env ],
        command => \@synapse_command,
        name    => "synapse-$idx-master",
    );
}

sub _start_nginx {
    my $self = shift;
    my %params = @_;

    my $env = $params{env};

    my $idx = $self->{hs_index};

    return $self->_start_process_and_await_connectable(
        connect_host => $self->{bind_host},
        connect_port => $self->{ports}{nginx_reverse_proxy},
        setup        => [ env => $env ],
        # The process needs to seem alive for the SyTest ProcessManager to work, even though the reload is non-blocking (comment from Sytest)
        command      => "/usr/sbin/nginx -s reload && sleep infinity",
        name         => "nginx-reverse-proxy-$idx"
    )
}

sub _start_messenger_proxy {
    my $self = shift;
    my %params = @_;

    my $env = $params{env};
    my $log = $params{log};

    my $idx = $self->{hs_index};

    return $self->_start_process_and_await_connectable(
        connect_host => $self->{bind_host},
        connect_port => $self->{ports}{messenger_proxy_inbound},
        setup        => [ env => $env ],
        command      => "java -Djava.security.properties==/messenger-proxy/java.security -Djdk.tls.namedGroups=brainpoolP384r1tls13,brainpoolP256r1tls13,brainpoolP384r1,brainpoolP256r1,secp384r1,secp256r1 -Dsun.security.ssl.allowLegacyHelloMessages=false -jar /messenger-proxy/mp-backend-jar-with-dependencies.jar >> " . $log,
        name         => "messenger-proxy-$idx"
    )
}

sub server_name {
    my $self = shift;
    return $self->{bind_host} . ":" . $self->{ports}{nginx_reverse_proxy};
}

sub federation_host {
    my $self = shift;
    return $self->{bind_host};
}

sub federation_port {
    my $self = shift;
    return $self->{ports}{messenger_proxy_inbound};
}

sub secure_port {
    my $self = shift;
    return $self->{ports}{synapse};
}

sub public_baseurl {
    my $self = shift;
    return "https://$self->{bind_host}:" . $self->{ports}{nginx_reverse_proxy};
}

sub messenger_proxy_url {
    my $self = shift;
    return "https://$self->{bind_host}:" . $self->{ports}{messenger_proxy_outbound}
}

sub messenger_proxy_url_insecure {
    my $self = shift;
    return "http://$self->{bind_host}:" . $self->{ports}{messenger_proxy_outbound}
}

sub generate_listeners {
    my $self = shift;

    return {
        type         => "http",
        port         => $self->{ports}{synapse},
        tls          => JSON::false,
        x_forwarded  => JSON::true,
        resources    => [ {
            names    => [ "client", "federation" ],
            compress => JSON::false
        } ]
    };
}

sub write_file_abs {
    my $self = shift;
    my ($abspath, $content) = @_;

    write_binary("$abspath", $content);

    return $abspath;
}

1;
