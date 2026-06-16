package maichess.insights.analysis.metrics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResultsSpec extends AnyFlatSpec with Matchers {

  "isWhiteWin/isBlackWin/isDraw" should "recognize the result strings" in {
    Results.isWhiteWin("1-0") shouldBe true
    Results.isWhiteWin("0-1") shouldBe false
    Results.isBlackWin("0-1") shouldBe true
    Results.isDraw("1/2-1/2") shouldBe true
    Results.isDraw("*") shouldBe false
  }

  "strongerScore" should "score from the stronger side's view" in {
    Results.strongerScore("1-0", strongerIsWhite = true) shouldBe 1.0
    Results.strongerScore("1-0", strongerIsWhite = false) shouldBe 0.0
    Results.strongerScore("0-1", strongerIsWhite = true) shouldBe 0.0
    Results.strongerScore("0-1", strongerIsWhite = false) shouldBe 1.0
    Results.strongerScore("1/2-1/2", strongerIsWhite = true) shouldBe 0.5
    Results.strongerScore("*", strongerIsWhite = true) shouldBe 0.5
  }

  "strongerOutcome" should "label win/draw/loss from the stronger side's view" in {
    Results.strongerOutcome("1-0", strongerIsWhite = true) shouldBe "win"
    Results.strongerOutcome("1-0", strongerIsWhite = false) shouldBe "loss"
    Results.strongerOutcome("0-1", strongerIsWhite = true) shouldBe "loss"
    Results.strongerOutcome("0-1", strongerIsWhite = false) shouldBe "win"
    Results.strongerOutcome("1/2-1/2", strongerIsWhite = true) shouldBe "draw"
    Results.strongerOutcome("*", strongerIsWhite = false) shouldBe "draw"
  }

  "terminationKind" should "classify mate by the final SAN" in {
    Results.terminationKind("Normal", Some("Qh7#")) shouldBe "mate"
  }

  it should "classify timeout, resign, and other" in {
    Results.terminationKind("Time forfeit", Some("Ke2")) shouldBe "timeout"
    Results.terminationKind("Normal", Some("Ke2")) shouldBe "resign"
    Results.terminationKind("Normal", None) shouldBe "resign"
    Results.terminationKind("Abandoned", Some("Ke2")) shouldBe "other"
    Results.terminationKind("", None) shouldBe "other"
  }
}
