package maichess.insights.tournament

/** Scala model of the tournament server's analytics-export document
  * (`GET /api/tournament/{id}/analytics-export`, `AnalyticsExport` in its
  * `openapi.yaml`). Only the fields the insights pipeline needs to assemble the
  * parsed-Parquet rows are kept; the raw document may carry more (e.g. standings,
  * which the analysis jobs recompute from the games). Pure data — no Spark.
  */
final case class TournamentClock(limit: Int, increment: Int)

/** One game from the export. `moves` is the space-separated UCI move sequence; the
  * board is replayed from it to derive SAN + `fenBefore` (the export carries no
  * per-move eval or clock, so those stay empty for tournament corpora).
  */
final case class TournamentExportGame(
    gameId: String,
    round: Int,
    whiteBotId: String,
    whiteBotName: String,
    blackBotId: String,
    blackBotName: String,
    // "white" | "black" | "draw"; None for an unfinished/aborted game.
    winner: Option[String],
    // checkmate | stalemate | draw | resigned | timeout | pending | ongoing
    terminationReason: String,
    totalPly: Int,
    moves: String,
    startedAt: Option[String],
    endedAt: Option[String],
)

final case class TournamentExport(
    schemaVersion: String,
    tournamentId: String,
    format: String,
    clock: TournamentClock,
    rated: Boolean,
    nbRounds: Int,
    startedAt: Option[String],
    finishedAt: Option[String],
    exportedAt: String,
    games: Vector[TournamentExportGame],
)

object TournamentExport {

  /** The only export schema version this pipeline understands. Consumers must gate
    * on it before trusting the payload (per the tournament server contract).
    */
  val SupportedSchemaVersion = "1.0"
}
