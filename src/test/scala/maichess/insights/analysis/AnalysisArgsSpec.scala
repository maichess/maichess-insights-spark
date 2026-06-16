package maichess.insights.analysis

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnalysisArgsSpec extends AnyFlatSpec with Matchers {

  private val required = Array("--corpus-id", "lichess-2024-12", "--mongo-uri", "mongodb://m:27017")

  "parse" should "apply defaults for everything but the required args" in {
    val a = AnalysisArgs.parse(required)
    a.corpusId shouldBe "lichess-2024-12"
    a.mongoUri shouldBe "mongodb://m:27017"
    a.parsedBucket shouldBe "insights-parsed"
    a.aggBucket shouldBe "insights-agg"
    a.mongoDb shouldBe "insights"
    a.jobs shouldBe MetricJob.all
    a.bookPlies shouldBe 0
    a.minReach shouldBe 1
    a.minSupport shouldBe 1
    a.jobId shouldBe "lichess-2024-12"
    a.sparkApplication shouldBe ""
  }

  it should "override buckets, db, thresholds, job id and spark app" in {
    val a = AnalysisArgs.parse(
      required ++ Array(
        "--parsed-bucket", "pb", "--agg-bucket", "ab", "--mongo-db", "db",
        "--book-plies", "10", "--min-reach", "5", "--min-support", "8",
        "--job-id", "job-7", "--spark-application", "insights-analysis-x",
      ),
    )
    a.parsedBucket shouldBe "pb"
    a.aggBucket shouldBe "ab"
    a.mongoDb shouldBe "db"
    a.bookPlies shouldBe 10
    a.minReach shouldBe 5
    a.minSupport shouldBe 8
    a.jobId shouldBe "job-7"
    a.sparkApplication shouldBe "insights-analysis-x"
  }

  it should "require corpus-id and mongo-uri" in {
    an[IllegalArgumentException] should be thrownBy AnalysisArgs.parse(Array("--mongo-uri", "x"))
    an[IllegalArgumentException] should be thrownBy AnalysisArgs.parse(Array("--corpus-id", "c"))
  }

  "parseJobs" should "default to all jobs for None, empty, or 'all'" in {
    AnalysisArgs.parseJobs(None) shouldBe MetricJob.all
    AnalysisArgs.parseJobs(Some("")) shouldBe MetricJob.all
    AnalysisArgs.parseJobs(Some("all")) shouldBe MetricJob.all
    AnalysisArgs.parseJobs(Some("ALL")) shouldBe MetricJob.all
  }

  it should "select a comma-separated subset, ignoring unknown names" in {
    AnalysisArgs.parseJobs(Some("openings,tricky")) shouldBe Seq(MetricJob.Openings, MetricJob.Tricky)
    AnalysisArgs.parseJobs(Some("Summary, bogus")) shouldBe Seq(MetricJob.Summary)
    AnalysisArgs.parseJobs(Some("openings,openings")) shouldBe Seq(MetricJob.Openings)
  }

  it should "fall back to all jobs when no valid name remains" in {
    AnalysisArgs.parseJobs(Some("nope,nada")) shouldBe MetricJob.all
  }

  "MetricJob.needsReplay" should "cover the FEN-dependent jobs only" in {
    MetricJob.needsReplay shouldBe Set(MetricJob.Endgames, MetricJob.Positions)
  }
}
