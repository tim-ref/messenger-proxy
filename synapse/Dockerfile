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
