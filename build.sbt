ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "io.github.maichess"

// Spark 3.5.x is Scala 2.13 (the official apache/spark images are 2.12 — see the
// Dockerfile). Connectors lag Scala 3, so the whole module stays on 2.13.
val sparkVersion       = "3.5.3"
val mongoSparkVersion  = "10.4.1"
val chesslibVersion    = "1.3.6"
val scalatestVersion   = "3.2.19"

lazy val root = (project in file("."))
  .settings(
    name := "maichess-insights-spark",

    // chesslib (board replay → FEN) is distributed via JitPack, not Maven Central.
    resolvers += "jitpack" at "https://jitpack.io",

    libraryDependencies ++= Seq(
      // Spark + the Mongo connector are supplied by the runtime image (task 02), so
      // they are Provided (kept out of the assembly) but also pulled into Test so
      // the local[*] SparkSession suites can run.
      "org.apache.spark"     %% "spark-core"           % sparkVersion      % "provided,test",
      "org.apache.spark"     %% "spark-sql"            % sparkVersion      % "provided,test",
      "org.mongodb.spark"    %% "mongo-spark-connector" % mongoSparkVersion % "provided,test",
      // zstd-jni: decompress the .pgn.zst corpora. Spark bundles this for its own zstd
      // codec so it is already in the runtime image → provided (out of the assembly),
      // test for the suites. Pinned to what Spark 3.5.3 ships to avoid a classpath skew.
      "com.github.luben"     % "zstd-jni"               % "1.5.5-4"         % "provided,test",
      // MongoDB Java driver: the ingest job point-updates insights_corpora.game_count.
      // The connector pulls it transitively at runtime; declare it explicitly (matching
      // the image's version) so com.mongodb.client.* is on the compile/test classpath.
      // provided → already in the image, kept out of the assembly.
      "org.mongodb"          % "mongodb-driver-sync"   % "5.1.4"           % "provided,test",
      // chesslib IS bundled (not in the image) — compile scope so assembly includes it.
      "com.github.bhlangonijr" % "chesslib"            % chesslibVersion,
      "org.scalatest"        %% "scalatest"            % scalatestVersion  % Test,
    ),

    // Spark needs a forked JVM with module access opened (Java 17+). The image runs
    // Java 17; these flags let the local[*] test suites run on a JDK that has the
    // modules. (Spark 3.5 does not support Java 21+.)
    Test / fork := true,
    // Spark 3.5 does not run on Java 18+. Suites that spin up a local[*]
    // SparkSession are tagged `maichess.insights.SparkTest`; exclude them when the
    // test JVM is too new (keeps `sbt test` green on a dev JDK 21/25) — CI + the
    // image run Java 17, where they execute normally.
    Test / testOptions ++= {
      val javaMajor = System.getProperty("java.specification.version").toInt
      if (javaMajor >= 18)
        Seq(Tests.Argument(TestFrameworks.ScalaTest, "-l", "maichess.insights.SparkTest"))
      else Seq.empty
    },
    Test / javaOptions ++= Seq(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
      "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
      "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
    ),

    // ── Assembly (fat jar → target/scala-2.13/app.jar, copied by the Dockerfile) ──
    assembly / assemblyJarName := "app.jar",
    assembly / mainClass := Some("maichess.insights.ingest.IngestJob"),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _ @ _*) => MergeStrategy.concat
      case PathList("META-INF", _ @ _*)             => MergeStrategy.discard
      case "module-info.class"                      => MergeStrategy.discard
      case x                                        => MergeStrategy.first
    },

    // ── Coverage (mirror these exclusions in stryker4s.conf) ─────────────────────
    // Excluded: Spark job mains (I/O orchestration), source adapters + the Mongo
    // sink (live network/S3/Mongo glue) — the pure parse/replay/aggregation logic
    // is what carries the coverage bar.
    coverageExcludedPackages := Seq(
      "maichess.insights.ingest.IngestJob",
      "maichess.insights.analysis.AnalysisJob",
      "maichess.insights.source.*",
      "maichess.insights.sink.*",
    ).mkString(";"),
    coverageFailOnMinimum := false,
  )
