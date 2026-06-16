package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SummarySpec extends AnyFlatSpec with Matchers with SparkSupport {

  import AnalysisFixtures._

  private def datasets(games: Seq[GameRow], plies: Seq[PlyRow]) = {
    val s = spark
    import s.implicits._
    (s.createDataset(games.toList), s.createDataset(plies.toList))
  }

  private def asMap(counts: Seq[Count]) = counts.map(c => c.key -> c.gameCount).toMap

  "compute" should "produce headline aggregates for the corpus" taggedAs SparkTest in {
    val gs = Seq(
      game("g1", "1-0", plyCount = 4, yearMonth = "2024-11", ratingBand = "1600-1999", termination = "Normal"),
      game("g2", "0-1", plyCount = 6, yearMonth = "2024-12", ratingBand = "2000-2399", termination = "Time forfeit"),
      game("g3", "1/2-1/2", plyCount = 8, yearMonth = "2024-12", ratingBand = "1600-1999", termination = "Normal"),
      game("g4", "1-0", plyCount = 10, yearMonth = "2025-01", ratingBand = "1200-1599", termination = "Normal"),
    )
    val ps = Seq(
      ply("g1", 1, "white", "e4"), ply("g1", 4, "white", "Ke2"),
      ply("g2", 1, "white", "d4"), ply("g2", 6, "black", "Kf1"),
      ply("g3", 1, "white", "e4"), ply("g3", 8, "black", "Kg1"),
      ply("g4", 1, "white", "Nf3"), ply("g4", 10, "black", "Qh7#"),
    )
    val (g, p) = datasets(gs, ps)
    val s = Summary.compute(g, p, corpus)(spark)

    s.corpusId shouldBe corpus
    s.totalGames shouldBe 4
    s.dateFrom shouldBe "2024-11"
    s.dateTo shouldBe "2025-01"
    s.drawRate shouldBe 0.25
    s.avgPlyCount shouldBe 7.0
    asMap(s.ratingDistribution) shouldBe Map("1600-1999" -> 2, "2000-2399" -> 1, "1200-1599" -> 1)
    asMap(s.firstMoves) shouldBe Map("e4" -> 2, "d4" -> 1, "Nf3" -> 1)
    asMap(s.terminationMix) shouldBe Map("resign" -> 2, "timeout" -> 1, "mate" -> 1)
  }

  it should "return a zeroed summary for an empty corpus" taggedAs SparkTest in {
    val (g, p) = datasets(Seq.empty, Seq.empty)
    val s = Summary.compute(g, p, corpus)(spark)
    s.totalGames shouldBe 0
    s.drawRate shouldBe 0.0
    s.ratingDistribution shouldBe empty
    s.firstMoves shouldBe empty
    s.terminationMix shouldBe empty
  }
}
