package maichess.insights.analysis.metrics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BlunderMathSpec extends AnyFlatSpec with Matchers {

  "cpLoss" should "measure how much White's move worsened White's eval" in {
    // eval is White's view; White's good move keeps/raises eval → no loss.
    BlunderMath.cpLoss(evalBefore = 20, evalAfter = 30, white = true) shouldBe 0
    // White blunders: eval drops 400 → +50.
    BlunderMath.cpLoss(evalBefore = 50, evalAfter = -350, white = true) shouldBe 400
  }

  it should "flip the sign for a Black move" in {
    // Black blunders: White's eval rises 380.
    BlunderMath.cpLoss(evalBefore = 20, evalAfter = 400, white = false) shouldBe 380
    // Black's good move (eval drops for White) → no loss for Black.
    BlunderMath.cpLoss(evalBefore = 20, evalAfter = -50, white = false) shouldBe 0
  }

  "isBlunder" should "trip at the threshold" in {
    BlunderMath.isBlunder(299) shouldBe false
    BlunderMath.isBlunder(300) shouldBe true
    BlunderMath.isBlunder(120, threshold = 100) shouldBe true
  }
}
