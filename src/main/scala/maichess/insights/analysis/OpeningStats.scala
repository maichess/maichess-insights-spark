package maichess.insights.analysis

import maichess.insights.analysis.metrics.Results
import maichess.insights.model.GameRow
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, SparkSession}

/** insights_openings: group by ECO + opening name, reporting game count and
  * White/Black/draw rates. No board replay (ECO/opening are in the header). Color /
  * rating-band / time-control splits and the monthly trend are query-side / future
  * refinements (knowledge/domain/insights-statistics.md).
  */
object OpeningStats {

  def compute(games: Dataset[GameRow], corpusId: String)(implicit spark: SparkSession): Dataset[OpeningStat] = {
    import spark.implicits._
    games
      .filter($"eco" =!= "")
      .groupBy($"eco", $"openingName")
      .agg(
        count(lit(1)).as("gameCount"),
        sum(when($"result" === Results.WhiteWin, 1).otherwise(0)).as("w"),
        sum(when($"result" === Results.BlackWin, 1).otherwise(0)).as("b"),
        sum(when($"result" === Results.Draw, 1).otherwise(0)).as("d"),
      )
      .select(
        lit(corpusId).as("corpusId"),
        $"eco",
        $"openingName",
        $"gameCount",
        ($"w" / $"gameCount").as("whiteWinRate"),
        ($"b" / $"gameCount").as("blackWinRate"),
        ($"d" / $"gameCount").as("drawRate"),
      )
      .orderBy($"gameCount".desc)
      .as[OpeningStat]
  }
}
