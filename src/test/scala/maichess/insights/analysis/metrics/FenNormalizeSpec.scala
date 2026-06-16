package maichess.insights.analysis.metrics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FenNormalizeSpec extends AnyFlatSpec with Matchers {

  "normalize" should "strip halfmove-clock and fullmove-number" in {
    FenNormalize.normalize("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"
  }

  it should "collapse transpositions that differ only by clock fields" in {
    val a = FenNormalize.normalize("8/8/8/8/8/4k3/4P3/4K3 w - - 5 40")
    val b = FenNormalize.normalize("8/8/8/8/8/4k3/4P3/4K3 w - - 0 1")
    a shouldBe b
  }

  it should "tolerate surrounding whitespace" in {
    FenNormalize.normalize("  8/8/8/8/8/4k3/4P3/4K3 w - - 0 1  ") shouldBe "8/8/8/8/8/4k3/4P3/4K3 w - -"
  }

  "isInBook" should "treat early plies as book" in {
    FenNormalize.isInBook(1) shouldBe true
    FenNormalize.isInBook(12) shouldBe true
    FenNormalize.isInBook(13) shouldBe false
    FenNormalize.isInBook(5, bookPlies = 4) shouldBe false
  }
}
