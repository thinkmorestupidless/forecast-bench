package forecastbench.steps

import forecastbench.domain.{Ending, OutcomeValue}
import forecastbench.support.ScriptedModel

import java.time.Duration

class RecordFeatures extends FeatureSuite("features/runs/record.feature") with PackSteps:

  When("the experimenter reads the run") { () => world.last = get(runPath) }

  Then(
    "the run names its cohort, its question, its arm, its repetition, its model, the version of its instructions and its evidence pack"
  ) { () =>
    val run = json(ok(world.last))("run")
    assertEquals(run("cohort").str, november)
    assertEquals(run("question").str, questionId("rates"))
    assertEquals(run("arm").str, "evidence")
    assertEquals(run("repetition").num.toInt, 1)
    assertEquals(run("model").str, "model-a")
    assertEquals(run("instructions")("version").num.toInt, 1)
    assertEquals(run("pack").str, forecastbench.domain.Ids.pack(november, questionId("rates")))
    ok(get(packPath)): Unit
  }

  Then(
    "the run holds exactly what the model was given, what the model answered, its forecast and the time it was recorded"
  ) { () =>
    val read    = json(ok(world.last))
    val request = fixture.model.requests(key).last
    val kept    = forecastbench.domain.Given.parse(read("given").str).get
    assertEquals(Some(kept.system), request.systemMessage)
    assertEquals(kept.user, lastUserText)
    assertEquals(read("answered").str, ScriptedModel.forecast(0.5))
    assertEquals(read("intact").bool, true)
    assertEquals(only.ending, Ending.Forecast(0.5))
    assertEquals(only.recordedAt, fixture.clock.now)
  }

  When("the experimenter reproduces the run") { () =>
    world.held("run") = ok(get(runPath))
    world.held("asked") = fixture.model.requests(key).size.toString
    world.last = post(s"$runPath/reproduce", "{}")
  }

  Then("the model is given exactly what the run holds it was given") { () =>
    ok(world.last): Unit
    val requests = fixture.model.requests(key)
    assertEquals(requests.size, world.held("asked").toInt + 1)
    val kept = forecastbench.domain.Given.parse(json(world.held("run"))("given").str).get
    assertEquals(requests.last.systemMessage, Some(kept.system))
    assertEquals(ScriptedModel.text(requests.last.copy(systemMessage = None)), kept.user)
  }

  Then("the answer is shown beside the run and is not recorded as a run") { () =>
    val shown = json(ok(world.last))
    assertEquals(shown("answered").str, ScriptedModel.forecast(0.5))
    assertEquals(shown("probability").num, 0.5)
    assertEquals(recorded(november).size, 1)
    assertEquals(json(ok(get(runPath))), json(world.held("run")))
  }

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

  Then("the run records its cost, its tokens and its latency") { () =>
    val run   = json(ok(world.last))("run")
    val usage = run("usage")
    assert(usage("inputTokens").num > 0 && usage("outputTokens").num > 0, usage.toString)
    assert(run("latencyMs").num >= 0, run.toString)
    // The model's cost and, for an arm that reads the appraisal, its share of it.
    val own = forecastbench.application.Models
      .all(fixture.settings, fixture.model)
      .cost("model-a", only.usage.get)
    assert(only.appraisalShare > 0, only.toString)
    assertEquals(BigDecimal(run("cost").num), own + only.appraisalShare)
  }
