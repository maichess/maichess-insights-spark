package maichess.insights.tournament

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TournamentExportParserSpec extends AnyFlatSpec with Matchers {

  private val full =
    """
    {
      "schemaVersion": "1.0",
      "tournamentId": "t7kXq2",
      "format": "swiss",
      "clock": { "limit": 300, "increment": 3 },
      "rated": true,
      "nbRounds": 5,
      "startedAt": "2025-06-17T10:00:00Z",
      "finishedAt": "2025-06-17T12:30:00Z",
      "exportedAt": "2025-06-17T12:31:00Z",
      "standings": [ { "botId": "bot_abc", "rank": 1 } ],
      "games": [
        {
          "gameId": "g1",
          "tournamentId": "t7kXq2",
          "round": 1,
          "whiteBotId": "bot_abc",
          "whiteBotName": "Engine1",
          "blackBotId": "bot_xyz",
          "blackBotName": "Engine2",
          "winner": "white",
          "winnerBotId": "bot_abc",
          "terminationReason": "checkmate",
          "totalPly": 5,
          "moves": "e2e4 f7f6 d2d4 g7g5 d1h5",
          "startedAt": "2025-06-17T10:05:00Z",
          "endedAt": "2025-06-17T10:05:03Z"
        },
        {
          "gameId": "g2",
          "round": 1,
          "whiteBotId": "bot_xyz",
          "whiteBotName": "Engine2",
          "blackBotId": "bot_abc",
          "blackBotName": "Engine1",
          "winner": null,
          "terminationReason": "draw",
          "totalPly": 0,
          "moves": ""
        }
      ]
    }
    """

  "parse" should "read the top-level export fields" in {
    val e = TournamentExportParser.parse(full)
    e.schemaVersion shouldBe "1.0"
    e.tournamentId shouldBe "t7kXq2"
    e.format shouldBe "swiss"
    e.clock shouldBe TournamentClock(300, 3)
    e.rated shouldBe true
    e.nbRounds shouldBe 5
    e.startedAt shouldBe Some("2025-06-17T10:00:00Z")
    e.finishedAt shouldBe Some("2025-06-17T12:30:00Z")
    e.exportedAt shouldBe "2025-06-17T12:31:00Z"
    e.games should have size 2
  }

  it should "read per-game fields including winner and timestamps" in {
    val g = TournamentExportParser.parse(full).games.head
    g.gameId shouldBe "g1"
    g.round shouldBe 1
    g.whiteBotName shouldBe "Engine1"
    g.blackBotName shouldBe "Engine2"
    g.winner shouldBe Some("white")
    g.terminationReason shouldBe "checkmate"
    g.totalPly shouldBe 5
    g.moves shouldBe "e2e4 f7f6 d2d4 g7g5 d1h5"
    g.startedAt shouldBe Some("2025-06-17T10:05:00Z")
    g.endedAt shouldBe Some("2025-06-17T10:05:03Z")
  }

  it should "treat a null winner and absent timestamps/moves as None/empty" in {
    val g = TournamentExportParser.parse(full).games(1)
    g.winner shouldBe None
    g.startedAt shouldBe None
    g.endedAt shouldBe None
    g.moves shouldBe ""
  }

  it should "default a missing games array to empty" in {
    val json =
      """{ "schemaVersion": "1.0", "tournamentId": "t", "format": "swiss",
         "clock": {"limit":60,"increment":0}, "rated": false, "nbRounds": 1,
         "exportedAt": "2025-06-17T12:31:00Z" }"""
    TournamentExportParser.parse(json).games shouldBe empty
  }

  it should "throw when a required field is missing" in {
    val json =
      """{ "schemaVersion": "1.0", "format": "swiss",
         "clock": {"limit":60,"increment":0}, "rated": false, "nbRounds": 1,
         "exportedAt": "2025-06-17T12:31:00Z", "games": [] }"""
    an[IllegalArgumentException] should be thrownBy TournamentExportParser.parse(json)
  }

  it should "throw when the document is not a JSON object" in {
    an[IllegalArgumentException] should be thrownBy TournamentExportParser.parse("[1,2,3]")
  }
}
