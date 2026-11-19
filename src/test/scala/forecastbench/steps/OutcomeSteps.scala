package forecastbench.steps

import forecastbench.domain.OutcomeValue

/**
 * What the outcome, score and report features share: reading outcomes and the outcome read.
 *
 * GherkinSuite matches a step by its text whatever its keyword, so "the outcome of "rates" is
 * "yes"" is one step whether it is written as a Given or a Then. Before the scenario's action it
 * arranges the outcome; after it, it checks it. Every When step marks the action as taken.
 */
trait OutcomeSteps extends CohortSetup:

  protected def acted(): Unit = world.held("acted") = "yes"

  protected def hasActed: Boolean = world.held.contains("acted")

  protected def readOutcomes(): String = ok(post("/outcomes/read", "{}"))

  protected def outcomeOf(name: String): ujson.Value =
    val o = json(ok(get(s"/questions/${questionId(name)}/outcome")))
    if o.isNull then fail(s"'$name' has no outcome")
    o

  protected def resolve(name: String, result: String, disputed: Boolean = false): Unit =
    val value = OutcomeValue.parse(result.toLowerCase).getOrElse(fail(s"no outcome '$result'"))
    fixture.kalshi.resolve(world.venueId(name), value, fixture.clock.now, disputed)

  private def outcomeIs(name: String, value: String, disputed: Boolean): Unit =
    if hasActed then
      assertEquals(outcomeOf(name)("value").str, value)
      assertEquals(outcomeOf(name)("disputed").bool, disputed)
    else
      resolve(name, value, disputed)
      readOutcomes(): Unit

  Given("the outcome of {string} is {string}") { (name: String, value: String) =>
    outcomeIs(name, value, disputed = false)
  }

  Given("the outcome of {string} is {string} and is disputed") { (name: String, value: String) =>
    outcomeIs(name, value, disputed = true)
  }

  Given("the outcome of {string} is {string} and is not disputed") { (name: String, value: String) =>
    outcomeIs(name, value, disputed = false)
  }
