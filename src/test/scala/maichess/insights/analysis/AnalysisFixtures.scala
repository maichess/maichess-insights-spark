package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}

/** Small typed fixtures for the analysis Spark suites. FENs are written directly so
  * the jobs can be exercised without running board replay.
  */
object AnalysisFixtures {

  val corpus = "test-corpus"

  def game(
      id: String,
      result: String,
      eco: String = "C20",
      opening: String = "King's Pawn Game",
      whiteElo: Option[Int] = Some(1700),
      blackElo: Option[Int] = Some(1650),
      timeControl: String = "blitz",
      termination: String = "Normal",
      plyCount: Int = 4,
      yearMonth: String = "2024-12",
      ratingBand: String = "1600-1999",
  ): GameRow =
    GameRow(corpus, id, result, eco, opening, whiteElo, blackElo, timeControl, termination,
      plyCount, yearMonth, ratingBand)

  def ply(
      gameId: String,
      ply: Int,
      side: String,
      san: String,
      fenBefore: Option[String] = None,
      evalCp: Option[Int] = None,
      clockMs: Option[Int] = None,
      uci: String = "",
  ): PlyRow =
    PlyRow(corpus, gameId, ply, side, san, uci, fenBefore, evalCp, clockMs)

  // King+pawn vs king endgame (signature KPvK, White stronger).
  val kpkFen = "8/8/8/8/8/4k3/4P3/4K3 w - - 0 1"
  // King+rook vs king+rook (signature KRvKR, tie → White stronger).
  val krkrFen = "3rk3/8/8/8/8/8/3R4/4K3 w - - 0 1"
}
