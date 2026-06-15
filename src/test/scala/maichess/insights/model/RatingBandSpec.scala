package maichess.insights.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RatingBandSpec extends AnyFlatSpec with Matchers {

  "forElo" should "bucket each band boundary" in {
    RatingBand.forElo(800) shouldBe "<1200"
    RatingBand.forElo(1199) shouldBe "<1200"
    RatingBand.forElo(1200) shouldBe "1200-1599"
    RatingBand.forElo(1599) shouldBe "1200-1599"
    RatingBand.forElo(1600) shouldBe "1600-1999"
    RatingBand.forElo(1999) shouldBe "1600-1999"
    RatingBand.forElo(2000) shouldBe "2000-2399"
    RatingBand.forElo(2399) shouldBe "2000-2399"
    RatingBand.forElo(2400) shouldBe "2400+"
  }

  "forGame" should "average both Elos" in {
    RatingBand.forGame(Some(1700), Some(1650)) shouldBe "1600-1999"
  }

  it should "fall back to whichever Elo is present" in {
    RatingBand.forGame(Some(2500), None) shouldBe "2400+"
    RatingBand.forGame(None, Some(900)) shouldBe "<1200"
  }

  it should "be unknown when neither is present" in {
    RatingBand.forGame(None, None) shouldBe RatingBand.Unknown
  }
}
