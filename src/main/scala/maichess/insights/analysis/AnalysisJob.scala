package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}
import maichess.insights.sink.MongoSink
import org.apache.spark.sql.{Dataset, SaveMode, SparkSession}

/** Analysis Spark entry point (invoked by the `SparkApplication` from task 05).
  *
  * Reads the parsed `games`/`plies` Parquet for a corpus, computes the requested
  * insight metrics, materializes each into its `insights_*` Mongo collection via the
  * connector, caches the aggregate Parquet in `insights-agg`, and records the run in
  * `insights_jobs` (succeeded/failed) for the control plane to read.
  *
  * I/O orchestration only — the testable aggregation lives in the per-metric objects
  * (`OpeningStats`, `EndgameStats`, `PositionFrequency`, `TrickyPositions`,
  * `Summary`). Excluded from coverage (mirrored in `stryker4s.conf`).
  */
object AnalysisJob {

  def main(args: Array[String]): Unit = {
    val cfg = AnalysisArgs.parse(args)
    implicit val spark: SparkSession =
      SparkSession.builder().appName(s"insights-analysis-${cfg.corpusId}").getOrCreate()
    try run(cfg)
    finally spark.stop()
  }

  def run(cfg: AnalysisArgs)(implicit spark: SparkSession): Unit = {
    import spark.implicits._
    val sink = new MongoSink(cfg.mongoUri, cfg.mongoDb)

    val base = s"s3a://${cfg.parsedBucket}/${cfg.corpusId}"
    val games: Dataset[GameRow] = spark.read.parquet(s"$base/games").as[GameRow]
    val plies: Dataset[PlyRow] = spark.read.parquet(s"$base/plies").as[PlyRow]

    // Job lifecycle status is owned by the control plane's SparkStatusReconciler (it maps
    // the SparkApplication state to the insights_jobs record by jobId via the k8s watch).
    // The Spark side does NOT write insights_jobs: a connector append created a document
    // with an ObjectId _id + camelCase fields, which the control plane's catalog (string
    // _id, snake_case) could not read — breaking the whole job/metric listing.
    cfg.jobs.foreach {
      case MetricJob.Openings =>
        val r = OpeningStats.compute(games, cfg.corpusId)
        sink.write(r, "insights_openings"); cacheAgg(r, cfg, "openings")
      case MetricJob.Endgames =>
        val r = EndgameStats.compute(games, plies, cfg.corpusId)
        sink.write(r, "insights_endgames"); cacheAgg(r, cfg, "endgames")
      case MetricJob.Positions =>
        val r = PositionFrequency.compute(games, plies, cfg.corpusId, cfg.bookPlies, cfg.minReach)
        sink.write(r, "insights_positions"); cacheAgg(r, cfg, "positions")
      case MetricJob.Tricky =>
        val r = TrickyPositions.compute(plies, cfg.corpusId, cfg.minSupport)
        sink.write(r, "insights_tricky"); cacheAgg(r, cfg, "tricky")
      case MetricJob.Summary =>
        val r = Summary.compute(games, plies, cfg.corpusId)
        sink.append(r, "insights_summary")
    }
  }

  private def cacheAgg[T](rows: Dataset[T], cfg: AnalysisArgs, name: String)(implicit
      spark: SparkSession,
  ): Unit =
    rows.write
      .mode(SaveMode.Overwrite)
      .parquet(s"s3a://${cfg.aggBucket}/${cfg.corpusId}/$name")
}
