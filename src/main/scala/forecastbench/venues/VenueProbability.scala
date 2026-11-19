package forecastbench.venues

import java.time.Instant

/**
 * The venue's probability (research R9): the middle of the best bid and ask when both exist and are
 * no more than 0.10 apart, else the last traded price, else none.
 */
object VenueProbability:

  val WidestSpread = 0.10

  def read(
      bid: Option[Double],
      ask: Option[Double],
      last: Option[Double],
      at: Instant
  ): Option[VenueReading] =
    def valid(p: Double) = p >= 0.0 && p <= 1.0
    val b                = bid.filter(valid)
    val a                = ask.filter(valid)
    val l                = last.filter(valid)
    val mid = for
      x <- b
      y <- a
      if y >= x && y - x <= WidestSpread + 1e-9
    yield (x + y) / 2
    mid
      .map(p => VenueReading(round(p), "mid", b, a, l, at))
      .orElse(l.map(p => VenueReading(p, "last", b, a, l, at)))

  private def round(p: Double): Double =
    BigDecimal(p).setScale(6, BigDecimal.RoundingMode.HALF_UP).toDouble
