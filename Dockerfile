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

FROM eclipse-temurin:17-jre

VOLUME /app/certificates

LABEL maintainer="TIMREF Maintainers"

ARG JAR_FILE

COPY $JAR_FILE /app/mp-backend-jar-with-dependencies.jar
COPY java.security /app

ENTRYPOINT ["java", \
    "-Djava.security.properties==/app/java.security", \
    "-Djdk.tls.namedGroups=brainpoolP384r1tls13,brainpoolP256r1tls13,brainpoolP384r1,brainpoolP256r1,secp384r1,secp256r1", \
    "-Dsun.security.ssl.allowLegacyHelloMessages=false", \
    "-jar", \
    "/app/mp-backend-jar-with-dependencies.jar" \
]
