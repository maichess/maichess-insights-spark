package maichess.insights.analysis

import maichess.insights.analysis.metrics.{FenNormalize, MaterialSignature, Results}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.udf

/** Spark UDF wrappers around the pure FEN/material helpers, shared by the endgame
  * and position jobs. The pure functions carry the coverage; these are one-liners.
  */
object FenUdfs {

  /** Normalized FEN (halfmove-clock + fullmove-number stripped). */
  val normalize: UserDefinedFunction = udf((fen: String) => FenNormalize.normalize(fen))

  /** Material signature, or `null` when the FEN is not an endgame (≤7 pieces). */
  val endgameSignature: UserDefinedFunction = udf((fen: String) =>
    if (MaterialSignature.isEndgame(fen)) MaterialSignature.signature(fen) else null)

  /** Whether White is the stronger side at this FEN. */
  val strongerIsWhite: UserDefinedFunction = udf((fen: String) => MaterialSignature.strongerIsWhite(fen))

  /** Stronger-side outcome ("win"/"draw"/"loss") of a game result. */
  val strongerOutcome: UserDefinedFunction =
    udf((result: String, strongerWhite: Boolean) => Results.strongerOutcome(result, strongerWhite))
}
