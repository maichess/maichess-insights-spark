package maichess.insights.ingest

import maichess.insights.filter.CorpusFilter

/** Source descriptor for an ingestion (mirrors the proto `IngestionSource`). */
sealed trait SourceDescriptor
object SourceDescriptor {
  final case class LichessMonth(yearMonth: String) extends SourceDescriptor
  final case class Upload(objectKey: String) extends SourceDescriptor
}

/** Parsed ingestion-job arguments. */
final case class IngestArgs(
    corpusId: String,
    source: SourceDescriptor,
    filter: CorpusFilter,
    replayBoard: Boolean,
    rawBucket: String,
    parsedBucket: String,
)

/** Pure `--key value` argument parsing for [[IngestJob]]. Kept separate from the
  * Spark main so it is unit-testable.
  */
object JobArgs {

  def parse(args: Array[String]): IngestArgs = {
    val m = toMap(args)
    val source = m.get("source-type") match {
      case Some("lichess") =>
        SourceDescriptor.LichessMonth(require(m, "lichess-month"))
      case Some("upload") =>
        SourceDescriptor.Upload(require(m, "upload-key"))
      case other =>
        throw new IllegalArgumentException(s"unknown or missing --source-type: ${other.getOrElse("")}")
    }
    IngestArgs(
      corpusId = require(m, "corpus-id"),
      source = source,
      filter = CorpusFilter(
        ratingBand = m.get("rating-band").filter(_.nonEmpty),
        timeControl = m.get("time-control").filter(_.nonEmpty),
        dateFrom = m.get("date-from").filter(_.nonEmpty),
        dateTo = m.get("date-to").filter(_.nonEmpty),
        sampleRate = m.get("sample-rate").flatMap(_.toDoubleOption).getOrElse(0.0),
      ),
      replayBoard = m.get("replay").exists(v => v == "true" || v == "1"),
      rawBucket = m.getOrElse("raw-bucket", "insights-raw"),
      parsedBucket = m.getOrElse("parsed-bucket", "insights-parsed"),
    )
  }

  /** Fold `--key value` pairs (and bare `--flag` → "true") into a map. */
  def toMap(args: Array[String]): Map[String, String] = {
    val b = Map.newBuilder[String, String]
    var i = 0
    while (i < args.length) {
      val a = args(i)
      if (a.startsWith("--")) {
        val key = a.drop(2)
        if (i + 1 < args.length && !args(i + 1).startsWith("--")) {
          b += key -> args(i + 1); i += 2
        } else { b += key -> "true"; i += 1 }
      } else i += 1
    }
    b.result()
  }

  private def require(m: Map[String, String], key: String): String =
    m.getOrElse(key, throw new IllegalArgumentException(s"missing required --$key"))
}
