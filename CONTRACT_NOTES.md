# Contract Notes — maichess-insights-spark

## Pending: `Maichess.PlatformProtos` (Scala) v0.14.0 publish

The Spark jobs (tasks 03/04) will consume the insights event/query types from the
Scala `platform-protos` artifact authored in task 01 (`insights.proto`,
`insights_events.proto`). That package builds only after the user tags + pushes
**`v0.14.0`** on `maichess-api-contracts`. Pin the Scala Maven coordinate to
`0.14.0` when the sbt module is added in task 03.

## Deviation: Scala 2.13 Spark base image (task 02)

Task 02 specifies `FROM apache/spark:3.5.x (Scala 2.13)`. The official
`apache/spark` images are **Scala 2.12 only** (verified on Docker Hub — no
`scala2.13` tag), and a 2.13 jar cannot run on a 2.12 runtime. Per the insights ADR
(Scala 2.13 is required), the `Dockerfile` instead builds the runtime from the
**Apache Spark Scala 2.13 distribution tarball**
(`spark-3.5.3-bin-hadoop3-scala2.13.tgz`) on a Temurin JRE base, reusing the
distribution's official Kubernetes entrypoint. Minimal adjustment that satisfies
the Scala-2.13 constraint; flagged for review. Nothing else in the spec changes.

## Build dependency

`sbt assembly` (and therefore a full `docker build`) needs the sbt module from task
03. Until then the image's build stage has nothing to compile and the
`docker-publish.yml` workflow is expected to fail.
