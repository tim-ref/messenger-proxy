/*
 * Copyright (C) 2023 akquinet GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

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
