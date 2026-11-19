package forecastbench.report

class CalibrationSuite extends munit.FunSuite:

  test("ten bands of equal width from nought to one"):
    val t = Calibration.table(Nil)
    assertEquals(t.size, 10)
    assertEquals(t.map(_.from), (0 until 10).map(_ / 10.0).toList)
    assertEquals(t.last.to, 1.0)

  test("each band gives its count, mean forecast, share of yes and its interval"):
    val forecasts = (1 to 20).map(i => (0.82, i <= 16)) ++ Seq((1.0, true), (0.05, false))
    val t         = Calibration.table(forecasts)
    val b         = t(8)
    assertEquals(b.count, 20)
    assertEqualsDouble(b.meanForecast.get, 0.82, 1e-12)
    assertEqualsDouble(b.shareYes.get, 0.8, 1e-12)
    assertEquals(b.interval, Wilson.interval(16, 20))
    assert(!b.tooFew)
    assertEquals(t(9).count, 1, "a forecast of one is in the last band")
    assertEquals(t(0).count, 1)

  test("a band with fewer than ten forecasts is too few"):
    val t = Calibration.table((1 to 7).map(_ => (0.33, true)))
    assert(t(3).tooFew)
    assertEquals(t(3).count, 7)
