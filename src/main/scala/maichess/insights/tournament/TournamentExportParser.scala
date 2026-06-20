package maichess.insights.tournament

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

/** Pure parse of a tournament analytics-export JSON document into
  * [[TournamentExport]] using Jackson's tree model (no Scala-module reflection, so
  * it is robust and unit-testable without a SparkSession). Missing required fields
  * throw [[IllegalArgumentException]]; optional fields degrade to None / defaults.
  */
object TournamentExportParser {

  private val mapper = new ObjectMapper()

  def parse(json: String): TournamentExport = {
    val root = mapper.readTree(json)
    if (root == null || !root.isObject)
      throw new IllegalArgumentException("analytics export is not a JSON object")

    val clockNode = required(root, "clock")
    TournamentExport(
      schemaVersion = str(root, "schemaVersion"),
      tournamentId = str(root, "tournamentId"),
      format = str(root, "format"),
      clock = TournamentClock(int(clockNode, "limit"), int(clockNode, "increment")),
      rated = bool(root, "rated"),
      nbRounds = int(root, "nbRounds"),
      startedAt = optStr(root, "startedAt"),
      finishedAt = optStr(root, "finishedAt"),
      exportedAt = str(root, "exportedAt"),
      games = arr(root, "games").map(game),
    )
  }

  private def game(n: JsonNode): TournamentExportGame =
    TournamentExportGame(
      gameId = str(n, "gameId"),
      round = int(n, "round"),
      whiteBotId = str(n, "whiteBotId"),
      whiteBotName = str(n, "whiteBotName"),
      blackBotId = str(n, "blackBotId"),
      blackBotName = str(n, "blackBotName"),
      winner = optStr(n, "winner"),
      terminationReason = str(n, "terminationReason"),
      totalPly = int(n, "totalPly"),
      moves = optStr(n, "moves").getOrElse(""),
      startedAt = optStr(n, "startedAt"),
      endedAt = optStr(n, "endedAt"),
    )

  private def required(n: JsonNode, field: String): JsonNode = {
    val v = n.get(field)
    if (v == null || v.isNull) throw new IllegalArgumentException(s"missing required field: $field")
    v
  }

  private def str(n: JsonNode, field: String): String = required(n, field).asText()
  private def int(n: JsonNode, field: String): Int = required(n, field).asInt()
  private def bool(n: JsonNode, field: String): Boolean = required(n, field).asBoolean()

  // None when the field is absent, JSON null, or an empty/whitespace string.
  private def optStr(n: JsonNode, field: String): Option[String] = {
    val v = n.get(field)
    if (v == null || v.isNull) None else Option(v.asText()).map(_.trim).filter(_.nonEmpty)
  }

  private def arr(n: JsonNode, field: String): Vector[JsonNode] = {
    val v = n.get(field)
    if (v == null || !v.isArray) Vector.empty
    else {
      val b = Vector.newBuilder[JsonNode]
      v.elements().forEachRemaining(b += _)
      b.result()
    }
  }
}
