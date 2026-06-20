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

🟡 **Ingestion + analysis jobs landed (tasks 03, 04).** The sbt project (`build.sbt`,
`src/`) plus the container image wiring (`Dockerfile`, `docker-publish.yml`) are in
place:

- **task 03** — ingestion + PGN parser (`.zst` download, decompress-once, parse
  `%eval`/`%clk` → partitioned Parquet). Entry point `IngestJob`.
- **task 04** — analysis jobs (opening / endgame / position / tricky / summary →
  Mongo `insights_*`, aggregate Parquet cache in `insights-agg`, run recorded in
  `insights_jobs`). Entry point `AnalysisJob`.

`sbt test` is green; the pure parse/replay/aggregation logic is unit-tested and the
`local[*]` `SparkSession` transformation suites are tagged `maichess.insights.SparkTest`
(they run on **Java 17** — Spark 3.5 cannot start a `SparkContext` on Java 18+, so the
build excludes that tag on a newer dev JVM; CI and the image run Java 17).

## Entry points

The assembly default main is `IngestJob`; the analysis run uses `AnalysisJob`. The
`SparkApplication` (task 05) selects the class via `mainClass`:

- **`maichess.insights.ingest.IngestJob`** — `--source-type lichess|upload|tournament …
  --corpus-id … [--replay]` → `insights-parsed` Parquet. The `tournament` source
  (`--source-type tournament --tournament-server <url> --tournament-id <id>`) fetches
  a finished tournament's public `analytics-export` (`schemaVersion "1.0"`), replays
  its UCI games for SAN + `fen_before`, and writes the same parsed layout — so the
  analysis jobs run over a tournament corpus unchanged (board replay is always on for
  this source; a tournament is tiny next to a Lichess dump). Tournament games carry no
  Elo, ECO, or per-move eval/clock, so rating-band/opening/blunder/think-time metrics
  are empty for them — the value is in the position/endgame/summary jobs.
- **`maichess.insights.analysis.AnalysisJob`** — `--corpus-id … --mongo-uri …
  [--jobs openings,endgames,positions,tricky,summary] [--book-plies N]
  [--min-reach N] [--min-support N]` → `insights_*` collections + `insights-agg`
  Parquet + an `insights_jobs` record. Endgame/position jobs need ingestion to have
  run with `--replay` (they read `fen_before`).

The Spark job mains, the source adapters, and the Mongo sink (`maichess.insights.sink.*`)
are live-I/O glue, excluded from coverage (`build.sbt`) and mutation (`stryker4s.conf`);
the pure logic and per-metric jobs carry the bar.

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
