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

  /** Adds the arm to the background's cohort, freezes it and forecasts it. */
  protected def forecastBy(words: String, name: String): (Int, String) =
    questionId(name): Unit
    ok(post(s"/cohorts/$november/arms", s"""{"arms":["${arm(words)}"]}""")): Unit
    freeze(november): Unit
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
