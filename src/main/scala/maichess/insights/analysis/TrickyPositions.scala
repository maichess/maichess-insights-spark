package maichess.insights.analysis

import maichess.insights.analysis.metrics.BlunderMath
import maichess.insights.model.PlyRow
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, SparkSession}

/** insights_tricky: per normalized position, the intersection of two annotation
  * signals (no engine) — average **centipawn loss** + **blunder probability** of the
  * move actually played, and **think time** from `%clk` deltas
  * (knowledge/domain/insights-statistics.md). "Trickiest" = reached with enough
  * support and both high cp-loss and high think time.
  *
  * `eval_cp` is always from White's view; the eval *before* a move is the previous
  * ply's `eval_cp`, the eval *after* is this ply's. Think time is the same side's
  * consecutive clock delta. Increment is not preserved in the parsed schema (the
  * `time_control` header is bucketed at ingestion), so the delta omits it — a
  * uniform understatement that does not affect the relative ranking.
  *
  * @param minSupport only positions with at least this many played moves are emitted.
  * @param blunderThresholdCp a move losing ≥ this many cp counts as a blunder.
  */
object TrickyPositions {

  def compute(
      plies: Dataset[PlyRow],
      corpusId: String,
      minSupport: Int = 1,
      blunderThresholdCp: Int = BlunderMath.BlunderThresholdCp,
  )(implicit spark: SparkSession): Dataset[TrickyStat] = {
    import spark.implicits._

    val byPly = Window.partitionBy($"gameId").orderBy($"ply")
    val bySide = Window.partitionBy($"gameId", $"side").orderBy($"ply")

    val moves = plies
      .filter($"fenBefore".isNotNull && $"evalCp".isNotNull)
      .withColumn("prevEval", lag($"evalCp", 1).over(byPly))
      .withColumn("prevClock", lag($"clockMs", 1).over(bySide))
      .filter($"prevEval".isNotNull)
      .withColumn("nfen", FenUdfs.normalize($"fenBefore"))
      // cp-loss for the side that moved (eval is White's view), clamped at 0.
      .withColumn(
        "cpLoss",
        greatest(
          lit(0),
          when($"side" === "white", $"prevEval" - $"evalCp").otherwise($"evalCp" - $"prevEval"),
        ),
      )
      .withColumn("isBlunder", when($"cpLoss" >= blunderThresholdCp, 1).otherwise(0))
      .withColumn(
        "thinkMs",
        when($"prevClock".isNull, lit(null).cast("int"))
          .otherwise(greatest(lit(0), $"prevClock" - $"clockMs")),
      )

    moves
      .groupBy($"nfen")
      .agg(
        count(lit(1)).as("support"),
        avg($"cpLoss").as("avgCentipawnLoss"),
        avg($"isBlunder").as("blunderProbability"),
        avg($"thinkMs").as("avgThinkTimeMs"),
      )
      .filter($"support" >= minSupport)
      .select(
        lit(corpusId).as("corpusId"),
        $"nfen".as("normalizedFen"),
        $"support",
        $"avgCentipawnLoss",
        $"blunderProbability",
        coalesce($"avgThinkTimeMs", lit(0.0)).as("avgThinkTimeMs"),
      )
      .orderBy($"avgCentipawnLoss".desc, $"avgThinkTimeMs".desc)
      .as[TrickyStat]
  }
}
