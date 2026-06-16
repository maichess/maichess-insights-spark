package maichess.insights.analysis.metrics

/** Pure helpers over PGN result + termination, shared by the analysis jobs. */
object Results {

  val WhiteWin = "1-0"
  val BlackWin = "0-1"
  val Draw = "1/2-1/2"

  def isWhiteWin(result: String): Boolean = result == WhiteWin
  def isBlackWin(result: String): Boolean = result == BlackWin
  def isDraw(result: String): Boolean = result == Draw

  /** Score for the *stronger side* given a result and whether the stronger side is
    * White: 1.0 win / 0.5 draw / 0.0 loss. Used for endgame conversion tendency.
    */
  def strongerScore(result: String, strongerIsWhite: Boolean): Double =
    if (isDraw(result)) 0.5
    else if (isWhiteWin(result)) (if (strongerIsWhite) 1.0 else 0.0)
    else if (isBlackWin(result)) (if (strongerIsWhite) 0.0 else 1.0)
    else 0.5 // unfinished "*" counted as neutral

  /** Outcome from the *stronger side's* perspective: "win" / "draw" / "loss".
    * Unfinished "*" games count as "draw" (neutral). Drives the endgame
    * conversion-tendency breakdown (win / draw / loss rates).
    */
  def strongerOutcome(result: String, strongerIsWhite: Boolean): String =
    if (isWhiteWin(result)) (if (strongerIsWhite) "win" else "loss")
    else if (isBlackWin(result)) (if (strongerIsWhite) "loss" else "win")
    else "draw"

  /** Classify a game's termination into mate / resign / timeout / other.
    * @param termination the PGN `Termination` header
    * @param lastSan     the final ply's SAN (mate ends with '#')
    */
  def terminationKind(termination: String, lastSan: Option[String]): String = {
    val t = termination.toLowerCase
    if (lastSan.exists(_.endsWith("#"))) "mate"
    else if (t.contains("time")) "timeout"
    else if (t == "normal") "resign"
    else "other"
  }
}
