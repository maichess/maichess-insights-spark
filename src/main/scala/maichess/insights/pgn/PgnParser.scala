package maichess.insights.pgn

/** One ply as parsed from movetext, before board replay. */
final case class ParsedPly(
    ply: Int,
    side: String,
    san: String,
    evalCp: Option[Int],
    clockMs: Option[Int],
)

/** A fully parsed PGN game: its tag headers and its plies. */
final case class ParsedGame(
    headers: Map[String, String],
    plies: Vector[ParsedPly],
)

/** Pure PGN parsing — headers, movetext tokenization, and `%eval` / `%clk`
  * annotation extraction. No board state, no Spark. Self-contained (the
  * .NET analysis-service parser is only a format reference, not imported).
  */
object PgnParser {

  // Mate scores are normalized to a large centipawn magnitude minus the distance,
  // so "#3" ranks above "#5" and both above any non-mating eval.
  val MateBase = 100000

  private val HeaderLine = """^\[(\w+)\s+"(.*)"\]\s*$""".r

  /** Split a multi-game PGN blob into individual game texts. Games are delimited
    * by a blank line *between* a movetext block and the next game's tags; a single
    * game's own tag/movetext blank line must not split it. We accumulate lines and
    * start a new game whenever a tag line follows a movetext line.
    */
  def splitGames(text: String): Vector[String] = {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)
    val games = Vector.newBuilder[String]
    val cur = new StringBuilder
    var sawMoves = false
    def flush(): Unit = {
      val g = cur.toString.trim
      if (g.nonEmpty) games += g
      cur.clear()
      sawMoves = false
    }
    for (line <- lines) {
      val isTag = line.startsWith("[")
      if (isTag && sawMoves) flush()
      if (line.trim.nonEmpty && !isTag) sawMoves = true
      cur.append(line).append('\n')
    }
    flush()
    games.result()
  }

  /** Parse one game text. Returns None if there are no tag headers at all. */
  def parse(gameText: String): Option[ParsedGame] = {
    val lines = gameText.replace("\r\n", "\n").replace("\r", "\n").split("\n").toVector
    val headers = parseHeaders(lines)
    if (headers.isEmpty) None
    else {
      val movetext = lines.dropWhile(l => l.startsWith("[") || l.trim.isEmpty).mkString(" ")
      Some(ParsedGame(headers, tokenize(movetext)))
    }
  }

  def parseHeaders(lines: Vector[String]): Map[String, String] =
    lines.collect { case HeaderLine(k, v) => k -> v }.toMap

  /** Tokenize movetext into plies. Strips move numbers, NAGs, result tokens, and
    * variation parens; attaches the `{...}` comment after each move to that move.
    */
  def tokenize(movetext: String): Vector[ParsedPly] = {
    val plies = Vector.newBuilder[ParsedPly]
    var ply = 0
    var i = 0
    val n = movetext.length
    var pendingSan: Option[String] = None

    def commit(san: String, comment: String): Unit = {
      ply += 1
      val side = if (ply % 2 == 1) "white" else "black"
      plies += ParsedPly(ply, side, san, extractEval(comment), extractClock(comment))
    }

    while (i < n) {
      val c = movetext.charAt(i)
      if (c == '{') {
        val end = movetext.indexOf('}', i)
        val close = if (end < 0) n else end
        val comment = movetext.substring(i + 1, close)
        pendingSan.foreach(commit(_, comment))
        pendingSan = None
        i = close + 1
      } else if (c.isWhitespace) {
        i += 1
      } else {
        val start = i
        while (i < n && !movetext.charAt(i).isWhitespace && movetext.charAt(i) != '{') i += 1
        val tok = movetext.substring(start, i)
        if (isMoveToken(tok)) {
          pendingSan.foreach(commit(_, ""))
          pendingSan = Some(stripDecorations(tok))
        }
      }
    }
    pendingSan.foreach(commit(_, ""))
    plies.result()
  }

  /** True for a real SAN move token (not a move number, result, NAG, or comment). */
  private def isMoveToken(tok: String): Boolean = {
    if (tok.isEmpty) false
    else if (tok == "1-0" || tok == "0-1" || tok == "1/2-1/2" || tok == "*") false
    else if (tok.startsWith("$")) false
    else if (tok.startsWith("(") || tok.startsWith(")")) false
    else if (tok.matches("""\d+\.+""")) false // "12." or "12..."
    else if (tok.forall(ch => ch.isDigit || ch == '.')) false
    else {
      // Move numbers can be glued to a move, e.g. "1.e4"; require a letter after stripping.
      val s = stripMoveNumberPrefix(tok)
      s.headOption.exists(h => (h >= 'a' && h <= 'h') || "KQRBNO".contains(h))
    }
  }

  private def stripMoveNumberPrefix(tok: String): String =
    tok.dropWhile(ch => ch.isDigit || ch == '.')

  /** Strip a glued move-number prefix and trailing check/mate/NAG decorations. */
  def stripDecorations(tok: String): String =
    stripMoveNumberPrefix(tok).takeWhile(ch => ch != '!' && ch != '?')

  /** Extract centipawns from a `[%eval ...]` clause. Mate `#N` → normalized. */
  def extractEval(comment: String): Option[Int] = {
    val EvalMate = """\[%eval\s+#(-?)(\d+)\]""".r
    val EvalCp = """\[%eval\s+(-?\d+(?:\.\d+)?)\]""".r
    EvalMate.findFirstMatchIn(comment) match {
      case Some(m) =>
        val dist = m.group(2).toInt
        val mag = MateBase - dist
        Some(if (m.group(1) == "-") -mag else mag)
      case None =>
        EvalCp.findFirstMatchIn(comment).map(m => Math.round(m.group(1).toDouble * 100).toInt)
    }
  }

  /** Extract milliseconds from a `[%clk H:MM:SS(.f)]` clause. */
  def extractClock(comment: String): Option[Int] = {
    val Clk = """\[%clk\s+([0-9:.]+)\]""".r
    Clk.findFirstMatchIn(comment).flatMap(m => clockToMs(m.group(1)))
  }

  /** Parse "H:MM:SS", "M:SS", or with fractional seconds, into ms. */
  def clockToMs(s: String): Option[Int] = {
    val parts = s.split(":")
    if (parts.isEmpty || parts.length > 3 || parts.exists(_.isEmpty)) None
    else
      try {
        val nums = parts.map(_.toDouble)
        val secs = parts.length match {
          case 1 => nums(0)
          case 2 => nums(0) * 60 + nums(1)
          case 3 => nums(0) * 3600 + nums(1) * 60 + nums(2)
        }
        Some(Math.round(secs * 1000).toInt)
      } catch { case _: NumberFormatException => None }
  }
}
