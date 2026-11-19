package forecastbench.steps

import forecastbench.domain.RunRecorded
import forecastbench.support.ScriptedModel

/**
 * The steps the arms and runs features share: the background's cohort, what the model answers, and
 * an arm forecasting a question of it.
 */
trait ModelSteps extends CohortSetup:

  protected def november: String = world.cohort("november")

  /** What the scripted model keys this scenario's answers on: the scenario's number. */
  protected def key: String = s"[#${world.n}]"

  /**
   * The background's cohort is drafted with its question here and frozen by the first step that
   * forecasts, with the arms the scenario names: a cohort is frozen before anything is observed of
   * it, and a frozen cohort could not be given the arms a scenario adds.
   */
  Given("a frozen cohort {string} with the model {string} and a prospective question {string}") {
    (name: String, model: String, question: String) =>
      val id = draft(name, model)
      settle(id, List(importedQuestion(question)), Nil)
  }

  Given("the model answers {string}") { (answer: String) =>
    val reply = answer.toDoubleOption match
      case Some(_) =>
        s"""{"probability": $answer, "abstain": null, "reasons": [], "rationale": "x"}"""
      case None =>
        s"""{"probability": "$answer", "abstain": null, "reasons": [], "rationale": "x"}"""
    fixture.model.replies(key, reply)
  }

  Given("the model cannot be reached")(() => fixture.model.unreachable(key))

  When("the arm {string} forecasts {string}") { (words: String, name: String) =>
    world.last = forecastBy(words, name)
  }

  protected def drafted: Boolean = readCohort(november)("status").str == "drafted"

  /** Adds the arms to the background's cohort, while it is drafted. */
  protected def names(words: List[String]): Unit =
    val named = readCohort(november)("arms").arr.map(_.str).toSet
    val add   = words.map(arm).filterNot(named)
    if drafted && add.nonEmpty then
      val arms = add.map(a => s""""$a"""").mkString(",")
      ok(post(s"/cohorts/$november/arms", s"""{"arms":[$arms]}""")): Unit

  /** Adds the arms to the background's cohort and freezes it, unless a step already has. */
  protected def frozenWithArms(words: List[String]): Unit =
    if drafted then
      names(words)
      freeze(november): Unit

  protected def frozenWith(words: String): Unit = frozenWithArms(List(words))

  Given("the cohort names the arms {string} and {string}")((a: String, b: String) =>
    names(List(a, b))
  )

  Given("the cohort names the arms {string}, {string} and {string}") {
    (a: String, b: String, c: String) =>
      names(List(a, b, c))
  }

  Given("the cohort names the arms {string}, {string}, {string} and {string}") {
    (a: String, b: String, c: String, d: String) => names(List(a, b, c, d))
  }

  When("the arms {string} and {string} forecast {string}") { (a: String, b: String, q: String) =>
    questionId(q): Unit
    frozenWithArms(List(a, b))
    world.last = forecastAndWait(november)
    ok(world.last): Unit
  }

  When("the arms {string}, {string}, {string} and {string} forecast {string}") {
    (a: String, b: String, c: String, d: String, q: String) =>
      questionId(q): Unit
      frozenWithArms(List(a, b, c, d))
      world.last = forecastAndWait(november)
    ok(world.last): Unit
  }

  /** The forecasting requests of the scenario made by one arm, by its instructions. */
  protected def requestsOf(
      armName: String
  ): Vector[com.thinkmorestupidless.ankka.agent.ModelRequest] =
    val text = forecastbench.application.Instructions.latest(armName).map(_.text)
    fixture.model.requests(key).filter(_.systemMessage == text)

  protected def userText(r: com.thinkmorestupidless.ankka.agent.ModelRequest): String =
    ScriptedModel.text(r.copy(systemMessage = None))

  /** The arm forecasts the background's question: added, frozen and forecast. */
  protected def forecastBy(words: String, name: String): (Int, String) =
    questionId(name): Unit
    frozenWith(words)
    forecastAndWait(november)

  protected def retryAndWait(): (Int, String) =
    val answer = post(s"/cohorts/$november/retry-failed", "{}")
    val round  = json(ok(answer))("round").num.toInt
    if round > 0 then awaitRound(november, round)
    answer

  /** The one run the scenario made. */
  protected def only: RunRecorded =
    recorded(november) match
      case List(run) => run
      case runs      => fail(s"expected one run, found ${runs.map(_.id)}")

  protected def lastUserText: String =
    val r = fixture.model.requests(key).lastOption.getOrElse(fail("the model was given nothing"))
    ScriptedModel.text(r.copy(systemMessage = None))
