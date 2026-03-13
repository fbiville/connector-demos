#!/bin/sh

set -eux

"$(dirname "$0")/wait-for-services.sh" broker schema-registry connect neo4jsink

curl --fail-with-body --request PUT http://localhost:8083/connectors/Neo4jCdcSinkDemo/config \
  --header "Content-Type:application/json" \
  --header "Accept:application/json" \
  --data @03-register-sink-connector.json
