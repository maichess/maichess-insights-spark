package maichess.insights.analysis

/** Row shapes materialized into the `insights_*` Mongo collections. Field meanings
  * mirror the insights proto / REST contract (knowledge/domain/insights-statistics.md);
  * the query API (task 06) maps these documents to the wire shape. Every row carries
  * `corpusId` so a collection can hold multiple corpora.
  */

/** insights_openings */
final case class OpeningStat(
    corpusId: String,
    eco: String,
    openingName: String,
    gameCount: Long,
    whiteWinRate: Double,
    blackWinRate: Double,
    drawRate: Double,
)

/** insights_endgames */
final case class EndgameStat(
    corpusId: String,
    materialSignature: String,
    frequency: Long,
    strongerSideWinRate: Double,
    drawRate: Double,
    strongerSideLossRate: Double,
)

/** insights_positions */
final case class PositionStat(
    corpusId: String,
    normalizedFen: String,
    reachCount: Long,
    whiteWinRate: Double,
    blackWinRate: Double,
    drawRate: Double,
)

/** insights_tricky */
final case class TrickyStat(
    corpusId: String,
    normalizedFen: String,
    support: Long,
    avgCentipawnLoss: Double,
    blunderProbability: Double,
    avgThinkTimeMs: Double,
)

/** insights_summary (one document per corpus) */
final case class SummaryStat(
    corpusId: String,
    totalGames: Long,
    dateFrom: String,
    dateTo: String,
    drawRate: Double,
    avgPlyCount: Double,
    ratingDistribution: Seq[Count],
    terminationMix: Seq[Count],
    firstMoves: Seq[Count],
)

final case class Count(key: String, gameCount: Long)
