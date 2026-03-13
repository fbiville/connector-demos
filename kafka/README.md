# Neo4j CDC via Kafka Demo

This project demonstrates change data capture from a source Neo4j database into Kafka and replication into a target Neo4j database through Kafka Connect.

The flow is:

1. `neo4jsrc` emits CDC events.
2. The source connector writes those events to the Kafka topic `demo`.
3. The sink connector reads `demo` and applies the changes to `neo4jsink`.

## Prerequisites

- Docker
- Docker Compose v2 (`docker compose`)
- `bash`
- `curl`

The following local ports should be available:

- `7474` and `7687` for `neo4jsrc`
- `17474` and `17687` for `neo4jsink`
- `8081` for Schema Registry
- `8083` for Kafka Connect
- `9021` for Confluent Control Center
- `9092` for Kafka

## Start The Stack

From this directory, start the containers:

```sh
docker compose up -d
```

The scripts below wait for the services they need, so you do not need to manually poll container health before running them.

## Configure The Demo

Run the setup scripts in this order:

```sh
./01-enable-cdc.sh
./02-register-source-connector.sh
./03-register-sink-connector.sh
./04-init-constraints.sh
```

What each script does:

- `01-enable-cdc.sh` enables CDC on `neo4jsrc` and verifies `txLogEnrichment` is `FULL`
- `02-register-source-connector.sh` registers or updates the Neo4j CDC source connector
- `03-register-sink-connector.sh` registers or updates the Neo4j CDC sink connector
- `04-init-constraints.sh` creates the `(:Customer {name})` and `(:Product {name})` node keys on both Neo4j databases

## Service Endpoints

- Source Neo4j Browser: `http://localhost:7474`
- Target Neo4j Browser: `http://localhost:17474`
- Control Center: `http://localhost:9021`

Neo4j credentials for both source and sink:

- username: `neo4j`
- password: `letmein!`

## Verify Replication

Connect to the source database at `http://localhost:7474` and create, update, and delete `Customer` nodes, `Product` nodes, and `PURCHASES` relationships. Then check that the same changes are replicated into the target database at `http://localhost:17474`.

Recommended source-side Cypher to try:

```cypher
CREATE (:Customer {name: 'Pierre'});
CREATE (:Product {name: 'Neo4j'});
MATCH (c:Customer {name: 'Pierre'}), (p:Product {name: 'Neo4j'})
CREATE (c)-[:PURCHASES]->(p);
```

Update them:

```cypher
MATCH (c:Customer {name: 'Pierre'})
SET c.name = 'Pierre Halftermeyer';

MATCH (p:Product {name: 'Neo4j'})
SET p.name = 'Neo4j Enterprise';
```

Delete them:

```cypher
MATCH (:Customer {name: 'Pierre Halftermeyer'})-[r:PURCHASES]->(:Product {name: 'Neo4j Enterprise'})
DELETE r;

MATCH (c:Customer {name: 'Pierre Halftermeyer'})
DELETE c;

MATCH (p:Product {name: 'Neo4j Enterprise'})
DELETE p;
```

Useful target-side checks:

```cypher
MATCH (c:Customer) RETURN c;
MATCH (p:Product) RETURN p;
MATCH (c:Customer)-[r:PURCHASES]->(p:Product) RETURN c, r, p;
```

## Optional Kafka Inspection

As an optional verification step, open Confluent Control Center at `http://localhost:9021`, inspect the `demo` topic, and confirm that CDC messages are being produced there before or while they are being consumed by the sink connector.

## Notes

- The source connector publishes `Customer`, `Product`, and `PURCHASES` CDC events to a single Kafka topic: `demo`.
- Schema Registry compatibility is configured to `NONE` in `docker-compose.yml` so that heterogeneous CDC schemas can coexist on that single topic.
- The connector registration scripts are idempotent. Re-running them updates the existing connector configuration.
