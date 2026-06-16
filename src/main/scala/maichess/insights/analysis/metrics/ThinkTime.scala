package maichess.insights.analysis.metrics

/** Think-time from consecutive `%clk` deltas for the *same side* (the clock only
  * ticks on that side's move), accounting for the increment added after each move:
  * think = prevClock - thisClock + increment. Clamped at 0 (a clock that went up by
  * more than the increment — data noise — yields 0).
  */
object ThinkTime {

  def spentMs(prevClockMs: Int, thisClockMs: Int, incrementMs: Int): Int =
    Math.max(0, prevClockMs - thisClockMs + incrementMs)

  /** Increment in ms from a "base+inc" (seconds) TimeControl header. 0 when absent. */
  def incrementMs(timeControl: String): Int = {
    val parts = timeControl.split("\\+")
    if (parts.length == 2) parts(1).toIntOption.map(_ * 1000).getOrElse(0) else 0
  }
}
