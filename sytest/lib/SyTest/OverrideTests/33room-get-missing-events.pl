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

# adapted from: https://github.com/matrix-org/sytest/blob/release-v1.119/tests/50federation/33room-get-missing-events.pl
test "Outbound federation can request missing events",
   requires => [ $main::OUTBOUND_CLIENT, $main::INBOUND_SERVER,
                 local_user_and_room_fixtures(
                    room_opts => { room_version => "10" },
                   ),
                 federation_user_id_fixture() ],

   do => sub {
      my ( $outbound_client, $inbound_server, $creator, $room_id, $user_id ) = @_;
      my $first_home_server = $creator->server_name;

      my $datastore         = $inbound_server->datastore;

      my $missing_event_id;

      $outbound_client->join_room(
         server_name => $first_home_server,
         room_id     => $room_id,
         user_id     => $user_id,
      )->then( sub {
         my ( $room ) = @_;

         # TODO: We happen to know the latest event in the server should be my
         #   m.room.member state event, but that's a bit fragile
         my $latest_event = $room->get_current_state_event( "m.room.member", $user_id );

         # Generate but don't send an event
         my $missing_event = $room->create_and_insert_event(
            type => "m.room.message",

            sender  => $user_id,
            content => {
               body => "Message 1",
            },
         );
         $missing_event_id = $room->id_for_event( $missing_event );

         # Generate another one and do send it so it will refer to the
         # previous in its prev_events field
         my $sent_event = $room->create_and_insert_event(
            type => "m.room.message",

            # This would be done by $room->create_and_insert_event anyway but lets be
            #   sure for this test
            prev_events => $room->make_event_refs( $missing_event ),

            sender  => $user_id,
            content => {
               body => "Message 2",
            },
         );

         Future->needs_all(
            $inbound_server->await_request_get_missing_events( $room_id )
            ->then( sub {
               my ( $req ) = @_;
               my $body = $req->body_from_json;

               assert_json_keys( $body, qw( earliest_events latest_events limit ));
               # TODO: min_depth but I have no idea what it does

               assert_json_list( my $earliest = $body->{earliest_events} );
               @$earliest == 1 or
                  die "Expected a single 'earliest_event' ID";
               assert_eq( $earliest->[0], $room->id_for_event( $latest_event ),
                  'earliest_events[0]' );

               assert_json_list( my $latest = $body->{latest_events} );
               @$latest == 1 or
                  die "Expected a single 'latest_events' ID";
               assert_eq( $latest->[0], $room->id_for_event( $sent_event ),
                  'latest_events[0]' );

               my @events = $datastore->get_backfill_events(
                  start_at    => $latest,
                  stop_before => $earliest,
                  limit       => $body->{limit},
               );

               $req->respond_json( {
                  events => \@events,
               } );

               Future->done(1);
            }),

            $outbound_client->send_event(
               event       => $sent_event,
               destination => $first_home_server,
            ),
         );
      })->then( sub {
         # creator user should eventually receive the missing event
         await_sync_timeline_contains(
            $creator, $room_id,
            check => sub {
               my ( $event ) = @_;
               $event->{type} eq "m.room.message" &&
               $event->{event_id} eq $missing_event_id;
            },
         );
      });
   };

sub sytest_user_and_room_fixture {
   # returns a fixture which creates an invite-only room, and a sytest user,
   # and joins the sytest user to the room.
   #
   # the fixture returns ( $room, $user_id )
   my ( $creator_user_fixture ) = @_;
   return fixture(
      requires => [
         $creator_user_fixture,
         room_fixture(
            $creator_user_fixture,
            preset => 'private_chat',
         ),
         federation_user_id_fixture(),
         $main::INBOUND_SERVER,
         $main::OUTBOUND_CLIENT,
      ],
      setup => sub {
         my (
            $creator_user, $room_id, $sytest_user_id,
            $inbound_server, $outbound_client,
         ) = @_;

         Future->needs_all(
            matrix_invite_user_to_room(
               $creator_user, $sytest_user_id, $room_id,
            ),
            $inbound_server->await_request_v2_invite( $room_id )->then( sub {
               my ( $req, undef ) = @_;

               my $body = $req->body_from_json;

               # sign the invite event and send it back
               my $invite = $body->{event};
               $inbound_server->datastore->sign_event( $invite );
               $req->respond_json( { event => $invite } );
               Future->done;
            }),
         )->then( sub {
            $outbound_client->join_room(
               server_name => $creator_user->http->server_name,
               room_id     => $room_id,
               user_id     => $sytest_user_id,
            );
         })->then( sub {
            my ( $room ) = @_;
            log_if_fail "Joined room " . $room->room_id . " with user $sytest_user_id";
            Future->done( $room, $sytest_user_id );
         });
      },
   );
}


