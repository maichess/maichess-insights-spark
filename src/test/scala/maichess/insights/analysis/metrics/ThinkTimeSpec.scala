package maichess.insights.analysis.metrics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ThinkTimeSpec extends AnyFlatSpec with Matchers {

  "spentMs" should "be the clock drop plus increment" in {
    // 300000 → 280000 with no increment = 20s thought.
    ThinkTime.spentMs(prevClockMs = 300000, thisClockMs = 280000, incrementMs = 0) shouldBe 20000
    // With a 2s increment, the player actually used 22s to drop 20s.
    ThinkTime.spentMs(prevClockMs = 300000, thisClockMs = 280000, incrementMs = 2000) shouldBe 22000
  }

  it should "clamp negative deltas (clock noise) to zero" in {
    ThinkTime.spentMs(prevClockMs = 280000, thisClockMs = 300000, incrementMs = 0) shouldBe 0
  }

  "incrementMs" should "parse base+inc and default to zero" in {
    ThinkTime.incrementMs("180+2") shouldBe 2000
    ThinkTime.incrementMs("300+0") shouldBe 0
    ThinkTime.incrementMs("600") shouldBe 0
    ThinkTime.incrementMs("-") shouldBe 0
    ThinkTime.incrementMs("180+x") shouldBe 0
  }
}
