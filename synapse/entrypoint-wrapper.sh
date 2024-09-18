#!/bin/bash

#
# Copyright (C) 2023 akquinet GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
#

# turn on bash's job control
set -m

echo "[+] waiting for db to start"
# Check if PostgreSQL is ready
/wait-for.sh "/test-db.py"

# Start the primary process and put it in the background
/start.py "$@" &

# Start the helper process
/add-initial-users.sh "$@" &

fg %1
