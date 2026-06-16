package maichess.insights.analysis

import maichess.insights.analysis.metrics.Results
import maichess.insights.model.{GameRow, PlyRow}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, SparkSession}

/** insights_summary: one headline-aggregate document per corpus — totals, date
  * range, rating distribution, draw rate, average length, termination mix, and
  * first-move popularity (knowledge/domain/insights-statistics.md).
  */
object Summary {

  def compute(games: Dataset[GameRow], plies: Dataset[PlyRow], corpusId: String)(implicit
      spark: SparkSession,
  ): SummaryStat = {
    import spark.implicits._

    val total = games.count()
    if (total == 0)
      return SummaryStat(corpusId, 0, "", "", 0.0, 0.0, Seq.empty, Seq.empty, Seq.empty)

    val agg = games
      .agg(
        sum(when($"result" === Results.Draw, 1).otherwise(0)).as("draws"),
        avg($"plyCount").as("avgPly"),
        min($"yearMonth").as("from"),
        max($"yearMonth").as("to"),
      )
      .first()
    val draws = agg.getAs[Long]("draws")
    val avgPly = agg.getAs[Double]("avgPly")
    val dateFrom = Option(agg.getAs[String]("from")).getOrElse("")
    val dateTo = Option(agg.getAs[String]("to")).getOrElse("")

    val ratingDist = counts(games.groupBy($"ratingBand").count(), "ratingBand")

    val firstMoves = counts(
      plies.filter($"ply" === 1).groupBy($"san").count().orderBy($"count".desc),
      "san",
    )

    val lastSan = plies
      .groupBy($"gameId")
      .agg(max(struct($"ply", $"san")).as("m"))
      .select($"gameId", $"m.san".as("lastSan"))
    val kindUdf = udf((term: String, last: String) =>
      Results.terminationKind(Option(term).getOrElse(""), Option(last)))
    val termMix = counts(
      games
        .join(lastSan, Seq("gameId"), "left")
        .withColumn("kind", kindUdf($"termination", $"lastSan"))
        .groupBy($"kind")
        .count(),
      "kind",
    )

    SummaryStat(
      corpusId = corpusId,
      totalGames = total,
      dateFrom = dateFrom,
      dateTo = dateTo,
      drawRate = draws.toDouble / total,
      avgPlyCount = avgPly,
      ratingDistribution = ratingDist,
      terminationMix = termMix,
      firstMoves = firstMoves,
    )
  }

  private def counts(df: Dataset[org.apache.spark.sql.Row], keyCol: String): Seq[Count] =
    df.collect().toSeq.map(r => Count(Option(r.getAs[String](keyCol)).getOrElse(""), r.getAs[Long]("count")))
}
