package maichess.insights.model

/** Rating-band bucketing (knowledge/domain/insights-statistics.md): bucket by the
  * average of the two Elos. Bands: `<1200`, `1200-1599`, `1600-1999`,
  * `2000-2399`, `2400+`. Unknown when neither Elo is present.
  */
object RatingBand {

  val Unknown = "unknown"

  /** Band for an explicit Elo value. */
  def forElo(elo: Int): String =
    if (elo < 1200) "<1200"
    else if (elo < 1600) "1200-1599"
    else if (elo < 2000) "1600-1999"
    else if (elo < 2400) "2000-2399"
    else "2400+"

  /** Band for a game: average the two Elos when both are present, else use the one
    * that is, else Unknown.
    */
  def forGame(whiteElo: Option[Int], blackElo: Option[Int]): String =
    (whiteElo, blackElo) match {
      case (Some(w), Some(b)) => forElo((w + b) / 2)
      case (Some(w), None)    => forElo(w)
      case (None, Some(b))    => forElo(b)
      case (None, None)       => Unknown
    }
}
