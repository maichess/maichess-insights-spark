package maichess.insights.ingest

import maichess.insights.filter.{CorpusFilter, Filters}
import maichess.insights.model.{GameRow, PlyRow}
import maichess.insights.pgn.{GameAssembler, PgnParser}
import org.apache.spark.sql.{Dataset, SparkSession}

/** The Spark transformation at the heart of ingestion: a `Dataset` of raw
  * per-game PGN texts → the `games` + `plies` row Datasets, parsed, assembled, and
  * filtered. Pure with respect to Spark I/O (no read/write here) so it is testable
  * against a `local[*]` SparkSession over fixture PGNs.
  */
object Ingestion {

  def transform(
      rawChunks: Dataset[String],
      corpusId: String,
      filter: CorpusFilter,
      replayBoard: Boolean,
  )(implicit spark: SparkSession): (Dataset[GameRow], Dataset[PlyRow]) = {
    import spark.implicits._

    // Each record is a raw PGN chunk, which may hold one game (the LF fast path where
    // IngestJob's "\n\n[Event " record-delimiter already split per game) OR many games
    // (e.g. a CRLF upload whose "\r\n\r\n[Event " separators the delimiter can't match,
    // so the whole file arrives as a single record). splitGames re-splits robustly
    // (CRLF/CR-normalized) in both cases, so a single-game chunk stays one game.
    val kept = rawChunks
      .flatMap(chunk => PgnParser.splitGames(chunk))
      .flatMap(text => PgnParser.parse(text).map(pg => GameAssembler.assemble(corpusId, pg, replayBoard)))
      .filter(a => keep(a.game, filter))
      .persist()

    (kept.map(_.game), kept.flatMap(_.plies))
  }

  /** Game-level predicate applied in the transform: band + time-control + sampling.
    * Date slicing is applied at source/month granularity by the job, not per row.
    */
  def keep(game: GameRow, filter: CorpusFilter): Boolean =
    Filters.bandOk(game, filter) && Filters.timeOk(game, filter) && Filters.sampleOk(game, filter)
}
