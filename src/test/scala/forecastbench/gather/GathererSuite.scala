package forecastbench.gather

import com.thinkmorestupidless.ankka.agent.{ModelResponse, TokenUsage}
import forecastbench.application.{Instructions, Limits, Models, Settings}
import forecastbench.domain.*
import forecastbench.support.{ScriptedModel, ScriptedSearch}

import java.time.Instant

class GathererSuite extends munit.FunSuite:

  private val now      = Instant.parse("2027-01-04T09:00:00Z")
  private val settings = Settings.load()
  private val model    = settings.models.keys.head
  private val limits   = Limits(12, 2000, 3, 10)
  private val asked = QuestionGiven(
    "Will rates fall [#g]?",
    "Resolves yes if they fall.",
    now.plusSeconds(86400 * 30),
    now
  )
  private val g            = Gather("november", "kalshi.q", asked, model, None, now)
  private def instructions = Instructions.latest("gatherer-queries").get

  private def gather(m: ScriptedModel, s: ScriptedSearch) =
    Gatherer.gather(g, Models.all(settings, m), s, instructions, limits)

  test("three searches: the statement and the two queries the model writes, each recorded"):
    val s    = ScriptedSearch()
    val pack = gather(ScriptedModel(), s).toOption.get
    assertEquals(
      pack.searches.map(_.query),
      List(asked.statement, s"latest on ${asked.statement}", s"figures for ${asked.statement}")
    )
    assertEquals(pack.searches.map(_.writtenBy), List("statement", model, model))
    assertEquals(pack.searches.head.results.size, 3)
    assertEquals(pack.documents.size, 9)
    assertEquals(pack.gathererVersion, 1)
    assertEquals(pack.usage, Usage(1000, 200))
    assert(pack.cost > 0)

  test("the queries are written from the question as an arm is given it, nothing else"):
    val m = ScriptedModel()
    gather(m, ScriptedSearch()): Unit
    val r = m.queryRequests("[#g]").head
    assertEquals(r.systemMessage, Some(instructions.text))
    assertEquals(ScriptedModel.text(r.copy(systemMessage = None)), Arm.block(asked))

  test("a search that fails leaves no pack and says why"):
    val s = ScriptedSearch()
    s.fails("[#g]")
    assertEquals(gather(ScriptedModel(), s), Left("the search failed: the scripted search is down"))

  test("a model that writes no queries leaves no pack and says why"):
    val m = ScriptedModel()
    m.whenQueries("[#g]")(_ =>
      Right(ModelResponse("I would search for rates.", usage = TokenUsage(1, 1)))
    )
    assert(
      gather(m, ScriptedSearch()).left.exists(_.startsWith("the queries could not be written"))
    )

  test("a search that finds nothing makes an empty pack"):
    val s = ScriptedSearch()
    s.findsNothing("[#g]")
    assert(gather(ScriptedModel(), s).toOption.get.empty)

  test("for a pretended time the search is asked for what was published before it"):
    val s = ScriptedSearch()
    val t = Instant.parse("2025-05-01T00:00:00Z")
    Gatherer.gather(
      g.copy(pretended = Some(t)),
      Models.all(settings, ScriptedModel()),
      s,
      instructions,
      limits
    ): Unit
    assert(s.asked("[#g]").forall(_._2.contains(t)))
