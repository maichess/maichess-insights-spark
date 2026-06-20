package maichess.insights.tournament

import maichess.insights.model.{GameRow, PlyRow, RatingBand}
import maichess.insights.pgn.{BoardReplay, GameAssembler, TimeControl}

/** Assemble parsed-Parquet rows ([[GameRow]] + [[PlyRow]]) from a tournament
  * analytics export. Bots carry no Elo (rating band is unknown) and the export has
  * no ECO/opening or per-move eval/clock, so those fields are empty; the value is
  * in the replayed positions (`fen_before`) + results, which drive the position,
  * endgame and summary analyses. Board replay always runs — a single tournament is
  * tiny next to a Lichess monthly dump, so the per-game replay cost is negligible
  * and SAN (needed for first-move / checkmate stats) is only available from it. Pure.
  */
object TournamentAssembler {

  def assemble(corpusId: String, exportDoc: TournamentExport): Vector[GameAssembler.Assembled] =
    exportDoc.games.map(g => assembleGame(corpusId, exportDoc, g))

  def assembleGame(
      corpusId: String,
      exportDoc: TournamentExport,
      g: TournamentExportGame,
  ): GameAssembler.Assembled = {
    val ucis = g.moves.split("\\s+").iterator.filter(_.nonEmpty).toVector
    val replay = BoardReplay.replayUci(ucis)
    val plies = replay.zipWithIndex.map { case (r, idx) =>
      PlyRow(
        corpusId = corpusId,
        gameId = g.gameId,
        ply = idx + 1,
        side = if (idx % 2 == 0) "white" else "black",
        san = r.san,
        uci = r.uci,
        fenBefore = Some(r.fenBefore),
        evalCp = None,
        clockMs = None,
      )
    }

    val game = GameRow(
      corpusId = corpusId,
      gameId = g.gameId,
      result = result(g.winner),
      eco = "",
      openingName = "",
      whiteElo = None,
      blackElo = None,
      timeControl = TimeControl.classify(s"${exportDoc.clock.limit}+${exportDoc.clock.increment}"),
      termination = terminationHeader(g.terminationReason),
      plyCount = g.totalPly,
      yearMonth = yearMonth(g, exportDoc),
      ratingBand = RatingBand.forGame(None, None),
    )

    GameAssembler.Assembled(game, plies)
  }

  /** Tournament winner → PGN-style result token (what the analysis jobs match on). */
  def result(winner: Option[String]): String = winner match {
    case Some("white") => "1-0"
    case Some("black") => "0-1"
    case Some("draw")  => "1/2-1/2"
    case _             => "*"
  }

  /** Map the tournament termination reason onto a PGN `Termination`-header value so
    * the shared `Results.terminationKind` classifies it: timeouts → "timeout",
    * checkmate/resign (`Normal`, no mate-SAN ⇒ resign; with one ⇒ mate) → resign/mate,
    * draws/stalemate/unfinished → "" ⇒ "other" (they are captured by the draw rate).
    */
  def terminationHeader(reason: String): String = reason match {
    case "timeout"               => "Time forfeit"
    case "checkmate" | "resigned" => "Normal"
    case _                        => ""
  }

  /** "YYYY-MM" from the game's end/start, falling back to the export's
    * finished/exported timestamps, else "unknown".
    */
  def yearMonth(g: TournamentExportGame, exportDoc: TournamentExport): String =
    Seq(g.endedAt, g.startedAt, exportDoc.finishedAt, Some(exportDoc.exportedAt)).flatten
      .flatMap(monthOf)
      .headOption
      .getOrElse("unknown")

  // ISO-8601 timestamp ("2025-06-17T10:05:03Z") → "2025-06", or None if not parseable.
  private def monthOf(iso: String): Option[String] =
    if (iso.length >= 7) {
      val ym = iso.substring(0, 7)
      val parts = ym.split("-")
      if (parts.length == 2 && parts(0).length == 4 && parts(0).forall(_.isDigit) && parts(1).forall(_.isDigit))
        Some(ym)
      else None
    } else None
}
