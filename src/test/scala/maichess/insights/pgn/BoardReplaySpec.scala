package maichess.insights.pgn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoardReplaySpec extends AnyFlatSpec with Matchers {

  "replay" should "produce UCI + fen-before for each legal SAN" in {
    val r = BoardReplay.replay(Seq("e4", "e5", "Nf3", "Nc6"))
    r should have size 4
    r.head.uci.toLowerCase shouldBe "e2e4"
    r.head.fenBefore should startWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w")
    r(1).uci.toLowerCase shouldBe "e7e5"
    // fen-before of move 2 has white's e-pawn already on e4
    r(1).fenBefore should startWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b")
  }

  it should "return empty for no moves" in {
    BoardReplay.replay(Seq.empty) shouldBe empty
  }

  it should "stop at the first illegal/garbled SAN" in {
    val r = BoardReplay.replay(Seq("e4", "e5", "Zz9", "Nc6"))
    r.map(_.uci.toLowerCase) shouldBe Vector("e2e4", "e7e5")
  }

  it should "handle a promotion and castling without throwing" in {
    val moves = Seq("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "O-O")
    val r = BoardReplay.replay(moves)
    r should have size 7
    r.last.uci.toLowerCase should (be("e1g1") or be("e1h1")) // king-side castle encodings
  }
}
