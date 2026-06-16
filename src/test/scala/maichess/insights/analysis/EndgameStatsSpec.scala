package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EndgameStatsSpec extends AnyFlatSpec with Matchers with SparkSupport {

  import AnalysisFixtures._

  private def datasets(games: Seq[GameRow], plies: Seq[PlyRow]) = {
    val s = spark
    import s.implicits._
    (s.createDataset(games.toList), s.createDataset(plies.toList))
  }

  "compute" should "classify endgame signatures and conversion tendency" taggedAs SparkTest in {
    // Two games reach KPvK (White stronger): one White win, one draw.
    val gs = Seq(game("g1", "1-0"), game("g2", "1/2-1/2"))
    val ps = Seq(
      ply("g1", 40, "white", "Kd2", fenBefore = Some(kpkFen)),
      ply("g2", 50, "white", "Ke2", fenBefore = Some(kpkFen)),
    )
    val (g, p) = datasets(gs, ps)
    val r = EndgameStats.compute(g, p, corpus)(spark).collect()
    r should have length 1
    val kpk = r.head
    kpk.materialSignature shouldBe "KPvK"
    kpk.frequency shouldBe 2
    kpk.strongerSideWinRate shouldBe 0.5
    kpk.drawRate shouldBe 0.5
    kpk.strongerSideLossRate shouldBe 0.0
  }

  it should "count each (game, signature) once even when it lingers" taggedAs SparkTest in {
    val gs = Seq(game("g1", "1-0"))
    val ps = Seq(
      ply("g1", 40, "white", "Kd2", fenBefore = Some(kpkFen)),
      ply("g1", 42, "white", "Ke2", fenBefore = Some(kpkFen)),
      ply("g1", 44, "white", "Kf2", fenBefore = Some(kpkFen)),
    )
    val (g, p) = datasets(gs, ps)
    val r = EndgameStats.compute(g, p, corpus)(spark).collect()
    r.head.frequency shouldBe 1
  }

  it should "ignore plies without a replayed FEN and non-endgame positions" taggedAs SparkTest in {
    val opening = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val gs = Seq(game("g1", "1-0"))
    val ps = Seq(
      ply("g1", 1, "white", "e4", fenBefore = None),
      ply("g1", 2, "black", "e5", fenBefore = Some(opening)),
    )
    val (g, p) = datasets(gs, ps)
    EndgameStats.compute(g, p, corpus)(spark).collect() shouldBe empty
  }
}
