package maichess.insights.analysis.metrics

/** Endgame material-signature classification (knowledge/domain/insights-statistics.md):
  * a position is an endgame when total piece count ≤ 7; the signature is the multiset
  * of non-king pieces per side written canonically (Q,R,B,N,P), stronger side first
  * (e.g. `KRPvKR`, `KQvKR`, `KBNvK`).
  */
object MaterialSignature {

  val EndgamePieceLimit = 7

  // Canonical ordering and relative weights (king implicit, listed first).
  private val order = "QRBNP"
  private val weight = Map('Q' -> 9, 'R' -> 5, 'B' -> 3, 'N' -> 3, 'P' -> 1)

  /** The piece-placement field's piece letters (both colors). */
  private def placement(fen: String): String =
    fen.trim.split("\\s+").headOption.getOrElse("").filter(_.isLetter)

  def pieceCount(fen: String): Int = placement(fen).length

  def isEndgame(fen: String): Boolean = {
    val n = pieceCount(fen)
    n > 0 && n <= EndgamePieceLimit
  }

  /** Per-side "K" + canonically-ordered non-king pieces, e.g. "KRP". */
  private def sidePieces(letters: String, white: Boolean): String = {
    val mine = letters.filter(c => if (white) c.isUpper else c.isLower).map(_.toUpper)
    val nonKing = mine.filter(_ != 'K')
    val sorted = nonKing.sortBy(c => order.indexOf(c.toInt))
    "K" + sorted.mkString
  }

  private def strength(side: String): Int =
    side.iterator.map(c => weight.getOrElse(c, 0)).sum

  /** Whether White is the stronger side (ties → White), for conversion tendency. */
  def strongerIsWhite(fen: String): Boolean = {
    val letters = placement(fen)
    strength(sidePieces(letters, white = true)) >= strength(sidePieces(letters, white = false))
  }

  /** Canonical signature `<stronger>v<weaker>`. Ties (equal strength) order the
    * lexicographically smaller side string first, so it is deterministic.
    */
  def signature(fen: String): String = {
    val letters = placement(fen)
    val white = sidePieces(letters, white = true)
    val black = sidePieces(letters, white = false)
    val (a, b) =
      if (strength(white) > strength(black)) (white, black)
      else if (strength(black) > strength(white)) (black, white)
      else if (white <= black) (white, black)
      else (black, white)
    s"${a}v$b"
  }
}
