package maichess.insights.pgn

import maichess.insights.model.{GameRow, PlyRow, RatingBand}

/** Assemble the parsed-Parquet rows from a [[ParsedGame]]: one [[GameRow]] and its
  * [[PlyRow]]s, deriving game-level fields from headers and (optionally) enriching
  * plies with UCI + `fen_before` via board replay. Pure.
  */
object GameAssembler {

  final case class Assembled(game: GameRow, plies: Vector[PlyRow])

  /** @param replayBoard run board replay to fill `uci` + `fen_before` (the
    *                    expensive stage; only needed for position/endgame stats).
    */
  def assemble(corpusId: String, parsed: ParsedGame, replayBoard: Boolean): Assembled = {
    val h = parsed.headers
    val whiteElo = intHeader(h, "WhiteElo")
    val blackElo = intHeader(h, "BlackElo")
    val game = GameRow(
      corpusId = corpusId,
      gameId = gameId(h),
      result = h.getOrElse("Result", "*"),
      eco = h.getOrElse("ECO", ""),
      openingName = h.getOrElse("Opening", ""),
      whiteElo = whiteElo,
      blackElo = blackElo,
      timeControl = TimeControl.classify(h.getOrElse("TimeControl", "")),
      termination = h.getOrElse("Termination", ""),
      plyCount = parsed.plies.size,
      yearMonth = yearMonth(h),
      ratingBand = RatingBand.forGame(whiteElo, blackElo),
    )

    val replay = if (replayBoard) BoardReplay.replay(parsed.plies.map(_.san)) else Vector.empty
    val plies = parsed.plies.zipWithIndex.map { case (p, idx) =>
      val r = replay.lift(idx)
      PlyRow(
        corpusId = corpusId,
        gameId = game.gameId,
        ply = p.ply,
        side = p.side,
        san = p.san,
        uci = r.map(_.uci).getOrElse(""),
        fenBefore = r.map(_.fenBefore),
        evalCp = p.evalCp,
        clockMs = p.clockMs,
      )
    }
    Assembled(game, plies)
  }

  /** Lichess games carry `[Site "https://lichess.org/<id>"]`; use the last path
    * segment as the id, else the `GameId`/`Site` header, else a content hash.
    */
  def gameId(h: Map[String, String]): String =
    h.get("Site").map(_.split("/").last).filter(_.nonEmpty)
      .orElse(h.get("GameId"))
      .getOrElse(Integer.toHexString(h.hashCode()))

  def intHeader(h: Map[String, String], key: String): Option[Int] =
    h.get(key).flatMap(_.trim.toIntOption)

  /** "YYYY.MM.DD" (UTCDate preferred) → "YYYY-MM"; "????" → "unknown". */
  def yearMonth(h: Map[String, String]): String =
    h.get("UTCDate").orElse(h.get("Date")) match {
      case Some(d) =>
        val parts = d.split("[.\\-/]")
        if (parts.length >= 2 && parts(0).forall(_.isDigit) && parts(1).forall(_.isDigit) && parts(0).length == 4)
          s"${parts(0)}-${parts(1)}"
        else "unknown"
      case None => "unknown"
    }
}
