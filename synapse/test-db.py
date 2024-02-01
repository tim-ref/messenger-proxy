# Copyright (C) 2023 akquinet GmbH

# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

#!/usr/bin/env python

import psycopg2
import os

try:
    db = psycopg2.connect(
        "dbname='{}' user='{}' host='{}' password='{}'".format(os.environ["POSTGRES_DB"], os.environ["POSTGRES_USER"],
                                                               os.environ["POSTGRES_HOST"],
                                                               os.environ["POSTGRES_PASSWORD"]))
except:
    exit(1)

exit(0)
