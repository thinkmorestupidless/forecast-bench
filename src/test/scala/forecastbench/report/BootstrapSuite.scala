package forecastbench.report

import scala.util.Random

class BootstrapSuite extends munit.FunSuite:

  private val values = (1 to 60).map(i => (s"g${i % 20}", math.sin(i.toDouble)))

  test("the same seed gives the same interval"):
    assertEquals(Bootstrap.mean(values, 7L), Bootstrap.mean(values, 7L))

  test("a constant sample gives a point"):
    val i = Bootstrap.mean((1 to 40).map(n => (s"g$n", 0.25)), 1L).get
    assertEqualsDouble(i.low, 0.25, 1e-12)
    assertEqualsDouble(i.high, 0.25, 1e-12)

  test("no values give no interval"):
    assertEquals(Bootstrap.mean(Nil, 1L), None)

  test("on a known distribution the interval covers the true mean about 95 times in a hundred"):
    val random = Random(42)
    val trials = 200
    val covered = (1 to trials).count { t =>
      val sample = (1 to 100).map(i => (s"q$i", random.nextGaussian() * 0.1 + 0.02))
      Bootstrap.mean(sample, t.toLong, resamples = 2000).get match
        case Interval(lo, hi) => lo <= 0.02 && 0.02 <= hi
    }
    val share = covered.toDouble / trials
    assert(share > 0.89 && share < 0.99, s"covered $share")

  test("groups are resampled whole"):
    // Two groups, one of nine values at 1 and one of one value at 0: a draw is all one or the
    // other, or one of each, so every mean is 0, 1 or 0.9.
    val g = (1 to 9).map(_ => ("a", 1.0)) :+ ("b", 0.0)
    val i = Bootstrap.mean(g, 3L).get
    assert(Set(0.0, 0.9, 1.0).contains(i.low) && Set(0.0, 0.9, 1.0).contains(i.high), i.toString)
