package maichess.insights.analysis.metrics

/** Normalized FEN for position-frequency (knowledge/domain/insights-statistics.md):
  * the FEN with the halfmove-clock and fullmove-number fields stripped, so
  * transpositions and clock differences collapse while piece placement, side to
  * move, castling, and en passant are kept.
  */
object FenNormalize {

  def normalize(fen: String): String =
    fen.trim.split("\\s+").take(4).mkString(" ")

  /** Ply index at which a position is considered out of the opening book, used to
    * optionally drop early-book positions. Plies (half-moves), so ~12 = move 6.
    */
  val DefaultBookPlies = 12

  def isInBook(ply: Int, bookPlies: Int = DefaultBookPlies): Boolean = ply <= bookPlies
}
