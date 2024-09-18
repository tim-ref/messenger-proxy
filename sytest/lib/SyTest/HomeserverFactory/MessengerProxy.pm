#
# Modified by akquinet GmbH on 18.04.2023
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

# adapted from: https://github.com/matrix-org/sytest/blob/develop/lib/SyTest/HomeserverFactory/Synapse.pm

use strict;
use warnings;

require SyTest::Homeserver::MessengerProxy;

package SyTest::HomeserverFactory::MessengerProxy;
use base qw( SyTest::HomeserverFactory );

sub _init
{
    my $self = shift;
    $self->{impl} = "SyTest::Homeserver::MessengerProxy";

    $self->{args} = {
        synapse_dir         => "../synapse",
        messenger_proxy_dir => "../messenger-proxy",
        python              => "python",
    };

    $self->{extra_args} = [];

    $self->SUPER::_init( @_ );
}

sub implementation_name
{
    # run as if we're running in synapse mode
    return "synapse";
}

sub get_options
{
    my $self = shift;

    return (
        'd|synapse-directory=s' => \$self->{args}{synapse_dir},
        'messenger-proxy-directory=s' => \$self->{args}{messenger_proxy_dir},
        'python=s' => \$self->{args}{python},

        'E=s' => sub { # process -Eoption=value
            my @more = split m/=/, $_[1];

            # Turn single-letter into -X but longer into --NAME
            $_ = ( length > 1 ? "--$_" : "-$_" ) for $more[0];

            push @{ $self->{extra_args} }, @more;
        },

        $self->SUPER::get_options(),
    );
}

sub print_usage
{
    print STDERR <<EOF
   -d, --synapse-directory DIR          - path to the checkout directory of synapse

       --messenger-proxy-directory DIR  - path to the checkout directory of the messenger-proxy

       --python PATH                    - path to the 'python' binary

   -ENAME, -ENAME=VALUE                 - pass extra argument NAME or NAME=VALUE to the
                                          homeserver.
EOF
}

sub create_server
{
    my $self = shift;
    my @extra_args = @{ $self->{extra_args} };

    my %params = (
        @_,
        %{ $self->{args}},
        extra_args => \@extra_args,
    );
    return $self->{impl}->new( %params );
}

1;
