package maichess.insights.filter

import maichess.insights.model.GameRow
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FiltersSpec extends AnyFlatSpec with Matchers {

  private def game(id: String, band: String = "1600-1999", tc: String = "blitz"): GameRow =
    GameRow(
      corpusId = "c", gameId = id, result = "1-0", eco = "C20", openingName = "x",
      whiteElo = Some(1700), blackElo = Some(1650), timeControl = tc, termination = "Normal",
      plyCount = 4, yearMonth = "2024-12", ratingBand = band,
    )

  "bandOk" should "match or pass through" in {
    Filters.bandOk(game("a"), CorpusFilter(ratingBand = Some("1600-1999"))) shouldBe true
    Filters.bandOk(game("a"), CorpusFilter(ratingBand = Some("2400+"))) shouldBe false
    Filters.bandOk(game("a"), CorpusFilter()) shouldBe true
  }

  "timeOk" should "match or pass through" in {
    Filters.timeOk(game("a"), CorpusFilter(timeControl = Some("blitz"))) shouldBe true
    Filters.timeOk(game("a"), CorpusFilter(timeControl = Some("bullet"))) shouldBe false
    Filters.timeOk(game("a"), CorpusFilter()) shouldBe true
  }

  "dateOk" should "honor inclusive bounds and reject unknown dates under a filter" in {
    val f = CorpusFilter(dateFrom = Some("2024-12-01"), dateTo = Some("2024-12-31"))
    Filters.dateOk(Some("2024-12-15"), f) shouldBe true
    Filters.dateOk(Some("2024-11-30"), f) shouldBe false
    Filters.dateOk(Some("2025-01-01"), f) shouldBe false
    Filters.dateOk(None, f) shouldBe false
    Filters.dateOk(None, CorpusFilter()) shouldBe true
  }

  "sampleOk" should "keep everything outside (0,1) and be deterministic inside" in {
    Filters.sampleOk(game("a"), CorpusFilter(sampleRate = 0.0)) shouldBe true
    Filters.sampleOk(game("a"), CorpusFilter(sampleRate = 1.0)) shouldBe true
    val f = CorpusFilter(sampleRate = 0.5)
    val g = game("stable-id")
    Filters.sampleOk(g, f) shouldBe Filters.sampleOk(g, f)
  }

  "bucket" should "land in [0,1)" in {
    val b = Filters.bucket("some-game-id")
    b should (be >= 0.0 and be < 1.0)
  }

  "keep" should "combine all predicates" in {
    val f = CorpusFilter(ratingBand = Some("1600-1999"), timeControl = Some("blitz"))
    Filters.keep(game("a"), Some("2024-12-05"), f) shouldBe true
    Filters.keep(game("a", band = "2400+"), Some("2024-12-05"), f) shouldBe false
  }
}
