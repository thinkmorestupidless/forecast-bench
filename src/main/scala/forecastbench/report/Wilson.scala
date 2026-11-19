package forecastbench.report

/** The Wilson score interval of a share (research R11), at 95% unless told otherwise. */
object Wilson:

  val Z95 = 1.959963984540054

  def interval(successes: Int, n: Int, z: Double = Z95): Option[Interval] =
    Option.when(n > 0) {
      val p      = successes.toDouble / n
      val z2     = z * z
      val centre = (p + z2 / (2 * n)) / (1 + z2 / n)
      val half   = z / (1 + z2 / n) * math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n))
      Interval(math.max(0.0, centre - half), math.min(1.0, centre + half))
    }
