package maichess.insights.analysis

import maichess.insights.analysis.metrics.Results
import maichess.insights.model.{GameRow, PlyRow}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, SparkSession}

/** insights_positions: most-reached **normalized FENs** (halfmove-clock +
  * fullmove-number stripped, so transpositions collapse), with the White/Black/draw
  * outcome of the games that reached them (knowledge/domain/insights-statistics.md).
  * Early opening-book plies can be excluded to surface middlegame convergence.
  * Requires board replay (`fen_before`).
  *
  * @param bookPlies positions reached at ply ≤ `bookPlies` are dropped (0 keeps all).
  * @param minReach  only positions reached by at least this many games are emitted.
  */
object PositionFrequency {

  def compute(
      games: Dataset[GameRow],
      plies: Dataset[PlyRow],
      corpusId: String,
      bookPlies: Int = 0,
      minReach: Int = 1,
  )(implicit spark: SparkSession): Dataset[PositionStat] = {
    import spark.implicits._

    val reached = plies
      .filter($"fenBefore".isNotNull && $"ply" > bookPlies)
      .withColumn("nfen", FenUdfs.normalize($"fenBefore"))
      .select($"gameId", $"nfen")
      .distinct()

    reached
      .join(games.select($"gameId", $"result"), Seq("gameId"))
      .groupBy($"nfen")
      .agg(
        count(lit(1)).as("reachCount"),
        sum(when($"result" === Results.WhiteWin, 1).otherwise(0)).as("w"),
        sum(when($"result" === Results.BlackWin, 1).otherwise(0)).as("b"),
        sum(when($"result" === Results.Draw, 1).otherwise(0)).as("d"),
      )
      .filter($"reachCount" >= minReach)
      .select(
        lit(corpusId).as("corpusId"),
        $"nfen".as("normalizedFen"),
        $"reachCount",
        ($"w" / $"reachCount").as("whiteWinRate"),
        ($"b" / $"reachCount").as("blackWinRate"),
        ($"d" / $"reachCount").as("drawRate"),
      )
      .orderBy($"reachCount".desc)
      .as[PositionStat]
  }
}
