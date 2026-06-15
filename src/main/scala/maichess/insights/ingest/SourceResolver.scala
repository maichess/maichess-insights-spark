package maichess.insights.ingest

/** A resolved raw source: where the bytes are and whether they are zstd-compressed. */
final case class RawSource(uri: String, compressed: Boolean)

/** Pure resolution of a [[SourceDescriptor]] to a fetchable URI. Pluggable: new
  * sources (Chess.com, TWIC) add a case here. The actual streaming/decompress is in
  * [[IngestJob]] (I/O glue, not unit-tested).
  */
object SourceResolver {

  /** Lichess monthly standard dump URL pattern. */
  def lichessUrl(yearMonth: String): String =
    s"https://database.lichess.org/standard/lichess_db_standard_rated_$yearMonth.pgn.zst"

  def resolve(source: SourceDescriptor, rawBucket: String): RawSource = source match {
    case SourceDescriptor.LichessMonth(ym) =>
      RawSource(lichessUrl(ym), compressed = true)
    case SourceDescriptor.Upload(key) =>
      RawSource(s"s3a://$rawBucket/$key", compressed = key.endsWith(".zst"))
  }
}
