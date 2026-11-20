package forecastbench.reasoning

import com.thinkmorestupidless.ankka.testkit.ScriptedServices
import forecastbench.domain.*
import forecastbench.support.ScriptedReasoning

import java.time.Instant

class ReasoningSuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")
  private val q   = Reasoning.questionId("november", "kalshi.rates")

  private def bench(): (ScriptedReasoning, ScriptedServices, Reasoning) =
    val scripted = ScriptedReasoning()
    val services =
      ScriptedServices().answer("reasoning")(r => scripted.respond(r.method, r.path, r.body))
    (scripted, services, Reasoning(services("reasoning")))

  private val docs = List(
    Document(
      1,
      "news.example",
      "https://news.example/1",
      Some(now.minusSeconds(3600)),
      "The bank held rates.",
      "t"
    ),
    Document(2, "blog.example", "https://blog.example/2", None, "Traders expect a cut.", "t"),
    Document(
      3,
      "late.example",
      "https://late.example/3",
      Some(now.plusSeconds(3600)),
      "A later view.",
      "t"
    )
  )
  private val pack = Pack("november", "kalshi.rates", 1, now, Nil, docs, Usage.zero, BigDecimal(0))

  /**
   * The records an appraisal writes, in order: holder, question, then each claim's source, evidence
   * and claim.
   */
  private def write(r: Reasoning) =
    for
      _  <- r.holder(Reasoning.appraiser("november"), "november appraisal")
      _  <- r.question(q, "Will rates fall?")
      s1 <- r.source("news.example")
      e1 <- r.evidence(s1, docs(0), now)
      _ <- r.claim(
        Reasoning.claimId(q, 1),
        Reasoning.appraiser("november"),
        "Rates were held",
        e1,
        Reasoning.hypothesis(q),
        Stance.Contradicts
      )
      s2 <- r.source("blog.example")
      e2 <- r.evidence(s2, docs(1), now)
      _ <- r.claim(
        Reasoning.claimId(q, 2),
        Reasoning.appraiser("november"),
        "A cut is expected",
        e2,
        Reasoning.hypothesis(q),
        Stance.Supports
      )
      _ <- r.await(Reasoning.claimId(q, 2))
    yield ()

  test("the question is held with a yes hypothesis and a no hypothesis"):
    val (scripted, _, r) = bench()
    assertEquals(write(r), Right(()))
    val held = scripted.get("question", q).get
    assertEquals(held("hypotheses").arr.map(_("id").str).toList, List("yes", "no"))

  test("a write sent twice is held once and answers as before"):
    val (scripted, _, r) = bench()
    assertEquals(write(r), Right(()))
    assertEquals(write(r), Right(()))
    assertEquals(scripted.all("claim").size, 2)

  test("the content is read back from the two cases, each claim at its document of the pack"):
    val (_, _, r) = bench()
    write(r): Unit
    val content = r.content(q, pack, List("When the bank meets")).toOption.get
    assertEquals(
      content.claims,
      List(
        ClaimGiven(
          "c1",
          "Rates were held",
          Stance.Contradicts,
          1,
          "news.example",
          docs(0).publishedAt,
          docs(0).excerpt
        ),
        ClaimGiven(
          "c2",
          "A cut is expected",
          Stance.Supports,
          2,
          "blog.example",
          None,
          docs(1).excerpt
        )
      )
    )
    assertEquals(content.openPoints, List("When the bank meets"))

  test("a time of publication later than now is not sent"):
    val (scripted, _, r) = bench()
    r.evidence("src.x", docs(2), now): Unit
    assert(scripted.all("evidence").forall(e => !e.obj.contains("publishedAt")))

  test("a belief rests on the claims it names"):
    val (scripted, _, r) = bench()
    write(r): Unit
    assertEquals(
      r.belief(
        Reasoning.beliefId(q, 1),
        Reasoning.graphHolder("november", 1),
        Reasoning.hypothesis(q),
        0.72,
        List(Reasoning.claimId(q, 2))
      ),
      Right(())
    )
    val b = scripted.get("revision", Reasoning.beliefId(q, 1)).get
    assertEquals(b("probability").num, 0.72)
    assertEquals(b("restsOn").arr.map(_("claim").str).toList, List(Reasoning.claimId(q, 2)))

  test("a service that cannot be reached, or answers 503, says so"):
    val (scripted, _, r) = bench()
    scripted.unreachable()
    assertEquals(r.holder("h", "h"), Left(Reasoning.Unreachable))
    val gone = Reasoning(ScriptedServices().unanswered("reasoning")("reasoning"))
    assertEquals(gone.question(q, "s"), Left(Reasoning.Unreachable))

  test("a refusal is the refusal's rule"):
    val (scripted, _, r) = bench()
    scripted.refuse("/claims", "claim.holder.not-a-writer")
    assertEquals(write(r), Left("claim.holder.not-a-writer"))

  test("waiting for a claim the service does not hold is refused"):
    val (_, _, r) = bench()
    assertEquals(r.await("november.x.c9"), Left("graph.wait.not-held"))
