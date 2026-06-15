package maichess.insights.pgn

/** Classify a PGN `TimeControl` header ("base+increment" seconds, e.g. "600+0")
  * into a Lichess-style speed class. Estimated game duration = base + 40*increment
  * (Lichess's own heuristic). "-" / unparseable → "unknown".
  */
object TimeControl {

  def classify(tc: String): String = estimateSeconds(tc) match {
    case None                       => "unknown"
    case Some(s) if s < 29          => "ultrabullet"
    case Some(s) if s < 179         => "bullet"
    case Some(s) if s < 479         => "blitz"
    case Some(s) if s < 1499        => "rapid"
    case Some(_)                    => "classical"
  }

  /** base + 40*increment seconds, or None for correspondence / unparseable. */
  def estimateSeconds(tc: String): Option[Int] = {
    val parts = tc.split("\\+")
    try {
      parts.length match {
        case 1 => parts(0).toIntOption.map(b => b)
        case 2 =>
          for {
            b <- parts(0).toIntOption
            i <- parts(1).toIntOption
          } yield b + 40 * i
        case _ => None
      }
    } catch { case _: NumberFormatException => None }
  }
}
