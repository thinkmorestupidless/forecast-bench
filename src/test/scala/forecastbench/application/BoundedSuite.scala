package forecastbench.application

import java.util.concurrent.atomic.AtomicInteger

class BoundedSuite extends munit.FunSuite:

  test("every item is worked, the results in the items' order, never more than the bound at once"):
    val now  = AtomicInteger(0)
    val most = AtomicInteger(0)
    val out = Bounded.map(1 to 200, 8) { i =>
      most.accumulateAndGet(now.incrementAndGet(), math.max): Unit
      Thread.sleep(2)
      now.decrementAndGet(): Unit
      i * 2
    }
    assertEquals(out, (1 to 200).map(_ * 2).toList)
    assert(most.get <= 8, s"${most.get} at once")
    assert(most.get > 1, "nothing ran at once")

  test("a failure is thrown as it was thrown"):
    val thrown = intercept[IllegalStateException](
      Bounded.map(1 to 50, 4)(i => if i == 7 then throw IllegalStateException("seven") else i)
    )
    assertEquals(thrown.getMessage, "seven")
