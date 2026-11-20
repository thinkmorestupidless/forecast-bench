package forecastbench.report

import forecastbench.domain.OutcomeValue
import forecastbench.domain.OutcomeValue.*

class ScoringSuite extends munit.FunSuite:

  private def r4(x: Double) = BigDecimal(x).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

  // features/scoring/score.feature, the first outline.
  List(
    (0.60, Yes, 0.1600, 0.5108),
    (0.75, Yes, 0.0625, 0.2877),
    (0.90, Yes, 0.0100, 0.1054),
    (0.60, No, 0.3600, 0.9163),
    (0.90, No, 0.8100, 2.3026),
    (0.50, Yes, 0.2500, 0.6931)
  ).foreach { (p, o, brier, log) =>
    test(s"$p on ${o.word}: Brier $brier, log $log"):
      assertEquals(
        Scoring.score(Scoring.One, p, o).map((b, l) => (r4(b), r4(l))),
        Some((brier, log))
      )
  }

  // The second outline: the log score at the ends, at the bound 0.01.
  List((1.00, No, 4.6052), (0.00, Yes, 4.6052), (1.00, Yes, 0.0101)).foreach { (p, o, log) =>
    test(s"$p on ${o.word} at the bound: log $log"):
      assertEquals(r4(Scoring.log(Scoring.One, p, o)), log)
  }

  test("void has no score"):
    assertEquals(Scoring.score(Scoring.One, 0.5, OutcomeValue.Void), None)

  test("an arm's forecast is the mean of its repetitions, with their spread"):
    val f = Scoring.armForecast(List(0.60, 0.70, 0.80)).get
    assertEquals(r4(f.mean), 0.7)
    assertEquals(r4(Scoring.brier(f.mean, Yes)), 0.09)
    assertEquals(r4(f.spread), 0.1)
    assertEquals(Scoring.armForecast(Nil), None)
