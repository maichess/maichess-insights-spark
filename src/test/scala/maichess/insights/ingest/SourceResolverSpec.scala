package maichess.insights.ingest

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SourceResolverSpec extends AnyFlatSpec with Matchers {

  "resolve" should "build the Lichess monthly URL (compressed)" in {
    val r = SourceResolver.resolve(SourceDescriptor.LichessMonth("2024-12"), "insights-raw")
    r.uri shouldBe "https://database.lichess.org/standard/lichess_db_standard_rated_2024-12.pgn.zst"
    r.compressed shouldBe true
  }

  "resolve" should "build an s3a URI for an upload and detect compression by suffix" in {
    SourceResolver.resolve(SourceDescriptor.Upload("uploads/a.pgn"), "insights-raw") shouldBe
      RawSource("s3a://insights-raw/uploads/a.pgn", compressed = false)
    SourceResolver.resolve(SourceDescriptor.Upload("uploads/a.pgn.zst"), "raw") shouldBe
      RawSource("s3a://raw/uploads/a.pgn.zst", compressed = true)
  }
}
