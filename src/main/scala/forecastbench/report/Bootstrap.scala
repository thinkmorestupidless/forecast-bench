package forecastbench.report

import scala.util.Random

/** An interval: its low and high ends. */
final case class Interval(low: Double, high: Double):
  def excludesZero: Boolean = low > 0.0 || high < 0.0

/**
 * The percentile bootstrap of a mean (research R11): resample the groups with replacement, take the
 * mean of every value in the groups drawn, and read the interval off the sorted means. Seeded, so
 * the same values and seed give the same interval.
 */
object Bootstrap:

  def mean(
      values: Seq[(String, Double)],
      seed: Long,
      resamples: Int = 10000,
      confidence: Double = 0.95
  ): Option[Interval] =
    if values.isEmpty then None
    else
      val groups = values.groupMap(_._1)(_._2).toVector.sortBy(_._1).map(_._2.toVector)
      val random = Random(seed)
      val means = Array.fill(resamples) {
        var sum   = 0.0
        var count = 0
        var i     = 0
        while i < groups.size do
          val g = groups(random.nextInt(groups.size))
          sum += g.sum
          count += g.size
          i += 1
        sum / count
      }
      java.util.Arrays.sort(means)
      val tail = (1.0 - confidence) / 2.0
      Some(Interval(at(means, tail), at(means, 1.0 - tail)))

  /** The percentile by the nearest rank. */
  private def at(sorted: Array[Double], q: Double): Double =
    sorted(math.min(sorted.length - 1, math.max(0, math.ceil(q * sorted.length).toInt - 1)))

  /** A seed from what makes the figure: the method, the cohorts and the arms. */
  def seed(parts: String*): Long =
    val digest = java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(parts.mkString("\u0000").getBytes("UTF-8"))
    java.nio.ByteBuffer.wrap(digest).getLong
