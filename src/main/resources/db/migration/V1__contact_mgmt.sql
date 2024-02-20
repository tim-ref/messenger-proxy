CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS contacts (
    id uuid DEFAULT uuid_generate_v4 (),
    owner_id VARCHAR(255) NOT NULL,
    approved_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    invite_start BIGINT NOT NULL,
    invite_end BIGINT,
    PRIMARY KEY (id)
    );
