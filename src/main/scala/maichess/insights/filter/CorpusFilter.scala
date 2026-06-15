package maichess.insights.filter

import maichess.insights.model.GameRow

/** The slice that produces a corpus (knowledge/domain/insights-statistics.md).
  * Empty `Option`s mean "all"; `sampleRate` outside (0,1) means "keep all". Dates
  * are inclusive "YYYY-MM-DD" (lexicographically comparable).
  */
final case class CorpusFilter(
    ratingBand: Option[String] = None,
    timeControl: Option[String] = None,
    dateFrom: Option[String] = None,
    dateTo: Option[String] = None,
    sampleRate: Double = 0.0,
)

object Filters {

  /** Keep a game iff it matches the band/time-control/date predicates and survives
    * deterministic sampling. `gameDate` is the game's "YYYY-MM-DD" (UTCDate) when
    * known; a date filter excludes games with an unknown date.
    */
  def keep(game: GameRow, gameDate: Option[String], filter: CorpusFilter): Boolean =
    bandOk(game, filter) && timeOk(game, filter) && dateOk(gameDate, filter) && sampleOk(game, filter)

  def bandOk(game: GameRow, f: CorpusFilter): Boolean =
    f.ratingBand.forall(_ == game.ratingBand)

  def timeOk(game: GameRow, f: CorpusFilter): Boolean =
    f.timeControl.forall(_ == game.timeControl)

  def dateOk(gameDate: Option[String], f: CorpusFilter): Boolean = {
    val fromOk = f.dateFrom.forall(d => gameDate.exists(_ >= d))
    val toOk = f.dateTo.forall(d => gameDate.exists(_ <= d))
    fromOk && toOk
  }

  /** Deterministic per-game sampling: a stable hash of the game id mapped to
    * [0,1) is compared against the rate, so the same corpus is reproducible.
    */
  def sampleOk(game: GameRow, f: CorpusFilter): Boolean =
    if (f.sampleRate <= 0.0 || f.sampleRate >= 1.0) true
    else bucket(game.gameId) < f.sampleRate

  def bucket(gameId: String): Double =
    Math.floorMod(gameId.hashCode.toLong, 100000L) / 100000.0
}
