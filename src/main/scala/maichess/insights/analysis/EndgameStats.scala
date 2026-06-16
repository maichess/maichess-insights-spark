package maichess.insights.analysis

import maichess.insights.model.{GameRow, PlyRow}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, SparkSession}

/** insights_endgames: over replayed FENs with ≤7 pieces, classify by canonical
  * **material signature** (stronger side first, e.g. `KRPvKR`) and report frequency
  * + conversion tendency — how often the stronger side wins / draws / loses
  * (knowledge/domain/insights-statistics.md). Requires board replay (`fen_before`).
  *
  * Each (game, signature, stronger-side) reached is counted once so a long endgame
  * that lingers on one signature does not inflate its frequency; conversion uses the
  * game's final result.
  */
object EndgameStats {

  def compute(games: Dataset[GameRow], plies: Dataset[PlyRow], corpusId: String)(implicit
      spark: SparkSession,
  ): Dataset[EndgameStat] = {
    import spark.implicits._

    val reached = plies
      .filter($"fenBefore".isNotNull)
      .withColumn("sig", FenUdfs.endgameSignature($"fenBefore"))
      .filter($"sig".isNotNull)
      .withColumn("strongerWhite", FenUdfs.strongerIsWhite($"fenBefore"))
      .select($"gameId", $"sig", $"strongerWhite")
      .distinct()

    reached
      .join(games.select($"gameId", $"result"), Seq("gameId"))
      .withColumn("outcome", FenUdfs.strongerOutcome($"result", $"strongerWhite"))
      .groupBy($"sig")
      .agg(
        count(lit(1)).as("frequency"),
        sum(when($"outcome" === "win", 1).otherwise(0)).as("wins"),
        sum(when($"outcome" === "draw", 1).otherwise(0)).as("draws"),
        sum(when($"outcome" === "loss", 1).otherwise(0)).as("losses"),
      )
      .select(
        lit(corpusId).as("corpusId"),
        $"sig".as("materialSignature"),
        $"frequency",
        ($"wins" / $"frequency").as("strongerSideWinRate"),
        ($"draws" / $"frequency").as("drawRate"),
        ($"losses" / $"frequency").as("strongerSideLossRate"),
      )
      .orderBy($"frequency".desc)
      .as[EndgameStat]
  }
}
