#!/bin/bash
#
# Copyright (C) 2023 akquinet GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
#

SERVER=http://localhost:8083/

echo "[+] waiting for synapse to start"
# simply wait until the server is available
/wait-for.sh "curl --fail --silent --output /dev/null $SERVER/_matrix/static/"

echo "[+] adding admin user"
register_new_matrix_user $SERVER -u "$SYNAPSE_ADMIN_USER" -p "$SYNAPSE_ADMIN_PASSWORD" -a -c /data/homeserver.yaml

if [ -n "$SYNAPSE_TEST_USER" ] && [ -n "$SYNAPSE_TEST_PASSWORD" ]; then
  echo "[+] adding test user"
  register_new_matrix_user $SERVER -u "$SYNAPSE_TEST_USER" -p "$SYNAPSE_TEST_PASSWORD" -c /data/homeserver.yaml --no-admin
else
  echo "[+] test user creation is skipped due to missing variables."
fi
echo "[+] finished configuration"
