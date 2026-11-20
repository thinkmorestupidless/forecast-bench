package forecastbench.steps

import forecastbench.application.QuestionForecast
import forecastbench.domain.Ending

class FailoverFeatures
    extends FeatureSuite("src/test/resources/features/runs/failover/failover.feature")
    with AppraisalSteps:

  Given("a search for {string} throws") { (name: String) =>
    fixture.search.throws(statement(name))
  }

  /** Round 0, at the freeze, gathers and does not read the venue: only the round's read throws. */
  Given("reading {string} at its venue throws when the round reads it") { (name: String) =>
    fixture.kalshi.throwsFor(world.venueId(name))
  }

  Then("the arm {string} forecast {string}") { (words: String, _: String) =>
    val run = recorded(november).find(_.arm == arm(words)).getOrElse(fail("no run of the arm"))
    assert(run.ending.isInstanceOf[Ending.Forecast], run.ending.toString)
  }

  Then("the run of the arm {string} is not made, the pack failed when its step did") {
    (words: String) =>
      assert(!recorded(november).exists(_.arm == arm(words)), "a run was recorded")
      val reasons = readCohort(november)("notMade").arr.toList
        .filter(_("arm").str == arm(words))
        .map(_("reason").str)
      assertEquals(reasons, List(s"the pack was not gathered: ${QuestionForecast.StepFailed}"))
  }

  Then("the run of the arm {string} is failed, the venue unreadable") { (words: String) =>
    val run = recorded(november).find(_.arm == arm(words)).getOrElse(fail("no run of the arm"))
    assertEquals(run.ending, Ending.Failed(QuestionForecast.VenueUnreadable))
  }
