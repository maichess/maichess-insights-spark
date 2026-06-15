package maichess.insights.pgn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TimeControlSpec extends AnyFlatSpec with Matchers {

  "classify" should "map base+increment to a speed class" in {
    TimeControl.classify("15+0") shouldBe "ultrabullet"
    TimeControl.classify("60+0") shouldBe "bullet"
    TimeControl.classify("180+0") shouldBe "blitz"
    TimeControl.classify("300+0") shouldBe "blitz"
    TimeControl.classify("600+0") shouldBe "rapid"
    TimeControl.classify("1800+0") shouldBe "classical"
  }

  it should "fold the increment into the estimate" in {
    // 60 + 40*5 = 260 -> blitz
    TimeControl.classify("60+5") shouldBe "blitz"
  }

  it should "return unknown for correspondence or garbage" in {
    TimeControl.classify("-") shouldBe "unknown"
    TimeControl.classify("") shouldBe "unknown"
    TimeControl.classify("a+b") shouldBe "unknown"
    TimeControl.classify("1+2+3") shouldBe "unknown"
  }

  "estimateSeconds" should "handle a bare base with no increment" in {
    TimeControl.estimateSeconds("120") shouldBe Some(120)
  }
}
