#!/bin/sh

set -eux

"$(dirname "$0")/wait-for-services.sh" broker schema-registry connect neo4jsrc

curl --fail-with-body --request PUT http://localhost:8083/connectors/Neo4jCdcSourceDemo/config \
  --header "Content-Type:application/json" \
  --header "Accept:application/json" \
  --data @02-register-source-connector.json
