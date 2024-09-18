#!/bin/bash -xe

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

# This script is run in the docker build process.
# adapted from: https://github.com/matrix-org/sytest/blob/develop/scripts/synapse_sytest.sh
#
# the messenger proxy source in /messenger-proxy,
# and a virtualenv in /venv.
# It installs synapse into the virtualenv, configures sytest according to the
# env vars, and runs sytest.
#

# Run the sytests.

set -e

cd "$(dirname $0)/.."

mkdir -p /work

if [ "$IN_PIPELINE" == true ]; then
  export MESSENGER_PROXY_DIR="$CI_PROJECT_DIR"
else
  export MESSENGER_PROXY_DIR=/messenger-proxy
fi

# generating a new ca key to use ecdsa instead of rsa
pushd /sytest/keys
rm ca.key ca.crt tls-selfsigned.crt tls-selfsigned.key
# CA
openssl ecparam -name prime256v1 -genkey -noout -out ca.key
openssl req -new -x509 -days 3650 -config $MESSENGER_PROXY_DIR/certificates/ca.conf -key ca.key -out ca.crt
cat ca.key > ca.pem
cat ca.crt >> ca.pem
# tls-selfsigned
openssl genrsa -out tls-selfsigned.key 2048
openssl req -new -key tls-selfsigned.key -out tls-selfsigned.csr -subj /CN=localhost
cat << EOF > tls-selfsigned.ext
subjectAltName=DNS:localhost
EOF
openssl x509 -req -in tls-selfsigned.csr -CA ca.crt -CAkey ca.key -set_serial 1 -out tls-selfsigned.crt -extfile tls-selfsigned.ext
# adding the sytest ca to the ubuntu root ca so java trusts the certificates
cp ca.crt /usr/local/share/ca-certificates
update-ca-certificates
popd

# increase general timeouts, otherwise the test results are  flaky
export TIMEOUT_FACTOR=4

echo "starting nginx..."
/usr/sbin/nginx
echo "nginx started"

# PostgreSQL configuration from https://github.com/matrix-org/sytest/blob/develop/scripts/synapse_sytest.sh
sed -i -r "s/^max_connections.*$/max_connections = 500/" "$PGDATA/postgresql.conf"

echo "fsync = off" >> "$PGDATA/postgresql.conf"
echo "full_page_writes = off" >> "$PGDATA/postgresql.conf"

# Start the database (comment from Sytest)
echo "starting postgres..."
su -c 'eatmydata /usr/lib/postgresql/*/bin/pg_ctl -w start -s' postgres
echo "postgres started"

export PGUSER=postgres
export POSTGRES_DB_1=pg0
export POSTGRES_DB_2=pg1

# Make the test databases for the two Messenger-Proxy servers that will be spun up
su -c "psql -c 'CREATE DATABASE $POSTGRES_DB_1;'" postgres
su -c "psql -c 'CREATE DATABASE $POSTGRES_DB_2;'" postgres

# default value for SYNAPSE_DEFAULT_BRANCH
: "${SYNAPSE_DEFAULT_BRANCH:=develop}"

# Try and fetch the branch
wget -q https://github.com/element-hq/synapse/archive/refs/tags/"$SYNAPSE_VERSION".tar.gz -O synapse.tar.gz || {
    # Probably a 404, fall back to develop
    echo "Using ${SYNAPSE_DEFAULT_BRANCH} instead..."
    wget -q https://github.com/element-hq/synapse/archive/${SYNAPSE_DEFAULT_BRANCH}.tar.gz -O synapse.tar.gz
}

mkdir -p /synapse
tar -C /synapse --strip-components=1 -xf synapse.tar.gz

