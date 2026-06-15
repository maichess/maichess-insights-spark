package maichess.insights.pgn

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

import scala.jdk.CollectionConverters._
import scala.util.Try

/** Board replay over a game's SAN moves using chesslib, producing the UCI move and
  * the FEN *before* each ply (needed for position/endgame analysis). Pure and
  * deterministic — the expensive stage, run only when those metrics are requested.
  */
object BoardReplay {

  /** UCI of a move + the full FEN of the position it was played from. */
  final case class Replayed(uci: String, fenBefore: String)

  /** Replay SAN moves from the standard start. Best-effort: if chesslib cannot
    * parse the line (illegal/garbled SAN), returns the moves replayed up to the
    * failure rather than throwing, so one bad game never fails a partition.
    */
  def replay(sans: Seq[String]): Vector[Replayed] =
    if (sans.isEmpty) Vector.empty
    else {
      val moves = parseMoves(sans.mkString(" "))
      val board = new Board()
      val out = Vector.newBuilder[Replayed]
      val it = moves.iterator
      var continue = true
      while (it.hasNext && continue) {
        val mv = it.next()
        val before = board.getFen
        if (Try(board.doMove(mv)).getOrElse(false)) out += Replayed(mv.toString, before)
        else continue = false
      }
      out.result()
    }

  private def parseMoves(sanLine: String): Vector[com.github.bhlangonijr.chesslib.move.Move] =
    Try {
      val ml = new MoveList()
      ml.loadFromSan(sanLine)
      ml.asScala.toVector
    }.getOrElse(parseIncrementally(sanLine))

  /** Fallback when the whole line fails: parse move-by-move, stopping at the first
    * SAN chesslib rejects.
    */
  private def parseIncrementally(sanLine: String): Vector[com.github.bhlangonijr.chesslib.move.Move] = {
    val board = new Board()
    val out = Vector.newBuilder[com.github.bhlangonijr.chesslib.move.Move]
    val tokens = sanLine.split("\\s+").filter(_.nonEmpty)
    var continue = true
    for (san <- tokens if continue) {
      Try {
        val ml = new MoveList(board.getFen)
        ml.loadFromSan(san)
        ml.asScala.headOption
      }.toOption.flatten match {
        case Some(mv) if Try(board.doMove(mv)).getOrElse(false) => out += mv
        case _                                                  => continue = false
      }
    }
    out.result()
  }
}
