package forecastbench.steps

import com.thinkmorestupidless.ankka.agent.{ModelResponse, TokenUsage}
import forecastbench.application.{AppraisalEntity, AppraisalState}
import forecastbench.domain.*
import forecastbench.reasoning.Reasoning
import forecastbench.support.{ScriptedModel, ScriptedSearch}

/** The appraisal of the background's question, as the appraisal features share it. */
trait AppraisalSteps extends ModelSteps:

  /** Drafted with its question and no arm; frozen by the step that names its arms. */
  Given("a frozen cohort {string} with a prospective question {string}") {
    (name: String, question: String) => settle(draft(name), List(importedQuestion(question)), Nil)
  }

  protected def documents: Int = world.held.get("documents").map(_.toInt).getOrElse(4)

  /** The scenario's documents: the statement's search finds them all, the model's queries none. */
  Given("the question {string} has an evidence pack of {string} documents") {
    (name: String, n: String) =>
      world.held("documents") = n
      fixture.search.findsBy(key) { query =>
        if query != statement(name) then Nil
        else
          (1 to n.toInt).toList.map(i =>
            ScriptedSearch.result(
              s"https://doc.example/${world.n}/$i",
              text = s"Document $i of the pack for $key says the bank will act on rates."
            )
          )
      }
  }

  protected def rq: String = Reasoning.questionId(november, questionId("rates"))

  protected def appraisalState: AppraisalState =
    fixture.kit.componentClient
      .forEventSourcedEntity(
        com.thinkmorestupidless.ankka.core.EntityId(Ids.appraisal(november, questionId("rates")))
      )
      .call(AppraisalEntity.get)
      .invoke()

  /** Names the graph and prose arms if none is named, freezes, and waits for the appraisal. */
  protected def appraised(): AppraisalState =
    if drafted && readCohort(november)("arms").arr.isEmpty then names(List("graph", "prose"))
    frozenWithArms(Nil)
    eventually("the appraisal to be held")(Option(appraisalState).filter(_.held))

  protected def made: AppraisalMade =
    appraised().made.getOrElse(fail(s"the appraisal failed: ${appraisalState.failed}"))

  Given("the appraisal of {string} is made")((_: String) => made: Unit)

  /** The document a reading request gives, by its number. */
  protected def docOf(r: com.thinkmorestupidless.ankka.agent.ModelRequest): Int =
    """\nDocument:\n\[(\d+)\]""".r.findFirstMatchIn(userText(r)).map(_.group(1).toInt).getOrElse(0)

  /** Each document's claims, as stances, each resting on words of that document. */
  protected def reads(stances: Int => List[String]): Unit =
    fixture.model.whenReading(key) { r =>
      val quote = ScriptedModel.quoteOf(r)
      val stated = stances(docOf(r)).zipWithIndex.map((s, i) =>
        (s"Document ${docOf(r)} claim ${i + 1}", s, quote)
      )
      Right(ModelResponse(ScriptedModel.claims(stated), usage = ScriptedModel.Usage))
    }

  Given("the graph revises a claim once the arm {string} has been given it") { (words: String) =>
    fixture.model.when(key) { r =>
      if r.systemMessage == forecastbench.application.Instructions.latest(arm(words)).map(_.text)
      then fixture.reasoning.revise("claim", Reasoning.claimId(rq, 1))(_("statement") = "Revised.")
      Right(ModelResponse(ScriptedModel.forecast(0.5), usage = ScriptedModel.Usage))
    }
  }

  Then(
    "the run of the arm {string} is recorded as failed, saying the two shapes did not hold the same content"
  ) { (words: String) =>
    val run = recorded(november).find(_.arm == arm(words)).getOrElse(fail("no run of the arm"))
    assertEquals(run.ending, Ending.Failed("the two shapes did not hold the same content"))
    assertEquals(
      requestsOf(arm(words)),
      Vector.empty,
      "the model was called for a run that failed the check"
    )
  }

  Given("the graph applies the first claim of {string} after the last") { (_: String) =>
    fixture.reasoning.late(Reasoning.claimId(rq, 1))
  }

  Given("the graph applies the first claim of {string} only on the third read") { (_: String) =>
    fixture.reasoning.appliedAfter(Reasoning.claimId(rq, 1), 2)
  }

  Given("the graph never applies the first claim of {string}") { (_: String) =>
    fixture.reasoning.neverApplied(Reasoning.claimId(rq, 1))
  }

  Then("the arm {string} is given every claim of the appraisal") { (words: String) =>
    val asked = requestsOf(arm(words)).headOption.getOrElse(fail("the arm was not asked"))
    made.claims.map(c => Reasoning.short(c.id)).foreach { c =>
      assert(userText(asked).contains(s"[$c]"), s"$c was not given")
    }
  }

  Then("the runs of the arms {string} and {string} are failed, the graph not caught up") {
    (a: String, b: String) =>
      List(a, b).foreach { words =>
        val run = recorded(november).find(_.arm == arm(words)).getOrElse(fail("no run"))
        assertEquals(run.ending, Ending.Failed(Reasoning.NotCaughtUp))
        assertEquals(requestsOf(arm(words)), Vector.empty, "the model was called")
      }
  }

  protected val zeroUsage: TokenUsage = TokenUsage(0, 0)
