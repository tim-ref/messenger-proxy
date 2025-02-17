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

# adapted from: https://github.com/matrix-org/sytest/blob/release-v1.119/tests/50federation/51transactions.pl
test "Server correctly handles transactions that break edu limits",
   requires => [ $main::OUTBOUND_CLIENT,
                 local_user_and_room_fixtures(),
                 federation_user_id_fixture() ],

   do => sub {
      my ( $outbound_client, $creator, $room_id, $user_id ) = @_;

      $outbound_client->join_room(
         server_name => $creator->server_name,
         room_id     => $room_id,
         user_id     => $user_id,
      )->then( sub {
         my ( $room ) = @_;

         my $new_event = $room->create_and_insert_event(
             type => "m.room.message",

             sender  => $user_id,
             content => {
                 body => "Message 1",
             },
         );

         # Generate two transactions, one that breaks the 50 PDU limit and one
         # that does not
         my @bad_pdus = ( $new_event ) x 51;
         my @good_pdus = ( $new_event ) x 10;

         Future->needs_all(
            # Send the transaction to the client and expect a fail
            $outbound_client->send_transaction(
                pdus => \@bad_pdus,
                destination => $creator->server_name,
            )->main::expect_http_400(),

            # Send the transaction to the client and expect a succeed
            $outbound_client->send_transaction(
                pdus => \@good_pdus,
                destination => $creator->server_name,
            )->then( sub {
                my ( $response ) = @_;

                Future->done( 1 );
            }),
         );
      });
   };

# Room version 6 states that homeservers should strictly enforce canonical JSON
# on PDUs. Test that a transaction to `send` with a PDU that has bad data will
# be handled properly.
#
# This enforces that the entire transaction is rejected if a single bad PDU is
# sent. It is unclear if this is the correct behavior or not.
#
# See https://github.com/matrix-org/synapse/issues/7543
test "Server rejects invalid JSON in a version 6 room",
   requires => [ $main::OUTBOUND_CLIENT,
                 federated_rooms_fixture( room_opts => { room_version => "10" } ) ],

   do => sub {
      my ( $outbound_client, $creator, $user_id, @rooms ) = @_;

      my $room = $rooms[0];

      my $bad_event = $room->create_and_insert_event(
          type => "m.room.message",

          sender  => $user_id,
          content => {
             body    => "Message 1",
             # Insert a "bad" value into the PDU, in this case a float.
             bad_val => 1.1,
          },
      );

      my @pdus = ( $bad_event );

      # Send the transaction to the client and expect a fail
      $outbound_client->send_transaction(
          pdus => \@pdus,
          destination => $creator->server_name,
      )->main::expect_m_bad_json;
   };
