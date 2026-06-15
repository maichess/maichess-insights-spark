# maichess-insights-spark — custom Spark image for the insights program.
#
# Scala 2.13 (per the insights ADR: Spark connectors lag Scala 3). The official
# apache/spark Docker images are Scala 2.12 ONLY — a Scala 2.13 application jar is
# binary-incompatible with a 2.12 Spark runtime — so the runtime is built from the
# Apache Spark *Scala 2.13 distribution tarball*, not FROM apache/spark.
#
# Published as ghcr.io/maichess/maichess-insights-spark by docker-publish.yml. The
# Spark Operator (maichess-deploy) runs this image as SparkApplication driver +
# executor pods. The jobs jar content lands in tasks 03/04; this Dockerfile wires
# the image (base + connector jars + assembly), so `sbt assembly` (hence a full
# build) requires the Scala module added in task 03.

# ── Build stage: assemble the Scala 2.13 jobs fat jar ────────────────────────
FROM eclipse-temurin:17-jdk-noble AS build

ARG SBT_VERSION=1.10.11
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    curl -fL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" | \
    tar -xz -C /usr/local --strip-components=1 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Warm the dependency cache (added in task 03).
COPY project/ project/
COPY build.sbt ./
RUN --mount=type=secret,id=GITHUB_TOKEN \
    --mount=type=secret,id=GITHUB_ACTOR \
    GITHUB_TOKEN=$(cat /run/secrets/GITHUB_TOKEN) \
    GITHUB_ACTOR=$(cat /run/secrets/GITHUB_ACTOR) \
    sbt update

COPY src/ src/
RUN --mount=type=secret,id=GITHUB_TOKEN \
    --mount=type=secret,id=GITHUB_ACTOR \
    GITHUB_TOKEN=$(cat /run/secrets/GITHUB_TOKEN) \
    GITHUB_ACTOR=$(cat /run/secrets/GITHUB_ACTOR) \
    sbt 'set assembly / test := {}' assembly && \
    cp target/scala-2.13/app.jar /app/app.jar

# ── Runtime stage: Scala 2.13 Spark + S3A (MinIO) + Mongo connector ───────────
FROM eclipse-temurin:17-jre-noble AS runtime

# Spark + bundled Hadoop versions. hadoop-aws MUST match the Hadoop the tarball
# bundles (Spark 3.5.3 "hadoop3" => Hadoop 3.3.4); aws-java-sdk-bundle is the
# version hadoop-aws 3.3.4 was built against.
ARG SPARK_VERSION=3.5.3
ARG HADOOP_AWS_VERSION=3.3.4
ARG AWS_SDK_BUNDLE_VERSION=1.12.262
ARG MONGO_SPARK_VERSION=10.4.1

ENV SPARK_HOME=/opt/spark
ENV PATH="${SPARK_HOME}/bin:${PATH}"

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl bash tini procps && \
    rm -rf /var/lib/apt/lists/*

# Scala 2.13 Spark distribution (NOT the 2.12 docker image). Provides the proper
# /opt/spark layout and the official Kubernetes entrypoint the operator expects.
RUN curl -fL "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3-scala2.13.tgz" \
      -o /tmp/spark.tgz && \
    mkdir -p "${SPARK_HOME}" && \
    tar -xzf /tmp/spark.tgz -C "${SPARK_HOME}" --strip-components=1 && \
    rm /tmp/spark.tgz && \
    cp "${SPARK_HOME}/kubernetes/dockerfiles/spark/entrypoint.sh" /opt/entrypoint.sh && \
    chmod +x /opt/entrypoint.sh

# Connector jars: S3A (MinIO access) + the Mongo Spark connector (analytic write
# path). Dropped into Spark's jars dir so they are on the driver/executor classpath
# without --packages (no submit-time network in cluster mode).
RUN cd "${SPARK_HOME}/jars" && \
    curl -fLO "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/${HADOOP_AWS_VERSION}/hadoop-aws-${HADOOP_AWS_VERSION}.jar" && \
    curl -fLO "https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/${AWS_SDK_BUNDLE_VERSION}/aws-java-sdk-bundle-${AWS_SDK_BUNDLE_VERSION}.jar" && \
    curl -fLO "https://repo1.maven.org/maven2/org/mongodb/spark/mongo-spark-connector_2.13/${MONGO_SPARK_VERSION}/mongo-spark-connector_2.13-${MONGO_SPARK_VERSION}.jar"

# The insights jobs fat jar.
COPY --from=build /app/app.jar ${SPARK_HOME}/jars/maichess-insights-spark.jar

# Run as the non-root spark uid the operator/Spark expect.
RUN groupadd -g 185 spark && useradd -u 185 -g 185 -m spark
USER 185

WORKDIR ${SPARK_HOME}
ENTRYPOINT ["/opt/entrypoint.sh"]
