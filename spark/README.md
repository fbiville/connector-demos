# Spark + neo4j-admin demo

This example loads Parquet files into a Neo4j database with `neo4j-admin`, then uses the Neo4j Spark connector to export nodes and relationships as CSV files to Google Cloud Storage.

## Prerequisites

- Maven
- Java 21
- Docker
- A Google Cloud project and bucket
- Application Default Credentials (`gcloud auth application-default login`)

Required environment variables:

- `GOOGLE_APPLICATION_CREDENTIALS`
- `GCP_PROJECT_ID`
- `GCS_BUCKET_NAME`

## Run

```bash
mvn verify
```

The test imports the sample data from `src/test/resources/neo4j-admin`, creates a `comments` database, and writes CSV output under `gs://$GCS_BUCKET_NAME/demo/`.
