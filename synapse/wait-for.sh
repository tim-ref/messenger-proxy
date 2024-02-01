#!/bin/bash
#
# Copyright (C) 2023 akquinet GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
#

#
# Retry Script
#
# ./wait-for.sh "<command>"
#
set -o nounset

RETRY_MAX_ATTEMPTS=${RETRY_MAX_ATTEMPTS:-30}
RETRY_DELAY_SECONDS=${RETRY_DELAY_SECONDS:-5}

function fail {
  echo "$1" >&2
  exit 1
}

function retry {
  echo "[+] $(timestamp) - trying to execute command: $*"
  local attempts=0
  local max=$RETRY_MAX_ATTEMPTS
  local delay=$RETRY_DELAY_SECONDS

  while [ true ]; do
    "$@" && break || {
      if [[ $attempts -lt $max ]]; then
        ((attempts++))
        echo "[+] $(timestamp) - Attempt failed $attempts/$max. retry in $delay seconds"
        sleep $delay
      else
        fail "[+] $(timestamp) - Command failed after $attempts attempts"
      fi
    }
  done
}

function timestamp() {
  date +"%Y-%m-%d %T"
}

retry $1

echo ""
echo "Finished"
