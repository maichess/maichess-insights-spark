package maichess.insights.pgn

/** Small real-Lichess-format fixtures shared across specs. */
object PgnParserFixtures {

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

  /** A game with a blunder: black's eval swings from +0.1 to +4.0 (White's view). */
  val blunderGame: String =
    """[Event "Rated Blitz game"]
      |[Site "https://lichess.org/blnd0001"]
      |[Result "1-0"]
      |[UTCDate "2024.12.10"]
      |[WhiteElo "1500"]
      |[BlackElo "1500"]
      |[ECO "C50"]
      |[Opening "Italian Game"]
      |[TimeControl "300+0"]
      |[Termination "Normal"]
      |
      |1. e4 { [%eval 0.1] [%clk 0:05:00] } 1... e5 { [%eval 0.1] [%clk 0:05:00] }
      |2. Nf3 { [%eval 0.2] [%clk 0:04:55] } 2... Qf6 { [%eval 4.0] [%clk 0:04:40] } 1-0
      |""".stripMargin
}
