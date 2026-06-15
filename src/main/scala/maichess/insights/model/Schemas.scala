package maichess.insights.model

/** Parsed-Parquet schema (knowledge/domain/insights-statistics.md). These case
  * classes are the Spark Dataset row types written to `insights-parsed`.
  */

/** One game (the `games` table). */
final case class GameRow(
    corpusId: String,
    gameId: String,
    // "1-0" / "0-1" / "1/2-1/2" / "*"
    result: String,
    eco: String,
    openingName: String,
    whiteElo: Option[Int],
    blackElo: Option[Int],
    timeControl: String,
    termination: String,
    plyCount: Int,
    // "YYYY-MM"
    yearMonth: String,
    ratingBand: String,
)

/** One half-move (the `plies` table); one row per ply. */
final case class PlyRow(
    corpusId: String,
    gameId: String,
    ply: Int,
    // "white" / "black"
    side: String,
    san: String,
    uci: String,
    // Filled only when board replay runs (position/endgame analysis).
    fenBefore: Option[String],
    // From %eval, centipawns, mate normalized; None when unannotated.
    evalCp: Option[Int],
    // From %clk, milliseconds; None when unannotated.
    clockMs: Option[Int],
)
