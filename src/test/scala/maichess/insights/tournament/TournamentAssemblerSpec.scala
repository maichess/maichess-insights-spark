package maichess.insights.tournament

import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TournamentAssemblerSpec extends AnyFlatSpec with Matchers {

  private def export(games: Vector[TournamentExportGame], clock: TournamentClock = TournamentClock(300, 3)) =
    TournamentExport(
      schemaVersion = "1.0",
      tournamentId = "t1",
      format = "swiss",
      clock = clock,
      rated = true,
      nbRounds = 1,
      startedAt = None,
      finishedAt = None,
      exportedAt = "2025-06-17T12:31:00Z",
      games = games,
    )

  private def game(
      id: String = "g1",
      winner: Option[String] = Some("white"),
      termination: String = "checkmate",
      totalPly: Int = 5,
      moves: String = "e2e4 f7f6 d2d4 g7g5 d1h5",
      startedAt: Option[String] = None,
      endedAt: Option[String] = None,
  ) = TournamentExportGame(
    gameId = id,
    round = 1,
    whiteBotId = "wb",
    whiteBotName = "White",
    blackBotId = "bb",
    blackBotName = "Black",
    winner = winner,
    terminationReason = termination,
    totalPly = totalPly,
    moves = moves,
    startedAt = startedAt,
    endedAt = endedAt,
  )

  "result" should "map winners to PGN result tokens" in {
    TournamentAssembler.result(Some("white")) shouldBe "1-0"
    TournamentAssembler.result(Some("black")) shouldBe "0-1"
    TournamentAssembler.result(Some("draw")) shouldBe "1/2-1/2"
    TournamentAssembler.result(None) shouldBe "*"
    TournamentAssembler.result(Some("weird")) shouldBe "*"
  }

  "terminationHeader" should "map reasons so terminationKind classifies them" in {
    TournamentAssembler.terminationHeader("timeout") shouldBe "Time forfeit"
    TournamentAssembler.terminationHeader("checkmate") shouldBe "Normal"
    TournamentAssembler.terminationHeader("resigned") shouldBe "Normal"
    TournamentAssembler.terminationHeader("draw") shouldBe ""
    TournamentAssembler.terminationHeader("stalemate") shouldBe ""
  }

  "yearMonth" should "prefer the game end date, then fall through to the export" in {
    TournamentAssembler.yearMonth(game(endedAt = Some("2025-03-01T00:00:00Z")), export(Vector.empty)) shouldBe "2025-03"
    TournamentAssembler.yearMonth(game(startedAt = Some("2025-02-01T00:00:00Z")), export(Vector.empty)) shouldBe "2025-02"
    // No game dates → falls back to the export's exportedAt (2025-06).
    TournamentAssembler.yearMonth(game(), export(Vector.empty)) shouldBe "2025-06"
  }

  it should "fall back to the export finishedAt before exportedAt" in {
    val e = export(Vector.empty).copy(finishedAt = Some("2024-12-31T23:00:00Z"))
    TournamentAssembler.yearMonth(game(), e) shouldBe "2024-12"
  }

  it should "be unknown when no date is parseable" in {
    // "not-a-date" is long enough but malformed; "2025" is too short to hold a month.
    val e = export(Vector.empty).copy(exportedAt = "not-a-date")
    TournamentAssembler.yearMonth(game(endedAt = Some("garbage"), startedAt = Some("2025")), e) shouldBe "unknown"
  }

  "assembleGame" should "build a GameRow with derived fields" in {
    val a = TournamentAssembler.assembleGame("corpus1", export(Vector.empty), game())
    a.game.corpusId shouldBe "corpus1"
    a.game.gameId shouldBe "g1"
    a.game.result shouldBe "1-0"
    a.game.eco shouldBe ""
    a.game.whiteElo shouldBe None
    a.game.blackElo shouldBe None
    a.game.ratingBand shouldBe "unknown"
    a.game.timeControl shouldBe "blitz" // 300 + 40*3 = 420s
    a.game.termination shouldBe "Normal"
    a.game.plyCount shouldBe 5
    a.game.yearMonth shouldBe "2025-06"
  }

  it should "replay UCI into alternating-side plies with SAN + fen-before" in {
    val a = TournamentAssembler.assembleGame("c", export(Vector.empty), game())
    a.plies should have size 5
    a.plies.map(_.side) shouldBe Vector("white", "black", "white", "black", "white")
    a.plies.map(_.ply) shouldBe Vector(1, 2, 3, 4, 5)
    a.plies.head.uci shouldBe "e2e4"
    a.plies.head.san shouldBe "e4"
    a.plies.head.fenBefore.value should startWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w")
    a.plies.last.san should endWith("#") // d1h5 is f7 mate-ish; scholar-style
    all(a.plies.map(_.evalCp)) shouldBe None
    all(a.plies.map(_.clockMs)) shouldBe None
  }

  it should "truncate plies at the first illegal move but still produce the game" in {
    // A custom-start game whose moves are illegal from the standard position.
    val g = game(id = "custom", moves = "e7e5 d2d4", totalPly = 2)
    val a = TournamentAssembler.assembleGame("c", export(Vector.empty), g)
    a.game.gameId shouldBe "custom"
    a.game.plyCount shouldBe 2 // game-level count is authoritative from the server
    a.plies shouldBe empty // nothing legally replayable from the standard start
  }

  "assemble" should "map every game in the export" in {
    val e = export(Vector(game(id = "a"), game(id = "b", winner = Some("draw"), termination = "draw")))
    val out = TournamentAssembler.assemble("c", e)
    out.map(_.game.gameId) shouldBe Vector("a", "b")
    out(1).game.result shouldBe "1/2-1/2"
  }
}
