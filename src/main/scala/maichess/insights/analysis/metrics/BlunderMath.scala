package maichess.insights.analysis.metrics

/** Eval-swing / blunder metrics (knowledge/domain/insights-statistics.md). `eval_cp`
  * is always from White's perspective; "centipawn loss" is how much the move
  * actually played worsened the position *for the side that moved*.
  */
object BlunderMath {

  /** Default blunder threshold: a move losing ≥ 300cp is a blunder. */
  val BlunderThresholdCp = 300

  /** Centipawn loss of a move.
    * @param evalBefore eval (White's view) of the position the move was played from
    * @param evalAfter  eval (White's view) after the move
    * @param white      true if White made the move
    * Loss is clamped at 0 (an improving move is not a "loss").
    */
  def cpLoss(evalBefore: Int, evalAfter: Int, white: Boolean): Int = {
    val raw = if (white) evalBefore - evalAfter else evalAfter - evalBefore
    Math.max(0, raw)
  }

  def isBlunder(loss: Int, threshold: Int = BlunderThresholdCp): Boolean =
    loss >= threshold
}
