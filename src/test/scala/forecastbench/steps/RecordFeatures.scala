package forecastbench.steps

import forecastbench.domain.{Ending, OutcomeValue}
import forecastbench.support.ScriptedModel

import java.time.Duration

class RecordFeatures extends FeatureSuite("features/runs/record.feature") with ModelSteps:

  override protected def pending = Set(
    // T055, T066: the evidence and graph arms.
    "a run is recorded with everything its answer came from",
    "a run records its cost, its tokens and its latency",
    "a run can be made again from its record without touching it"
  )

  private def runPath = s"/runs/${only.id}"

  Given("the model answers with a time of its own") { () =>
    fixture.model.replies(
      key,
      """{"probability": 0.5, "abstain": null, "reasons": [], "rationale": "x", "recordedAt": "1999-01-01T00:00:00Z"}"""
    )
  }

  Then("the run is dated the time the bench recorded it") { () =>
    assertEquals(only.recordedAt, fixture.clock.now)
  }

  Given("a run of the arm {string} for {string} with the forecast {string}") {
    (words: String, name: String, p: String) =>
      fixture.model.replies(key, ScriptedModel.forecast(p.toDouble))
      ok(forecastBy(words, name)): Unit
  }

  Given("a run of the arm {string} for {string}") { (words: String, name: String) =>
    ok(forecastBy(words, name)): Unit
  }

  When("anyone tries to change the run's forecast") { () =>
    val change = """{"ending":{"type":"Forecast","probability":0.9}}"""
    world.last = put(runPath, change)
    world.held("patch") = send("PATCH", runPath, Some(change))._1.toString
  }

  Then("the change is refused") { () =>
    assertEquals(world.last._1, 405, world.last._2)
    assertEquals(world.held("patch"), "405")
  }

  Then("the run's forecast is {string}") { (p: String) =>
    assertEquals(json(ok(get(runPath)))("run")("ending")("probability").num, p.toDouble)
  }

  When("anyone tries to remove the run") { () => world.last = send("DELETE", runPath) }

  Then("the removal is refused") { () =>
    assertEquals(world.last._1, 405, world.last._2)
    ok(get(runPath)): Unit
  }

  Given("the question {string} has closed") { (name: String) =>
    // Frozen first, as a cohort is; then the venue closes it early, and the bench reads that.
    frozenWith("model alone")
    val closes = fixture.clock.now.plus(Duration.ofHours(1))
    fixture.kalshi.update(world.venueId(name))(_.copy(closesAt = closes))
    fixture.kalshi.only = Some(world.venueId(name))
    ok(post("/venues/kalshi/import", """{"standing":"prospective","onePerEvent":false}""")): Unit
    fixture.kalshi.only = None
    fixture.clock.advance(Duration.ofHours(2))
  }

  Given("the question {string} has an outcome") { (name: String) =>
    frozenWith("model alone")
    fixture.kalshi.resolve(world.venueId(name), OutcomeValue.Yes, fixture.clock.now)
  }

  Then("no run is recorded") { () =>
    assertEquals(recorded(november), Nil)
  }

  Then("the experimenter is told the question has closed") { () =>
    val reasons = readCohort(november)("notMade").arr.map(_("reason").str).toList
    assertEquals(reasons, List("the question has closed"))
  }

  Given("the cohort {string} has been forecast") { (name: String) =>
    assertEquals(world.cohort(name), november)
    ok(forecastBy("model alone", "rates")): Unit
  }

  When("the experimenter reads the history of the cohort") { () =>
    world.last = get(s"/cohorts/$november/history")
  }

  Then(
    "they read when it was frozen, when each pack was gathered and when each run was recorded, in order"
  ) { () =>
    val lines = json(ok(world.last)).arr.toList.map(l => (l("at").str, l("what").str))
    assertEquals(lines.map(_._1), lines.map(_._1).sorted, "the history is not in order")
    val frozen = lines.indexWhere(_._2 == "frozen")
    val run    = lines.indexWhere(_._2.startsWith(s"run ${only.id} recorded"))
    assert(frozen >= 0 && run > frozen, lines.mkString("\n"))
    assert(only.ending.isInstanceOf[Ending.Forecast])
  }
