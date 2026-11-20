package forecastbench.steps

import forecastbench.report.Scoring
import forecastbench.support.ScriptedModel

class ScoreFeatures
    extends FeatureSuite("features/scoring/score.feature")
    with ModelSteps
    with OutcomeSteps:

  /**
   * The arm's repetitions of the background's question, answered in turn: its runs are made one
   * after another, so the answers come in the order they are listed.
   */
  private def repetitions(words: String, answers: List[Either[String, Double]]): Unit =
    val next = java.util.concurrent.atomic.AtomicInteger(0)
    fixture.model.when(key) { _ =>
      answers(next.getAndIncrement() min (answers.size - 1)) match
        case Left(reply) =>
          Right(
            com.thinkmorestupidless.ankka.agent.ModelResponse(reply, usage = ScriptedModel.Usage)
          )
        case Right(p) =>
          Right(
            com.thinkmorestupidless.ankka.agent
              .ModelResponse(ScriptedModel.forecast(p), usage = ScriptedModel.Usage)
          )
    }
    ok(put(s"/cohorts/$november", s"""{"repetitions":${answers.size}}""")): Unit
    ok(forecastBy(words, "rates")): Unit
    assertEquals(recorded(november).size, answers.size)
    // The runs are in the view the report reads before a scenario reads the report.
    eventually("the runs to be listed") {
      Option.when(json(ok(get(s"/runs?cohort=$november"))).arr.size == answers.size)(())
    }

  Given(
    "the arm {string} has runs for {string} with the forecasts {string}, {string} and {string}"
  ) { (words: String, _: String, a: String, b: String, c: String) =>
    repetitions(words, List(a, b, c).map(p => Right(p.toDouble)))
  }

  Given(
    "the arm {string} has runs for {string} with the forecasts {string} and {string} and one failed run"
  ) { (words: String, _: String, a: String, b: String) =>
    repetitions(words, List(Right(a.toDouble), Right(b.toDouble), Left("I would rather not say.")))
    assertEquals(recorded(november).count(_.ending.word == "failed"), 1)
  }

  Given("every run of the arm {string} for {string} abstained") { (words: String, _: String) =>
    val abstain =
      """{"probability": null, "abstain": "nothing here can be known", "reasons": [], "rationale": "x"}"""
    repetitions(words, List.fill(3)(Left(abstain)))
    assert(recorded(november).forall(_.ending.word == "abstained"))
  }

  /** The report's figure for the arm and the background's question, once its outcome is scored. */
  private def figure(name: String): ujson.Value =
    eventually("the arm's figure to be scored") {
      json(ok(get(s"/cohorts/$november/report")))("perQuestion").arr.find(f =>
        f("question").str == questionId(name) && f("arm").str == "evidence" && !f("brier").isNull
      )
    }

  Then("the arm's forecast for {string} is {string}") { (name: String, p: String) =>
    assertEqualsDouble(figure(name)("forecast").num, p.toDouble, 1e-9)
  }

  Then("the arm's Brier score for {string} is {string}") { (name: String, b: String) =>
    assertEquals(r4(figure(name)("brier").num), b.toDouble)
  }

  Then("the spread of the repetitions is kept beside it") { () =>
    val f = figure("rates")
    assertEquals(f("repetitions").num.toInt, 3)
    assertEqualsDouble(f("spread").num, 0.1, 1e-9)
  }

  Then("the arm has no score for {string}") { (name: String) =>
    eventually("the outcome to be read") {
      Option.when(json(ok(get(s"/questions/${questionId(name)}")))("outcomes").arr.nonEmpty)(())
    }
    val figures = json(ok(get(s"/cohorts/$november/report")))("perQuestion").arr
    assert(
      !figures.exists(f => f("question").str == questionId(name) && f("arm").str == "evidence"),
      figures.toString
    )
    recorded(november).foreach(r =>
      assertEquals(json(ok(get(s"/runs/${r.id}")))("scores").arr.size, 0)
    )
  }

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
