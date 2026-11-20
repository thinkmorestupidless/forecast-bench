package forecastbench.report

class WilsonSuite extends munit.FunSuite:

  private def r4(x: Double) = BigDecimal(x).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

  test("worked values"):
    // 8 of 10: 0.4902 to 0.9433; 0 of 10: 0 to 0.2775; 50 of 100: 0.4038 to 0.5962.
    assertEquals(Wilson.interval(8, 10).map(i => (r4(i.low), r4(i.high))), Some((0.4902, 0.9433)))
    assertEquals(Wilson.interval(0, 10).map(i => (r4(i.low), r4(i.high))), Some((0.0, 0.2775)))
    assertEquals(Wilson.interval(50, 100).map(i => (r4(i.low), r4(i.high))), Some((0.4038, 0.5962)))

  test("no forecasts give no interval"):
    assertEquals(Wilson.interval(0, 0), None)
