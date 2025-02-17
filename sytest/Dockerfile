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

ARG TARGETARCH

ENV SYTEST_PLUGINS=/plugins
ENV SYTEST_LIB=/sytest/lib
# SYNAPSE_VERSION and SYTEST_BRANCH should match
ENV SYNAPSE_VERSION=v1.119.0
ENV SYTEST_BRANCH=release-v1.119
# IN_PIPELINE to true when run in pipeline
ENV IN_PIPELINE=false
ENV APP_HOME=/messenger-proxy

LABEL maintainer="TIMREF Maintainers"

RUN apt-get update && \
    apt-get install -y wget nginx libtap-formatter-junit-perl libtap-formatter-html-perl openssl && \
    apt-get clean

RUN if [ "$TARGETARCH" = "arm64" ]; then \
	wget https://download.oracle.com/java/21/latest/jdk-21_linux-aarch64_bin.tar.gz && \
	mkdir -p /opt/java && \
	tar -xzf jdk-21_linux-aarch64_bin.tar.gz -C /opt/java; \
else \
	wget https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz && \
	mkdir -p /opt/java && \
	tar -xzf jdk-21_linux-x64_bin.tar.gz -C /opt/java; \
fi

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

RUN env PATH="/opt/java/jdk-21.0.6/bin:$PATH" "$SYTEST_PLUGINS/messenger-proxy/scripts/messenger_proxy_sytest.sh"
