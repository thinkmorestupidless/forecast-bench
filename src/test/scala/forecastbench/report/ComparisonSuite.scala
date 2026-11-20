package forecastbench.report

class ComparisonSuite extends munit.FunSuite:

  private def paired(n: Int, d: Int => Double = _ => -0.02) =
    (1 to n).map(i => Paired(s"q$i", s"g${i % 7}", d(i)))

  // features/reports/report.feature: a comparison says how far it can be trusted by its count.
  List(12 -> "too few", 29 -> "too few", 30 -> "preliminary", 99 -> "preliminary", 100 -> "counted")
    .foreach { (n, standing) =>
      test(s"$n questions: $standing"):
        val c = Comparison.of("graph", "prose", paired(n), 1L, Scoring.One)
        assertEquals(c.count, n)
        assertEquals(c.standing, standing)
    }

  test("too few gives its count and no difference"):
    val c = Comparison.of("graph", "prose", paired(5), 1L, Scoring.One)
    assertEquals((c.count, c.meanDifference, c.interval), (5, None, None))

  test("it says which arm is better only when the interval leaves out nought"):
    assertEquals(
      Comparison.says("graph", "prose", Interval(-0.031, -0.004)),
      "graph has the lower Brier score"
    )
    assertEquals(
      Comparison.says("graph", "prose", Interval(0.004, 0.031)),
      "prose has the lower Brier score"
    )
    assertEquals(
      Comparison.says("graph", "prose", Interval(-0.020, 0.011)),
      "no difference is shown"
    )

  test("a question in several cohorts is counted once, by the mean of its differences"):
    val twice = paired(40) ++ Seq(Paired("q1", "g1", 0.10))
    val c     = Comparison.of("graph", "prose", twice, 1L, Scoring.One)
    assertEquals(c.count, 40)
    assertEqualsDouble(c.meanDifference.get, (39 * -0.02 + (-0.02 + 0.10) / 2) / 40, 1e-12)

  test("the same input and seed give the same comparison"):
    val p = paired(60, i => math.sin(i.toDouble) / 10)
    assertEquals(
      Comparison.of("a", "b", p, 9L, Scoring.One),
      Comparison.of("a", "b", p, 9L, Scoring.One)
    )
