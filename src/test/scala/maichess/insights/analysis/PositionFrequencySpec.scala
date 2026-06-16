package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PositionFrequencySpec extends AnyFlatSpec with Matchers with SparkSupport {

  import AnalysisFixtures._

  // Same placement, different clock fields → must collapse to one normalized FEN.
  private val fenA = "8/8/8/8/8/4k3/4P3/4K3 w - - 0 20"
  private val fenB = "8/8/8/8/8/4k3/4P3/4K3 w - - 9 30"
  private val other = "8/8/8/8/8/5k2/5P2/5K2 w - - 0 20"

  private def datasets(games: Seq[GameRow], plies: Seq[PlyRow]) = {
    val s = spark
    import s.implicits._
    (s.createDataset(games.toList), s.createDataset(plies.toList))
  }

  "compute" should "collapse transpositions and count reaching games with outcomes" taggedAs SparkTest in {
    val gs = Seq(game("g1", "1-0"), game("g2", "0-1"), game("g3", "1/2-1/2"))
    val ps = Seq(
      ply("g1", 30, "white", "Kd2", fenBefore = Some(fenA)),
      ply("g2", 40, "white", "Ke2", fenBefore = Some(fenB)), // same position, different clocks
      ply("g3", 50, "white", "Kf2", fenBefore = Some(other)),
    )
    val (g, p) = datasets(gs, ps)
    val r = PositionFrequency.compute(g, p, corpus)(spark).collect()
    val top = r.maxBy(_.reachCount)
    top.normalizedFen shouldBe "8/8/8/8/8/4k3/4P3/4K3 w - -"
    top.reachCount shouldBe 2
    top.whiteWinRate shouldBe 0.5
    top.blackWinRate shouldBe 0.5
    top.drawRate shouldBe 0.0
  }

  it should "count a position once per game and apply the min-reach floor" taggedAs SparkTest in {
    val gs = Seq(game("g1", "1-0"))
    val ps = Seq(
      ply("g1", 30, "white", "Kd2", fenBefore = Some(fenA)),
      ply("g1", 32, "white", "Ke2", fenBefore = Some(fenB)), // same normalized pos, same game
    )
    val (g, p) = datasets(gs, ps)
    PositionFrequency.compute(g, p, corpus, minReach = 2)(spark).collect() shouldBe empty
    PositionFrequency.compute(g, p, corpus, minReach = 1)(spark).head.reachCount shouldBe 1
  }

  it should "exclude early book plies" taggedAs SparkTest in {
    val gs = Seq(game("g1", "1-0"))
    val ps = Seq(
      ply("g1", 4, "black", "e5", fenBefore = Some(fenA)),
      ply("g1", 30, "white", "Kf2", fenBefore = Some(other)),
    )
    val (g, p) = datasets(gs, ps)
    val r = PositionFrequency.compute(g, p, corpus, bookPlies = 12)(spark).collect()
    r.map(_.normalizedFen) shouldBe Array("8/8/8/8/8/5k2/5P2/5K2 w - -")
  }
}
