package maichess.insights.analysis

import maichess.insights.ingest.JobArgs

/** Which metric jobs an analysis run should compute. */
object MetricJob extends Enumeration {
  val Openings, Endgames, Positions, Tricky, Summary = Value

  /** All jobs, in run order. */
  val all: Seq[Value] = Seq(Openings, Endgames, Positions, Tricky, Summary)

  /** Jobs that read `fen_before` and therefore need board replay to have run. */
  val needsReplay: Set[Value] = Set(Endgames, Positions)

  def parse(name: String): Option[Value] =
    all.find(_.toString.equalsIgnoreCase(name.trim))
}

/** Parsed analysis-job arguments (the AnalysisJob main is excluded from coverage; the
  * parsing is unit-tested here). Keyed by `corpus-id`; `jobs` selects metrics
  * (comma-separated, or `all`).
  */
final case class AnalysisArgs(
    corpusId: String,
    jobs: Seq[MetricJob.Value],
    parsedBucket: String,
    aggBucket: String,
    mongoUri: String,
    mongoDb: String,
    bookPlies: Int,
    minReach: Int,
    minSupport: Int,
    jobId: String,
    sparkApplication: String,
)

object AnalysisArgs {

  def parse(args: Array[String]): AnalysisArgs = {
    val m = JobArgs.toMap(args)
    val corpusId = require(m, "corpus-id")
    AnalysisArgs(
      corpusId = corpusId,
      jobs = parseJobs(m.get("jobs")),
      parsedBucket = m.getOrElse("parsed-bucket", "insights-parsed"),
      aggBucket = m.getOrElse("agg-bucket", "insights-agg"),
      mongoUri = require(m, "mongo-uri"),
      mongoDb = m.getOrElse("mongo-db", "insights"),
      bookPlies = m.get("book-plies").flatMap(_.toIntOption).getOrElse(0),
      minReach = m.get("min-reach").flatMap(_.toIntOption).getOrElse(1),
      minSupport = m.get("min-support").flatMap(_.toIntOption).getOrElse(1),
      jobId = m.getOrElse("job-id", corpusId),
      sparkApplication = m.getOrElse("spark-application", ""),
    )
  }

  /** `all` / empty → every job; otherwise the comma-separated subset (unknown names
    * ignored, falling back to all jobs if nothing valid remains).
    */
  def parseJobs(spec: Option[String]): Seq[MetricJob.Value] =
    spec.map(_.trim).filter(s => s.nonEmpty && !s.equalsIgnoreCase("all")) match {
      case None => MetricJob.all
      case Some(s) =>
        val picked = s.split(",").toSeq.flatMap(MetricJob.parse)
        if (picked.isEmpty) MetricJob.all else picked.distinct
    }

  private def require(m: Map[String, String], key: String): String =
    m.getOrElse(key, throw new IllegalArgumentException(s"missing required --$key"))
}
