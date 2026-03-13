#!/bin/bash

set -eu

"$(dirname "$0")/wait-for-services.sh" neo4jsrc

cdc_query="ALTER DATABASE neo4j SET OPTION txLogEnrichment 'FULL'"
check_query="SHOW DATABASE neo4j YIELD name, options RETURN name, options.txLogEnrichment AS txLogEnrichment"

printf 'Running CDC query:\n%s\n\n' "$cdc_query"

docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsrc \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive --format plain \
  "$cdc_query" > /dev/null

printf 'Running verification query:\n%s\n\n' "$check_query"

docker exec --env JAVA_OPTS=--enable-native-access=ALL-UNNAMED neo4jsrc \
  cypher-shell --username neo4j --password 'letmein!' \
  --non-interactive --format verbose \
  "$check_query"
