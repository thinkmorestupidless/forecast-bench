package forecastbench.reasoning

import com.thinkmorestupidless.ankka.agent.ModelResponse
import com.thinkmorestupidless.ankka.testkit.ScriptedServices
import forecastbench.application.{Instructions, Models, Settings}
import forecastbench.domain.*
import forecastbench.support.{ScriptedModel, ScriptedReasoning}

import java.time.Instant

class AppraiserSuite extends munit.FunSuite:

  private val now      = Instant.parse("2027-01-04T09:00:00Z")
  private val settings = Settings.load()
  private val model    = settings.models.keys.head
  private val asked = QuestionGiven(
    "Will rates fall [#a]?",
    "Resolves yes if they fall.",
    now.plusSeconds(86400 * 30),
    now
  )
  private val docs = (1 to 3).toList.map(n =>
    Document(
      n,
      s"source$n.example",
      s"https://source$n.example/$n",
      Some(now.minusSeconds(3600)),
      s"Document $n says the bank will act soon.",
      "t"
    )
  )
  private val pack = Pack("november", "kalshi.rates", 1, now, Nil, docs, Usage.zero, BigDecimal(0))
  private val a    = Appraise("november", "kalshi.rates", asked, pack, model, 2, now)

  private def run(m: ScriptedModel, r: ScriptedReasoning = ScriptedReasoning()) =
    val services = ScriptedServices().answer("reasoning")(q => r.respond(q.method, q.path, q.body))
    Appraiser.appraise(
      a,
      Models.all(settings, m),
      Reasoning(services("reasoning")),
      Instructions.latest("appraisal-reader").get,
      Instructions.latest("appraisal-open-points").get
    )

  test("each document read, its claims checked and written, the open points held"):
    val r    = ScriptedReasoning()
    val made = run(ScriptedModel(), r).toOption.get
    val q    = Reasoning.questionId("november", "kalshi.rates")
    assertEquals(made.claims.map(_.id), List(1, 2, 3).map(Reasoning.claimId(q, _)))
    assertEquals(made.claims.map(_.document), List(1, 2, 3))
    assertEquals(made.openPoints.size, 1)
    assertEquals(made.usage, Usage(4000, 800)) // three readings and the open points
    assertEquals(made.readerVersion, 1)
    assertEquals(
      r.all("holder").map(_("id").str).toSet,
      Set("november.appraisal", "november.graph.r1", "november.graph.r2")
    )
    assertEquals(r.all("claim").size, 3)

  test(
    "a claim whose quote is not in its document fails the appraisal, naming it, and nothing is written"
  ):
    val m = ScriptedModel()
    m.whenReading("Document 2 says")(_ =>
      Right(
        ModelResponse(
          ScriptedModel.claims(List(("Rates will fall", "supports", "words found nowhere")))
        )
      )
    )
    val r      = ScriptedReasoning()
    val failed = run(m, r).swap.toOption.get
    assertEquals(failed.reason, Appraisal.NotInDocument)
    assertEquals(failed.naming, Some("claim 1 of document [2]: Rates will fall"))
    assertEquals(r.all("claim"), Nil)
    assert(failed.cost > 0)

  test("a document that yields no claim gives none"):
    val m = ScriptedModel()
    m.whenReading("Document 3 says")(_ => Right(ModelResponse(ScriptedModel.claims(Nil))))
    assertEquals(run(m).toOption.get.claims.map(_.document), List(1, 2))

  test("the reasoning service unreachable fails the appraisal, saying so"):
    val r = ScriptedReasoning()
    r.unreachable()
    assertEquals(run(ScriptedModel(), r).swap.toOption.get.reason, Reasoning.Unreachable)
