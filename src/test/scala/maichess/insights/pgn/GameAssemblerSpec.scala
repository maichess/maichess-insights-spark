package maichess.insights.pgn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GameAssemblerSpec extends AnyFlatSpec with Matchers {

  private val parsed = PgnParser.parse(PgnParserFixtures.game).get

  "assemble" should "derive game-level fields from headers" in {
    val a = GameAssembler.assemble("lichess-2024-12", parsed, replayBoard = false)
    a.game.gameId shouldBe "abcd1234"
    a.game.result shouldBe "1-0"
    a.game.eco shouldBe "C20"
    a.game.yearMonth shouldBe "2024-12"
    a.game.timeControl shouldBe "blitz"
    a.game.ratingBand shouldBe "1600-1999"
    a.game.plyCount shouldBe 4
    a.game.corpusId shouldBe "lichess-2024-12"
  }

  it should "leave uci/fen empty when replay is off" in {
    val a = GameAssembler.assemble("c", parsed, replayBoard = false)
    a.plies.map(_.uci).toSet shouldBe Set("")
    a.plies.map(_.fenBefore).toSet shouldBe Set(None)
    a.plies.head.evalCp shouldBe Some(20)
  }

  it should "fill uci/fen when replay is on" in {
    val a = GameAssembler.assemble("c", parsed, replayBoard = true)
    a.plies.head.uci.toLowerCase shouldBe "e2e4"
    a.plies.head.fenBefore.isDefined shouldBe true
  }

  "gameId" should "use the Site path, then GameId, then a hash" in {
    GameAssembler.gameId(Map("Site" -> "https://lichess.org/xyz")) shouldBe "xyz"
    GameAssembler.gameId(Map("GameId" -> "g42")) shouldBe "g42"
    GameAssembler.gameId(Map("Other" -> "x")) should not be empty
  }

  "yearMonth" should "format and reject dates" in {
    GameAssembler.yearMonth(Map("UTCDate" -> "2024.12.05")) shouldBe "2024-12"
    GameAssembler.yearMonth(Map("Date" -> "2023.01.09")) shouldBe "2023-01"
    GameAssembler.yearMonth(Map("Date" -> "????.??.??")) shouldBe "unknown"
    GameAssembler.yearMonth(Map.empty) shouldBe "unknown"
  }

  "intHeader" should "parse present numeric headers only" in {
    GameAssembler.intHeader(Map("WhiteElo" -> "1700"), "WhiteElo") shouldBe Some(1700)
    GameAssembler.intHeader(Map("WhiteElo" -> "x"), "WhiteElo") shouldBe None
    GameAssembler.intHeader(Map.empty, "WhiteElo") shouldBe None
  }
}
