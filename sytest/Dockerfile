#
# Copyright © 2023 - 2025 akquinet GmbH (https://www.akquinet.de)
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

# this image is regularly updated without setting a new container tag
# thus the used image you are using locally can be outdated
FROM matrixdotorg/sytest-synapse:bullseye

ENV SYTEST_PLUGINS=/plugins
ENV SYTEST_LIB=/sytest/lib
# SYNAPSE_VERSION and SYTEST_BRANCH should match
ENV SYNAPSE_VERSION=v1.119.0
ENV SYTEST_BRANCH=release-v1.119
# IN_PIPELINE to true when run in pipeline
ENV IN_PIPELINE=false
ENV APP_HOME=/messenger-proxy

LABEL maintainer="TIMREF Maintainers"

# The "bullseye" sytest image is based on a version of Debian that does not know openjdk-21-jdk yet.
# So we use adoptium to install termurin-21-jdk instead.
# Note that the tests previously failed with a manual Java21 installation but they now work under temurin.
RUN wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /usr/share/keyrings/adoptium.gpg > /dev/null \
    && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bullseye main" | tee /etc/apt/sources.list.d/adoptium.list

RUN apt-get update && \
    apt-get install -y gnupg2 temurin-21-jdk nginx libtap-formatter-junit-perl libtap-formatter-html-perl openssl && \
    apt-get clean

WORKDIR $APP_HOME

ARG JAR_FILE

COPY java.security $APP_HOME
COPY /certificates/ca.conf $APP_HOME/certificates/

WORKDIR /

RUN mkdir -p "$SYTEST_PLUGINS/messenger-proxy"
COPY sytest $SYTEST_PLUGINS/messenger-proxy

COPY $JAR_FILE "$APP_HOME/mp-backend-jar-with-dependencies.jar"

# allow failing
RUN /bin/bash /bootstrap.sh messenger_proxy || :

RUN "$SYTEST_PLUGINS/messenger-proxy/scripts/messenger_proxy_sytest.sh"