my $user_f = local_user_fixture();
test "outliers whose auth_events are in a different room are correctly rejected",
   requires => [
      $user_f,
      sytest_user_and_room_fixture( $user_f ),
      sytest_user_and_room_fixture( $user_f ),
      $main::INBOUND_SERVER,
      $main::OUTBOUND_CLIENT,
   ],

   do => sub {
      my (
         $creator_user,
         $room1, $sytest_user_1,
         $room2, $sytest_user_2,
         $inbound_server, $outbound_client,
      ) = @_;
      my $synapse_server_name = $creator_user->http->server_name;

      # this tests an edge-case with auth events
      #
      # we have two (invite-only) rooms (1 and 2), with a different user in
      # each room (1 and 2).
      #
      # In room 2, we create three events, Q, R, S.
      #
      # We send S over federation, and allow the server to backfill R, leaving
      # the server with a gap in the dag. It therefore requests the state at Q,
      # which leads to Q being persisted as an outlier.
      #
      # Q is a membership event for user 1, but its auth_events point to the
      # membership in room 1. It should be rejected.
      #
      # R is a regular event, but sent by user 1 (so again should be rejected).
      #
      # S is a legit event.

      my %initial_room2_state  = %{ $room2->{current_state} };

      my ( $event_Q, $event_id_Q ) = $room2->create_and_insert_event(
         type => 'm.room.member',
         sender => $sytest_user_1,
         state_key => $sytest_user_1,
         content => { membership => 'join', },
         auth_events => $room2->make_event_refs(
            $room2->get_current_state_event( "m.room.create" ),
            $room2->get_current_state_event( "m.room.power_levels" ),
            $room1->get_current_state_event( "m.room.member", $sytest_user_1 ),
         ),
      );

      my ( $event_R, $event_id_R ) = $room2->create_and_insert_event(
         type        => "m.room.message",
         sender      => $sytest_user_1,
         content     => { body => "event R" },
      );

      my ( $event_S, $event_id_S ) = $room2->create_and_insert_event(
         type        => "m.room.message",
         sender      => $sytest_user_2,
         content     => { body => "event S" },
      );

      log_if_fail "events Q, R, S", [ $event_id_Q, $event_id_R, $event_id_S ];

      my $state_req_fut;

      Future->needs_all(
         # send S
         $outbound_client->send_event(
            event => $event_S,
            destination => $synapse_server_name,
         ),

         # we expect to get a missing_events request
         $inbound_server->await_request_get_missing_events( $room2->{room_id} )
         ->then( sub {
            my ( $req ) = @_;
            my $body = $req->body_from_json;
            log_if_fail "/get_missing_events request", $body;

            assert_deeply_eq(
               $body->{latest_events},
               [ $event_id_S ],
               "latest_events in /get_missing_events request",
            );

            # just return R
            my $resp = { events => [ $event_R ] };

            log_if_fail "/get_missing_events response", $resp;
            $req->respond_json( $resp );
            Future->done(1);
         }),

         # there will still be a gap, so then we expect a state_ids request
         $inbound_server->await_request_state_ids(
            $room2->{room_id}, $event_id_Q,
         )->then( sub {
            my ( $req, @params ) = @_;
            log_if_fail "/state_ids request", \@params;

            my $resp = {
               pdu_ids => [
                  map { $room2->id_for_event( $_ ) } values( %initial_room2_state ),
               ],
               auth_chain_ids => $room2->event_ids_from_refs( $event_Q->{auth_events} ),
            };

            log_if_fail "/state_ids response", $resp;

            # once we respond to `/state_ids`, the server may send a /state request;
            # be prepared to answer that.  (it may, alternatively, send individual
            # /event requests)
            $state_req_fut = await_and_handle_request_state(
               $inbound_server, $room2, $event_id_Q, [ values( %initial_room2_state ) ]
            );

            $req->respond_json( $resp );
            Future->done(1);
         }),
      )->then( sub {
         # wait for either S to turn up in /sync, or $state_req_fut to fail.
         Future->wait_any(
            $state_req_fut->then( sub { Future->new() } ),

            await_sync_timeline_contains(
               $creator_user, $room2->room_id, check => sub {
                  my ( $event ) = @_;
                  log_if_fail "Got event in room2", $event;

                  my $event_id = $event->{event_id};

                  # if either Q or R show up, that's a problem
                  if( $event->{sender} eq $sytest_user_1 ) {
                     die "Got an event $event_id from a user who shouldn't be a member";
                  }

                  return $event_id eq $event_id_S;
               },
            ),
         );
      })->then( sub {
         # finally, check that the state in room 2 looks correct.
         matrix_get_room_state_by_type(
            $creator_user, $room2->room_id,
         );
      })->then( sub {
         my ( $state ) = @_;

         log_if_fail "state in room 2", $state;

         # there should not be a membership event for user 1.
         if( exists $state->{'m.room.member'}{$sytest_user_1} ) {
            die "user became a member of the room without an invite";
         }
         Future->done;
      });
   };

