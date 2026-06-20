package maichess.insights.ingest

import maichess.insights.filter.CorpusFilter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JobArgsSpec extends AnyFlatSpec with Matchers {

  "parse" should "read a Lichess ingestion with filter + replay" in {
    val a = JobArgs.parse(Array(
      "--corpus-id", "lichess-2024-12-blitz",
      "--source-type", "lichess",
      "--lichess-month", "2024-12",
      "--rating-band", "1600-1999",
      "--time-control", "blitz",
      "--sample-rate", "0.15",
      "--replay", "true",
      "--mongo-uri", "mongodb://mongo:27017",
      "--mongo-db", "maichess",
    ))
    a.corpusId shouldBe "lichess-2024-12-blitz"
    a.source shouldBe SourceDescriptor.LichessMonth("2024-12")
    a.filter shouldBe CorpusFilter(Some("1600-1999"), Some("blitz"), None, None, 0.15)
    a.replayBoard shouldBe true
    a.rawBucket shouldBe "insights-raw"
    a.parsedBucket shouldBe "insights-parsed"
    a.mongoUri shouldBe Some("mongodb://mongo:27017")
    a.mongoDb shouldBe "maichess"
  }

  it should "read an upload ingestion with defaults" in {
    val a = JobArgs.parse(Array(
      "--corpus-id", "upload-7",
      "--source-type", "upload",
      "--upload-key", "uploads/x.pgn",
    ))
    a.source shouldBe SourceDescriptor.Upload("uploads/x.pgn")
    a.filter.sampleRate shouldBe 0.0
    a.replayBoard shouldBe false
    a.mongoUri shouldBe None
    a.mongoDb shouldBe "maichess"
  }

  it should "read a tournament-export ingestion" in {
    val a = JobArgs.parse(Array(
      "--corpus-id", "tournament-t7kXq2",
      "--source-type", "tournament",
      "--tournament-server", "https://nowchess.example.de",
      "--tournament-id", "t7kXq2",
    ))
    a.source shouldBe SourceDescriptor.TournamentExport("https://nowchess.example.de", "t7kXq2")
    a.corpusId shouldBe "tournament-t7kXq2"
  }

  it should "reject a tournament source missing the server or id" in {
    an[IllegalArgumentException] should be thrownBy
      JobArgs.parse(Array("--corpus-id", "c", "--source-type", "tournament", "--tournament-id", "t1"))
    an[IllegalArgumentException] should be thrownBy
      JobArgs.parse(Array("--corpus-id", "c", "--source-type", "tournament", "--tournament-server", "https://x"))
  }

  it should "reject an unknown or missing source type" in {
    an[IllegalArgumentException] should be thrownBy
      JobArgs.parse(Array("--corpus-id", "c", "--source-type", "ftp"))
    an[IllegalArgumentException] should be thrownBy
      JobArgs.parse(Array("--corpus-id", "c"))
  }

  it should "reject a missing required key" in {
    an[IllegalArgumentException] should be thrownBy
      JobArgs.parse(Array("--source-type", "lichess", "--lichess-month", "2024-12"))
  }

  "toMap" should "treat a bare flag as true" in {
    JobArgs.toMap(Array("--replay", "--corpus-id", "c")) shouldBe
      Map("replay" -> "true", "corpus-id" -> "c")
  }
}
