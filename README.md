# maichess-insights-spark

The **Scala Apache Spark batch module** for the maichess insights program — the
*only* component that touches Spark / Parquet / MinIO, and the analytic write path
into the `insights_*` Mongo collections. A sibling to the `maichess-insights-service`
.NET control plane (which submits/monitors the Spark jobs and serves the read API).

See the design docs in `maichess-knowledge-base`:
[architecture/insights-and-spark](../../maichess-knowledge-base/knowledge/architecture/insights-and-spark.md),
[operations/spark-and-minio](../../maichess-knowledge-base/knowledge/operations/spark-and-minio.md),
[domain/insights-statistics](../../maichess-knowledge-base/knowledge/domain/insights-statistics.md).

## Status

🟡 **Scaffolding (task 02).** This repo currently holds the **container image
wiring** only — the `Dockerfile` (base + connector jars + assembly COPY) and the
`docker-publish.yml` workflow. The actual Spark jobs (the sbt project, `build.sbt`,
and `src/`) land in:

- **task 03** — ingestion + PGN parser (`.zst` download, decompress-once, parse
  `%eval`/`%clk` → partitioned Parquet).
- **task 04** — analysis jobs (opening / endgame / position / tricky → Mongo
  `insights_*`).

Until task 03 adds the sbt module, `sbt assembly` (and therefore a full image
build) has nothing to compile — the `docker-publish.yml` workflow is expected to be
red until then.

## Image

`ghcr.io/maichess/maichess-insights-spark` — run by the Kubeflow Spark Operator
(wired in `maichess-deploy`, task 02) as `SparkApplication` driver + executor pods,
hard-pinned to `maichess-mega` and resource-capped.

### Scala 2.13 base — deliberate deviation from the task spec

Task 02 specifies `FROM apache/spark:3.5.x (Scala 2.13)`, but **the official
`apache/spark` Docker images are published for Scala 2.12 only** — there is no
`scala2.13` tag. A Scala 2.13 application jar is binary-incompatible with a 2.12
Spark runtime, and the insights ADR explicitly chose Scala 2.13 (Spark connectors
lag Scala 3). So the runtime stage is built from the **Apache Spark Scala 2.13
distribution tarball** (`spark-3.5.3-bin-hadoop3-scala2.13.tgz`) on a Temurin JRE
base instead, reusing the distribution's official Kubernetes entrypoint. This is
the only way to honour the Scala 2.13 decision; it is recorded here and in
`CONTRACT_NOTES.md`.

### Pinned versions (verified against upstream)

| Component | Version | Why |
|---|---|---|
| Spark | 3.5.3 (Scala 2.13, hadoop3) | bundles Hadoop 3.3.4 |
| hadoop-aws | 3.3.4 | must match the bundled Hadoop (S3A → MinIO) |
| aws-java-sdk-bundle | 1.12.262 | the version hadoop-aws 3.3.4 was built against |
| mongo-spark-connector_2.13 | 10.4.1 | analytic write path to `insights_*` |
