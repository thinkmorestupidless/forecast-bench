package forecastbench.report

/** One band of a calibration table. */
final case class Band(
    from: Double,
    to: Double,
    count: Int,
    meanForecast: Option[Double],
    shareYes: Option[Double],
    interval: Option[Interval],
    tooFew: Boolean
)

/**
 * Ten bands of equal width from nought to one; a forecast of exactly one is in the last. Each band
 * gives its count, the mean of its forecasts, the share that resolved yes and the Wilson interval
 * of that share; under ten forecasts it is marked too few.
 */
object Calibration:

  val Bands       = 10
  val TooFewUnder = 10

  def band(p: Double): Int = math.min(Bands - 1, math.max(0, (p * Bands).toInt))

  def table(forecasts: Seq[(Double, Boolean)]): List[Band] =
    val by = forecasts.groupBy((p, _) => band(p))
    (0 until Bands).toList.map { i =>
      val in    = by.getOrElse(i, Nil)
      val count = in.size
      val yes   = in.count(_._2)
      Band(
        (BigDecimal(i) / Bands).toDouble,
        (BigDecimal(i + 1) / Bands).toDouble,
        count,
        Option.when(count > 0)(in.map(_._1).sum / count),
        Option.when(count > 0)(yes.toDouble / count),
        Wilson.interval(yes, count),
        count < TooFewUnder
      )
    }
