package maichess.insights.analysis

import maichess.insights.model.PlyRow
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TrickyPositionsSpec extends AnyFlatSpec with Matchers with SparkSupport {

  import AnalysisFixtures._

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  private val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
  private val afterE5 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
  // The "tricky" position: it's Black to move after 1.e4 e5 2.Nf3.
  private val afterNf3 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"
  private val afterNf3Norm = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq -"

  private def plies(rows: PlyRow*) = {
    val s = spark
    import s.implicits._
    s.createDataset(rows.toList)
  }

  // Black blunders Qf6 from afterNf3: eval +20 → +400 (White's view), and spends 20s.
  private val blunderGame = Seq(
    ply("g1", 1, "white", "e4", Some(startFen), Some(10), Some(300000)),
    ply("g1", 2, "black", "e5", Some(afterE4), Some(10), Some(300000)),
    ply("g1", 3, "white", "Nf3", Some(afterE5), Some(20), Some(295000)),
    ply("g1", 4, "black", "Qf6", Some(afterNf3), Some(400), Some(280000)),
  )

  // Black plays the sound Nc6 from afterNf3: eval stays +20, spends 15s.
  private val soundGame = Seq(
    ply("g2", 1, "white", "e4", Some(startFen), Some(10), Some(300000)),
    ply("g2", 2, "black", "e5", Some(afterE4), Some(10), Some(300000)),
    ply("g2", 3, "white", "Nf3", Some(afterE5), Some(20), Some(295000)),
    ply("g2", 4, "black", "Nc6", Some(afterNf3), Some(20), Some(285000)),
  )

  "compute" should "score centipawn loss, blunder probability, and think time" taggedAs SparkTest in {
    val r = TrickyPositions.compute(plies(blunderGame: _*), corpus)(spark).collect()
    val tricky = r.find(_.normalizedFen == afterNf3Norm).get
    tricky.support shouldBe 1
    tricky.avgCentipawnLoss shouldBe 380.0
    tricky.blunderProbability shouldBe 1.0
    tricky.avgThinkTimeMs shouldBe 20000.0
    // It should rank first (highest cp-loss).
    r.head.normalizedFen shouldBe afterNf3Norm
  }

  it should "average across multiple moves from the same position" taggedAs SparkTest in {
    val r = TrickyPositions.compute(plies(blunderGame ++ soundGame: _*), corpus)(spark).collect()
    val pos = r.find(_.normalizedFen == afterNf3Norm).get
    pos.support shouldBe 2
    pos.avgCentipawnLoss shouldBe 190.0 // (380 + 0) / 2
    pos.blunderProbability shouldBe 0.5
    pos.avgThinkTimeMs shouldBe 17500.0 // (20000 + 15000) / 2
  }

  it should "drop the first ply (no eval-before) and apply the support floor" taggedAs SparkTest in {
    // White's opening move has no previous eval and is excluded entirely.
    val r = TrickyPositions.compute(plies(blunderGame: _*), corpus)(spark).collect()
    r.exists(_.normalizedFen.startsWith("rnbqkbnr/pppppppp")) shouldBe false

    val floored = TrickyPositions.compute(plies(blunderGame: _*), corpus, minSupport = 2)(spark).collect()
    floored shouldBe empty
  }

  it should "yield zero think time when a side has no earlier move to diff against" taggedAs SparkTest in {
    // Black's first move (ply 2) has eval-before but no prior Black clock → think 0.
    val r = TrickyPositions.compute(plies(blunderGame: _*), corpus)(spark).collect()
    val afterE4Norm = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq -"
    r.find(_.normalizedFen == afterE4Norm).get.avgThinkTimeMs shouldBe 0.0
  }
}
