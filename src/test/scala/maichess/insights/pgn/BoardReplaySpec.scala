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

  "replayUci" should "derive SAN + fen-before for each legal UCI move" in {
    val r = BoardReplay.replayUci(Seq("e2e4", "e7e5", "g1f3", "b8c6"))
    r should have size 4
    r.map(_.uci) shouldBe Vector("e2e4", "e7e5", "g1f3", "b8c6")
    r.map(_.san) shouldBe Vector("e4", "e5", "Nf3", "Nc6")
    r.head.fenBefore should startWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w")
    r(1).fenBefore should startWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b")
  }

  it should "mark a checkmating move with '#' in its SAN" in {
    // Scholar's mate: the final queen capture is checkmate.
    val r = BoardReplay.replayUci(Seq("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"))
    r should have size 7
    r.last.san should endWith("#")
  }

  it should "return empty for no moves" in {
    BoardReplay.replayUci(Seq.empty) shouldBe empty
  }

  it should "stop at the first illegal UCI move" in {
    // e7e5 is illegal as White's reply move list expects black; here a bogus 3rd move.
    val r = BoardReplay.replayUci(Seq("e2e4", "e7e5", "e2e4", "b8c6"))
    r.map(_.uci) shouldBe Vector("e2e4", "e7e5")
  }

  it should "handle a promotion encoded in UCI" in {
    // White promotes on a8: a sequence that reaches a8=Q.
    val r = BoardReplay.replayUci(Seq("a2a4", "b7b5", "a4b5", "b8c6", "b5b6", "g8f6", "b6b7", "f6g8", "b7b8q"))
    r.last.uci shouldBe "b7b8q"
    r.last.san should startWith("b8")
  }
}
