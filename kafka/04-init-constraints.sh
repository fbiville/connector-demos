#!/bin/bash

set -eux

"$(dirname "$0")/wait-for-services.sh" neo4jsrc neo4jsink

docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsrc \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive "CREATE CONSTRAINT customer_name_key IF NOT EXISTS FOR (c:Customer) REQUIRE (c.name) IS NODE KEY"

docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsrc \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive "CREATE CONSTRAINT product_name_key IF NOT EXISTS FOR (p:Product) REQUIRE (p.name) IS NODE KEY"



docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsink \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive "CREATE CONSTRAINT customer_name_key IF NOT EXISTS FOR (c:Customer) REQUIRE (c.name) IS NODE KEY"

docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsink \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive "CREATE CONSTRAINT product_name_key IF NOT EXISTS FOR (p:Product) REQUIRE (p.name) IS NODE KEY"
