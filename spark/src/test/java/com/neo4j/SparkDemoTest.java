package com.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public class SparkDemoTest {

    private static final String DATABASE = "comments";

    private static final String NEO4J_DATASOURCE = "org.neo4j.spark.DataSource";

    private static Driver driver;

    private static SparkSession spark;

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer(DockerImageName.parse("neo4j:2026-enterprise"))
            .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
            .withAdminPassword("letmein!")
            .withCreateContainerCmdModifier(cmd -> cmd.withUser("neo4j"))
            .withFileSystemBind(
                    MountableFile.forClasspathResource("/neo4j-admin/").getFilesystemPath(),
                    "/import",
                    BindMode.READ_ONLY);

    @BeforeAll
    static void import_to_neo4j() throws Exception {
        ensureGcsAccess();
        driver = GraphDatabase.driver(
                NEO4J.getBoltUrl(),
                AuthTokens.basic("neo4j", "letmein!"),
                Config.builder().build());
        driver.verifyConnectivity();

        importData();
        verifyData(Map.of("Person", 10_620L, "Comment", 2_391_707L), Map.of("CREATED", 2_391_707L));
        spark = SparkSession.builder()
                .master("local[*]")
                .appName("SparkDemo")
                .config("neo4j.url", NEO4J.getBoltUrl())
                .config("neo4j.authentication.basic.username", "neo4j")
                .config("neo4j.authentication.basic.password", NEO4J.getAdminPassword())
                .getOrCreate();
    }

    @AfterAll
    static void clean_up() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    void exports_graph_data_to_csv_on_gcs() {
        var bucket = System.getenv("GCS_BUCKET_NAME");
        Map.of(
                        ":Comment", "gs://%s/demo/comments".formatted(bucket),
                        ":Person", "gs://%s/demo/persons".formatted(bucket))
                .forEach((label, uri) -> spark.read()
                        .format(NEO4J_DATASOURCE)
                        .option("database", DATABASE)
                        .option("labels", label)
                        .load()
                        .drop("<labels>", "<id>")
                        .write()
                        .mode("overwrite")
                        .option("header", "true")
                        .csv(uri));
        var createdRelUri = "gs://%s/demo/created".formatted(bucket);
        spark.read()
                .format(NEO4J_DATASOURCE)
                .option("database", DATABASE)
                .option("relationship", "CREATED")
                .option("relationship.source.labels", ":Person")
                .option("relationship.target.labels", ":Comment")
                .load()
                .drop("<rel.id>", "<rel.type>", "<source.id>", "<source.labels>", "<target.id>", "<target.labels>")
                .write()
                .mode("overwrite")
                .option("header", "true")
                .csv(createdRelUri);
    }

    private static void ensureGcsAccess() {
        Assumptions.assumeTrue(
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null,
                "Please run `gcloud auth application-default login` and define the environment variable GOOGLE_APPLICATION_CREDENTIALS with the resulting path");
        Assumptions.assumeTrue(System.getenv("GCP_PROJECT_ID") != null, "Please set your GCP Project ID %s".formatted("GCP_PROJECT_ID"));
        Assumptions.assumeTrue(
                System.getenv("GCS_BUCKET_NAME") != null, "Please set the target GCS bucket name %s".formatted("GCS_BUCKET_NAME"));
    }

    private static void verifyData(Map<String, Long> nodeCounts, Map<String, Long> relationshipCounts) {
        nodeCounts.forEach((label, expectedCount) -> {
            try (var session = driver.session(SessionConfig.forDatabase(DATABASE))) {
                var count = session.readTransaction(tx -> {
                    var cypher = "MATCH (n:`%s`) RETURN count(n) AS count".formatted(label);
                    return tx.run(cypher).single().get("count").asLong();
                });
                assertThat(count)
                        .overridingErrorMessage("(:`%s`) node total mismatch (got %d)".formatted(label, count))
                        .isEqualTo(expectedCount);
            }
        });
        relationshipCounts.forEach((type, expectedCount) -> {
            try (var session = driver.session(SessionConfig.forDatabase(DATABASE))) {
                var count = session.readTransaction(tx -> {
                    var cypher = "MATCH ()-[r:`%s`]->() RETURN count(r) AS count".formatted(type);
                    return tx.run(cypher).single().get("count").asLong();
                });
                assertThat(count)
                        .overridingErrorMessage("()-[:`%s`]->() relationship total mismatch (got %d)".formatted(type, count))
                        .isEqualTo(expectedCount);
            }
        });
    }

    private static void importData() throws Exception {
        var execution = NEO4J.execInContainer(
                "neo4j-admin",
                "database",
                "import",
                "full",
                DATABASE,
                "--verbose",
                "--input-type=parquet",
                "--schema=/import/schema.cypher",
                "--nodes=Person=/import/Person/header_for_Person.csv,/import/Person/.*.parquet",
                "--nodes=Comment=/import/Comment/header_for_Comment.csv,/import/Comment/.*.parquet",
                "--relationships=CREATED=/import/Comment/header_for_CREATED.csv,/import/Comment/.*.parquet");
        assertThat(execution.getExitCode())
                .overridingErrorMessage(execution.getStderr())
                .isZero();
        try (var session = driver.session(SessionConfig.forDatabase("system"))) {
            session.run("CREATE DATABASE $name WAIT", Map.of("name", DATABASE)).consume();
        }
    }
}
