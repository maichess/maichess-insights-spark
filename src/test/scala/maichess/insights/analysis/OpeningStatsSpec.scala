package maichess.insights.analysis

import maichess.insights.model.GameRow
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OpeningStatsSpec extends AnyFlatSpec with Matchers with SparkSupport {

  import AnalysisFixtures._

  private def games(rows: GameRow*) = {
    val s = spark
    import s.implicits._
    s.createDataset(rows.toList)
  }

  "compute" should "report win/draw/loss rates per ECO" taggedAs SparkTest in {
    val ds = games(
      game("g1", "1-0", eco = "C20"),
      game("g2", "1-0", eco = "C20"),
      game("g3", "0-1", eco = "C20"),
      game("g4", "1/2-1/2", eco = "C20"),
    )
    val r = OpeningStats.compute(ds, corpus)(spark).collect()
    r should have length 1
    val c20 = r.head
    c20.eco shouldBe "C20"
    c20.corpusId shouldBe corpus
    c20.gameCount shouldBe 4
    c20.whiteWinRate shouldBe 0.5
    c20.blackWinRate shouldBe 0.25
    c20.drawRate shouldBe 0.25
  }

  it should "drop games without an ECO and order by popularity" taggedAs SparkTest in {
    val ds = games(
      game("g1", "1-0", eco = "C50"),
      game("g2", "1-0", eco = "C50"),
      game("g3", "1-0", eco = "B10"),
      game("g4", "1-0", eco = ""),
    )
    val r = OpeningStats.compute(ds, corpus)(spark).collect()
    r.map(_.eco) shouldBe Array("C50", "B10")
    r.head.gameCount shouldBe 2
  }
}