if [ -n "$OFFLINE" ]; then
    # if we're in offline mode, just put synapse into the virtualenv, and
    # hope that the deps are up-to-date.
    #
    # pip will want to install any requirements for the build system
    # (https://github.com/pypa/pip/issues/5402), so we have to provide a
    # directory of pre-downloaded build requirements.
    #
    # We need both the `--no-deps` and `--no-index` flags for offline mode:
    # `--no-index` only prevents PyPI usage and does not stop pip from
    # installing dependencies from git.
    # `--no-deps` skips installing dependencies but does not stop pip from
    # pulling Synapse's build dependencies from PyPI. (comment from Sytest)
    echo "Installing Synapse using pip in offline mode..."
    /venv/bin/pip install --no-deps --no-index --find-links /pypi-offline-cache /synapse

    if ! /venv/bin/pip check ; then
        echo "There are unmet dependencies which can't be installed in offline mode" >&2
        exit 1
    fi
else
    if [ -f "/synapse/poetry.lock" ]; then
        echo "Installing Synapse using poetry..."
        # Only use the cached install in local images, in pipeline we use Gitlab caching (comment from Sytest)
        if [ "$IN_PIPELINE" != true ]; then
            # Install Synapse and dependencies using poetry, respecting the lockfile.
            # The virtual env will already be populated with dependencies from the
            # Docker build. (comment from Sytest)
            if [ -d /synapse/.venv ]; then
                # There was a virtual env in the source directory for some reason.
                # We want to use our own, so remove it. (comment from Sytest)
                rm -rf /synapse/.venv
            fi
            ln -s -T /venv /synapse/.venv # reuse the existing virtual env (comment from Sytest)
        fi
        pushd /synapse
        poetry install -vvv --extras all
        popd
    else
        # Install Synapse and dependencies using pip. As of pip 20.1, this will
        # try to build Synapse in-tree, which means writing changes to the source
        # directory.
        # The virtual env will already be populated with dependencies from the
        # Docker build.
        # Keeping this option around allows us to `pip install` from wheel in synapse's
        # "latest dependencies" job. (comment from Sytest)
        echo "Installing Synapse using pip..."
        /venv/bin/pip install -q --upgrade --upgrade-strategy eager --no-cache-dir /synapse[all]
    fi

    /venv/bin/pip install -q --upgrade --no-cache-dir \
        coverage codecov tap.py coverage_enable_subprocess

    # Make sure all Perl deps are installed -- this is done in the docker build
    # so will only install packages added since the last Docker build (comment from Sytest)
    /sytest/install-deps.pl
fi

# Run the tests
echo >&2 "+++ Running tests"

if [ "$IN_PIPELINE" == true ]; then
  pushd /synapse
  PYTHON_PATH=$(poetry env info -p)/bin/python
  popd
  OUTPUT=tap
else
  PYTHON_PATH=/venv/bin/python
  OUTPUT=term
fi
pushd /sytest

RUN_TESTS=(
    perl -I "$SYTEST_LIB" /sytest/run-tests.pl --server-implementation=MessengerProxy --python="$PYTHON_PATH" --messenger-proxy-directory="$MESSENGER_PROXY_DIR"
    --synapse-directory=/synapse -B "/plugins/messenger-proxy/akquinet_blacklist.txt" -O "$OUTPUT" --all --work-directory="/work"
)

TEST_STATUS=0
if [ "$IN_PIPELINE" == true ]; then
  mkdir -p /logs
  "${RUN_TESTS[@]}" "$@" >/logs/results.tap &
  pid=$!
else
  "${RUN_TESTS[@]}" "$@" &
  pid=$!
fi
popd

# make sure that we kill the test runner on SIGTERM, SIGINT, etc (comment from Sytest)
trap 'kill $pid && /usr/sbin/nginx -s quit' TERM INT
wait $pid || TEST_STATUS=$?
trap - TERM INT

# stop nginx (comment from Sytest)
echo "stopping nginx..."
/usr/sbin/nginx -s quit
echo "nginx stopped"

# stopping the database (comment from Sytest)
echo "stopping postgres..."
su -c 'eatmydata /usr/lib/postgresql/*/bin/pg_ctl -w stop -m immediate' postgres
echo "postgres stopped"

if [ $TEST_STATUS -ne 0 ]; then
    echo >&2 -e "run-tests \e[31mFAILED\e[0m: exit code $TEST_STATUS"
else
    echo >&2 -e "run-tests \e[32mPASSED\e[0m"
fi

exit $TEST_STATUS
