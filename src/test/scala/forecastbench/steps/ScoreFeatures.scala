package forecastbench.steps

import forecastbench.report.Scoring
import forecastbench.support.ScriptedModel

class ScoreFeatures
    extends FeatureSuite("features/scoring/score.feature")
    with ModelSteps
    with OutcomeSteps:

  override protected def pending = Set(
    // T055: an arm's forecast over repetitions is read for the evidence arm.
    "an arm's forecast for a question is the mean of its repetitions",
    "an arm's forecast for a question uses only the repetitions that gave a forecast",
    "an arm with no forecast for a question has no score for it"
  )

  /** Drafted with its question; frozen by the step that makes its run (see ModelSteps). */
  Given("a prospective question {string} in a frozen cohort {string}") {
    (question: String, cohort: String) =>
      settle(draft(cohort), List(importedQuestion(question)), Nil)
  }

  private def r4(x: Double) = BigDecimal(x).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

  private def runOf: ujson.Value = json(ok(get(s"/runs/${only.id}")))

  private def scores: List[ujson.Value] = runOf("scores").arr.toList

  /** The run's latest score, once the consumers have made it. */
  private def latest: ujson.Value =
    eventually("the run to be scored")(scores.lastOption.filter(_ => scores.nonEmpty))("score")

  Given("a run for {string} with the forecast {string}") { (name: String, p: String) =>
    fixture.model.replies(key, ScriptedModel.forecast(p.toDouble))
    ok(forecastBy("model alone", name)): Unit
  }

  Given("a run for {string} with a forecast") { (name: String) =>
    fixture.model.replies(key, ScriptedModel.forecast(0.65))
    ok(forecastBy("model alone", name)): Unit
  }

  Then("the run's Brier score is {string}") { (b: String) =>
    eventually(s"a Brier score of $b") {
      scores.lastOption.map(_("score")("brier").num).filter(x => r4(x) == b.toDouble)
    }: Unit
  }

  Then("the run's log score is {string}") { (l: String) =>
    assertEquals(r4(latest("log").num), l.toDouble)
  }

  Given("the scoring method's bound is {string}") { (bound: String) =>
    assertEquals(Scoring.One.logBound, bound.toDouble)
  }

  Then("the run's scores name the scoring method that made them") { () =>
    latest: Unit
    scores.foreach(s => assertEquals(s("score")("method").num.toInt, 1))
  }

  Given("a run for {string} scored by the scoring method {string}") {
    (name: String, method: String) =>
      fixture.model.replies(key, ScriptedModel.forecast(0.7))
      ok(forecastBy("model alone", name)): Unit
      resolve(name, "yes")
      readOutcomes(): Unit
      assertEquals(latest("method").num.toInt, method.toInt)
  }

  When("the experimenter scores the cohort by the scoring method {string}") { (method: String) =>
    acted()
    world.last = post(s"/cohorts/$november/score?method=$method", "{}")
    ok(world.last): Unit
  }

  Then("the run has scores by the scoring method {string}") { (method: String) =>
    assert(scores.exists(_("score")("method").num.toInt == method.toInt), scores.toString)
  }

  Then("its scores by the scoring method {string} are still held") { (method: String) =>
    assert(scores.exists(_("score")("method").num.toInt == method.toInt), scores.toString)
  }

  Given("a run for {string} recorded as {word}") { (name: String, ending: String) =>
    ending match
      case "abstained" =>
        fixture.model.replies(key, """{"probability": null, "abstain": "No.", "reasons": []}""")
      case "failed" => fixture.model.unreachable(key)
      case other    => fail(s"no ending '$other'")
    ok(forecastBy("model alone", name)): Unit
  }

  When("the outcome of {string} is {string} and disputed") { (name: String, value: String) =>
    acted()
    resolve(name, value, disputed = true)
    readOutcomes(): Unit
  }

  Then("the run has no score") { () =>
    // Scoring the cohort now makes, at once, any score the consumers would make in time.
    assertEquals(json(ok(post(s"/cohorts/$november/score?method=1", "{}")))("scored").num.toInt, 0)
    Thread.sleep(500)
    assertEquals(scores, Nil)
  }

  Given("a run for {string} with the forecast {string} scored on the outcome {string}") {
    (name: String, p: String, value: String) =>
      fixture.model.replies(key, ScriptedModel.forecast(p.toDouble))
      ok(forecastBy("model alone", name)): Unit
      resolve(name, value)
      readOutcomes(): Unit
      latest: Unit
  }

  When("the outcome of {string} is revised to {string}") { (name: String, value: String) =>
    acted()
    resolve(name, value)
    readOutcomes(): Unit
  }

  Then("its scores on the earlier outcome are still held, marked as made from a revised outcome") {
    () =>
      val all = scores
      assertEquals(all.size, 2, all.toString)
      assertEquals(all.head("score")("outcomeSeq").num.toInt, 1)
      assertEquals(all.head("revised").bool, true)
      assertEquals(all(1)("revised").bool, false)
  }
