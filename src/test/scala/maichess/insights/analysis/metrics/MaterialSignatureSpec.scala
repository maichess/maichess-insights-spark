package maichess.insights.analysis.metrics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MaterialSignatureSpec extends AnyFlatSpec with Matchers {

  // King + pawn vs king.
  private val kpk = "8/8/8/8/8/4k3/4P3/4K3 w - - 0 1"
  // King+rook+pawn vs king+rook.
  private val krpkr = "8/8/4k3/8/8/3r4/3RP3/3K4 w - - 0 1"
  // The opening start position (32 pieces) — not an endgame.
  private val start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  "pieceCount" should "count both colors' pieces" in {
    MaterialSignature.pieceCount(kpk) shouldBe 3
    MaterialSignature.pieceCount(start) shouldBe 32
  }

  "isEndgame" should "hold at ≤7 pieces and reject the opening" in {
    MaterialSignature.isEndgame(kpk) shouldBe true
    MaterialSignature.isEndgame(krpkr) shouldBe true
    MaterialSignature.isEndgame(start) shouldBe false
    MaterialSignature.isEndgame("8/8/8/8/8/8/8/8 w - - 0 1") shouldBe false
  }

  "signature" should "write the stronger side first, canonically ordered" in {
    MaterialSignature.signature(kpk) shouldBe "KPvK"
    MaterialSignature.signature(krpkr) shouldBe "KRPvKR"
  }

  it should "order pieces Q,R,B,N,P" in {
    // White: K + Q,N,P  vs  Black: K  → "KQNP".
    MaterialSignature.signature("4k3/8/8/8/8/2N5/PQ6/4K3 w - - 0 1") shouldBe "KQNPvK"
  }

  it should "break ties deterministically by the smaller side string" in {
    // KR vs KR (equal strength): both "KR", deterministic "KRvKR".
    MaterialSignature.signature("3rk3/8/8/8/8/8/3R4/4K3 w - - 0 1") shouldBe "KRvKR"
    // KB (white) vs KN (black): equal strength (3 each); "KB" < "KN" → "KBvKN".
    MaterialSignature.signature("4k3/5n2/8/8/8/8/3B4/4K3 w - - 0 1") shouldBe "KBvKN"
  }

  "strongerIsWhite" should "favor the heavier side and break ties to White" in {
    MaterialSignature.strongerIsWhite(kpk) shouldBe true
    // Black up a rook.
    MaterialSignature.strongerIsWhite("3rk3/8/8/8/8/8/8/4K3 w - - 0 1") shouldBe false
    // Equal material → tie to White.
    MaterialSignature.strongerIsWhite("3rk3/8/8/8/8/8/3R4/4K3 w - - 0 1") shouldBe true
  }
}
