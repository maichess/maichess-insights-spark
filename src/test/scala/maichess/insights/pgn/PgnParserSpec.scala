package maichess.insights.pgn

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PgnParserSpec extends AnyFlatSpec with Matchers {

  val game: String =
    """[Event "Rated Blitz game"]
      |[Site "https://lichess.org/abcd1234"]
      |[White "alice"]
      |[Black "bob"]
      |[Result "1-0"]
      |[UTCDate "2024.12.05"]
      |[WhiteElo "1700"]
      |[BlackElo "1650"]
      |[ECO "C20"]
      |[Opening "King's Pawn Game"]
      |[TimeControl "300+0"]
      |[Termination "Normal"]
      |
      |1. e4 { [%eval 0.2] [%clk 0:05:00] } 1... e5 { [%eval 0.15] [%clk 0:05:00] }
      |2. Nf3 { [%eval 0.3] [%clk 0:04:58] } 2... Nc6 { [%eval 0.25] [%clk 0:04:57] } 1-0
      |""".stripMargin

  "parse" should "extract headers" in {
    val g = PgnParser.parse(game).get
    g.headers("Result") shouldBe "1-0"
    g.headers("WhiteElo") shouldBe "1700"
    g.headers("ECO") shouldBe "C20"
    g.headers should have size 12
  }

  it should "return None when there are no headers" in {
    PgnParser.parse("1. e4 e5 *") shouldBe None
  }

  it should "tokenize plies with alternating sides" in {
    val g = PgnParser.parse(game).get
    g.plies.map(_.san) shouldBe Vector("e4", "e5", "Nf3", "Nc6")
    g.plies.map(_.side) shouldBe Vector("white", "black", "white", "black")
    g.plies.map(_.ply) shouldBe Vector(1, 2, 3, 4)
  }

  it should "attach eval and clock to the right ply" in {
    val g = PgnParser.parse(game).get
    g.plies.head.evalCp shouldBe Some(20)
    g.plies.head.clockMs shouldBe Some(300000)
    g.plies(2).evalCp shouldBe Some(30)
    g.plies(2).clockMs shouldBe Some(298000)
  }

  "splitGames" should "separate two concatenated games" in {
    val blob = game + "\n\n" + game.replace("abcd1234", "wxyz9999")
    val games = PgnParser.splitGames(blob)
    games should have size 2
    games(1) should include("wxyz9999")
  }

  it should "return a single game when there is one" in {
    PgnParser.splitGames(game) should have size 1
  }

  it should "ignore trailing blank lines" in {
    PgnParser.splitGames(game + "\n\n\n") should have size 1
  }

  "tokenize" should "skip move numbers, NAGs, results and variations" in {
    val mt = "1. e4 $1 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 1/2-1/2"
    val plies = PgnParser.tokenize(mt)
    plies.map(_.san) should contain allOf ("e4", "e5", "Nf3", "Nc6")
    plies.map(_.san) should not contain "1/2-1/2"
  }

  it should "handle move numbers glued to moves" in {
    PgnParser.tokenize("1.e4 e5 2.Nf3").map(_.san) shouldBe Vector("e4", "e5", "Nf3")
  }

  it should "strip check and annotation glyphs" in {
    PgnParser.tokenize("1. Qh5 Nc6 2. Bc4 Nf6?? 3. Qxf7#").map(_.san) shouldBe
      Vector("Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#")
  }

  "extractEval" should "read centipawn and mate scores" in {
    PgnParser.extractEval("[%eval 0.34]") shouldBe Some(34)
    PgnParser.extractEval("[%eval -1.27]") shouldBe Some(-127)
    PgnParser.extractEval("[%eval #3]") shouldBe Some(PgnParser.MateBase - 3)
    PgnParser.extractEval("[%eval #-2]") shouldBe Some(-(PgnParser.MateBase - 2))
    PgnParser.extractEval("no eval here") shouldBe None
  }

  "extractClock" should "read a clock clause" in {
    PgnParser.extractClock("[%clk 0:01:30]") shouldBe Some(90000)
    PgnParser.extractClock("nothing") shouldBe None
  }

  "clockToMs" should "parse H:MM:SS, M:SS, fractions, and reject bad input" in {
    PgnParser.clockToMs("0:05:00") shouldBe Some(300000)
    PgnParser.clockToMs("1:30") shouldBe Some(90000)
    PgnParser.clockToMs("0:00:01.5") shouldBe Some(1500)
    PgnParser.clockToMs("90") shouldBe Some(90000)
    PgnParser.clockToMs("1:2:3:4") shouldBe None
    PgnParser.clockToMs("a:b") shouldBe None
    PgnParser.clockToMs(":30") shouldBe None
  }

  "stripDecorations" should "drop move-number prefix and glyphs" in {
    PgnParser.stripDecorations("12.Nf3+") shouldBe "Nf3+"
    PgnParser.stripDecorations("e4!?") shouldBe "e4"
  }
}
