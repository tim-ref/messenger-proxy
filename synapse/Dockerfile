#
# Copyright © 2023 - 2024 akquinet GmbH (https://www.akquinet.de)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM matrixdotorg/synapse:v1.99.0 AS base

# Build and install modules
FROM base

# Copy Configuration files
COPY config/local-docker/* /data/

# Copy entrypoint scripts
COPY ./entrypoint-wrapper.sh /entrypoint-wrapper.sh
COPY ./wait-for.sh /wait-for.sh
COPY ./add-admin.sh /add-admin.sh
COPY ./test-db.py /test-db.py

ENTRYPOINT "/entrypoint-wrapper.sh"
