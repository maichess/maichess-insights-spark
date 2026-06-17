package maichess.insights.ingest

import maichess.insights.filter.CorpusFilter
import maichess.insights.pgn.PgnParserFixtures
import maichess.insights.{SparkSupport, SparkTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IngestionSpec extends AnyFlatSpec with Matchers with SparkSupport {

  private def gamesDs(texts: String*) = {
    val s = spark
    import s.implicits._
    s.createDataset(texts.toList)
  }

  "transform" should "produce games + plies for each parsed game" taggedAs SparkTest in {
    val (g, p) = Ingestion.transform(
      gamesDs(PgnParserFixtures.game, PgnParserFixtures.blunderGame),
      "c", CorpusFilter(), replayBoard = false,
    )(spark)
    g.count() shouldBe 2
    p.count() shouldBe 8
  }

  it should "drop games failing the rating-band filter" taggedAs SparkTest in {
    val (g, _) = Ingestion.transform(
      gamesDs(PgnParserFixtures.game, PgnParserFixtures.blunderGame),
      "c", CorpusFilter(ratingBand = Some("1600-1999")), replayBoard = false,
    )(spark)
    g.collect().map(_.gameId).toSet shouldBe Set("abcd1234")
  }

  it should "fill fen_before when replay is enabled" taggedAs SparkTest in {
    val (_, p) = Ingestion.transform(
      gamesDs(PgnParserFixtures.game), "c", CorpusFilter(), replayBoard = true,
    )(spark)
    p.collect().forall(_.fenBefore.isDefined) shouldBe true
  }

  it should "skip non-PGN chunks without failing" taggedAs SparkTest in {
    val (g, _) = Ingestion.transform(
      gamesDs(PgnParserFixtures.game, "not a game"), "c", CorpusFilter(), replayBoard = false,
    )(spark)
    g.count() shouldBe 1
  }

  // Regression: a CRLF upload's "\r\n\r\n[Event " separators don't match IngestJob's
  // "\n\n[Event " record delimiter, so the whole multi-game file arrives as one record.
  // splitGames must still split it into the individual games (previously merged into one).
  it should "split a single CRLF record holding multiple games" taggedAs SparkTest in {
    val blob = Seq(PgnParserFixtures.game, PgnParserFixtures.blunderGame)
      .map(_.replace("\n", "\r\n"))
      .mkString("\r\n\r\n")
    val (g, p) = Ingestion.transform(gamesDs(blob), "c", CorpusFilter(), replayBoard = false)(spark)
    g.count() shouldBe 2
    p.count() shouldBe 8
  }
}