# A homeserver receiving a response from `get_missing_events` for a version 6
# room with a bad JSON value (e.g. a float) should discard the bad data.
#
# To test this we need to:
# * Add an event with "bad" data into the room history, but don't send it.
# * Add a "good" event into the room history and send it.
# * The homeserver attempts to get the missing event (with the bad data).
# * The homeserver should reject the "good" event.
# * To check this we send another valid event pointing at the "good" event,
#   and wait for a call to `/get_missing_events` for that event (thus proving
#   that the homeserver rejected the good event).
test "Outbound federation will ignore a missing event with bad JSON for room version 6",
   requires => [ $main::OUTBOUND_CLIENT, $main::INBOUND_SERVER,
                 federated_rooms_fixture( room_opts => { room_version => "10" } ) ],

   do => sub {
      my ( $outbound_client, $inbound_server, $creator, $user_id, @rooms ) = @_;

      my $room = $rooms[0];
      my $room_id = $room->{room_id};
      my $first_home_server = $creator->server_name;

      my $datastore         = $inbound_server->datastore;

      # TODO: We happen to know the latest event in the server should be my
      #   m.room.member state event, but that's a bit fragile
      my $latest_event = $room->get_current_state_event( "m.room.member", $user_id );

      log_if_fail "Latest event", $latest_event;

      # Generate but don't send an event
      my $missing_event = $room->create_and_insert_event(
         type => "m.room.message",

         sender  => $user_id,
         content => {
            body    => "Message 1",
            # Insert a bad value here so that this event cannot be fetched.
            bad_val => 1.1,
         },
      );
      my $missing_event_id = $room->id_for_event( $missing_event );

      log_if_fail "Missing event", $missing_event;

      # Generate another event which will be sent. It will refer to the missing
      # event in its prev_events field.
      my $sent_event = $room->create_and_insert_event(
         type => "m.room.message",

         # This would be done by $room->create_and_insert_event anyway but lets be
         #   sure for this test
         prev_events => $room->make_event_refs( $missing_event ),

         sender  => $user_id,
         content => {
            body => "Message 2",
         },
      );
      my $sent_event_id = $room->id_for_event( $sent_event );

      log_if_fail "Sent event", $sent_event;

      # We now create create another event that references the "good" event
      # above. If the good event was correctly rejected then we'll see an
      # attempt to fetch it via `/get_missing_events`
      my $marker_event = $room->create_and_insert_event(
         type => "m.room.message",

         prev_events => $room->make_event_refs( $sent_event ),

         sender  => $user_id,
         content => {
            body    => "Message 3",
         },
      );
      my $marker_event_id = $room->id_for_event( $marker_event );

      Future->needs_all(
         $inbound_server->await_request_get_missing_events( $room_id )
         ->then( sub {
            my ( $req ) = @_;

            my @events = $datastore->get_backfill_events(
               start_at    => [ $sent_event_id ],
               stop_before => [ $room->id_for_event( $latest_event ) ],
               limit       => 10,
            );

            log_if_fail "Backfilling", \@events;

            respond_to_get_missing_events( $req, $room, $latest_event, $sent_event, \@events )
         }),

         # Can't use send_event here because that checks none were rejected.
         $outbound_client->send_transaction(
            destination => $first_home_server,
            pdus => [ $sent_event ],
         ),
      )->then( sub {
         log_if_fail "Sending marker event and waiting for /get_missing_events";

         Future->needs_all(
            $inbound_server->await_request_get_missing_events( $room_id )
            ->then( sub {
               my ( $req ) = @_;

               respond_to_get_missing_events( $req, $room, $latest_event, $marker_event, [ $sent_event ] )
            }),
            $outbound_client->send_transaction(
               destination => $first_home_server,
               pdus => [ $marker_event ],
            )
         )
      });
   };


=head2 respond_to_get_missing_events

   respond_to_get_missing_events( $body, $room, $earliest_event, $latest_event, $events_to_return )

Respond to a `/get_missing_events` request for the given room.

Asserts that the request has an `earliest_events` field matching the given
`$earliest_event`, and similarly that the request has a `latest_events` field
that matches the given `$latest_event`.

Responds to the request with the given set of `$events_to_return`.

=cut

sub respond_to_get_missing_events
{
   my ( $req, $room, $earliest_event, $latest_event, $events_to_return ) = @_;
   my $body = $req->body_from_json;

   log_if_fail "/get_missing_events body", $body;

   assert_json_list( my $earliest = $body->{earliest_events} );
   @$earliest == 1 or
      die "Expected a single 'earliest_event' ID";

   # It is expected that the earliest event is the m.room.member event,
   # but it is possible that the caches have not yet been invalidated
   # so also allow any of that event's previous events.
   my @expected = @{$earliest_event->{prev_events}};
   push( @expected, $room->id_for_event( $earliest_event ) );
   assert_ok( any { $earliest->[0] eq $_ } @expected,
      "'earliest_events' did not match" );

   assert_json_list( my $latest = $body->{latest_events} );
   @$latest == 1 or
      die "Expected a single 'latest_events' ID";
   assert_eq( $latest->[0], $room->id_for_event( $latest_event ),
      'latest_events[0]' );

   $req->respond_json( {
      events => $events_to_return,
   } );

   Future->done;
}

push our @EXPORT, qw( respond_to_get_missing_events );
