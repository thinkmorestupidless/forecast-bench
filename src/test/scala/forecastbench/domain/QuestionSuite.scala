package forecastbench.domain

import forecastbench.domain.QuestionEvent.*

import java.time.Instant

class QuestionSuite extends munit.FunSuite:

  private val now    = Instant.parse("2027-01-04T09:00:00Z")
  private val closes = Instant.parse("2027-02-01T00:00:00Z")

  private def imp(
      standing: Standing = Standing.Prospective,
      outcomes: Int = 2,
      criteria: Option[String] = Some("Resolves yes if the Bank cuts."),
      outcome: Option[OutcomeReading] = None,
      closesAt: Instant = closes
  ) = QuestionImport(
    "kalshi",
    "BOE-CUT",
    "BOE",
    "Will the Bank cut?",
    criteria,
    closesAt,
    Some("Economics"),
    outcomes,
    "economy",
    standing,
    outcome,
    now
  )

  private def held(events: List[QuestionEvent]): Option[Question] =
    events.foldLeft(Option.empty[Question])(Question.apply)

  test("a first import holds the question with its standing"):
    val events = Question.importing(None, imp()).toOption.get
    val q      = held(events).get
    assertEquals(q.id, "kalshi.boe-cut")
    assertEquals(q.standing, Standing.Prospective)
    assertEquals(q.domain, "economy")
    assert(q.waiting)

  test("a question with other than two outcomes or no criteria is refused"):
    assertEquals(Question.importing(None, imp(outcomes = 5)), Left("more than two outcomes"))
    assertEquals(
      Question.importing(None, imp(criteria = Some("  "))),
      Left("no resolution criteria")
    )
    assertEquals(Question.importing(None, imp(criteria = None)), Left("no resolution criteria"))

  test("a development question's outcome arrives with its import"):
    val reading = OutcomeReading(OutcomeValue.Yes, disputed = false, Some(closes))
    val events =
      Question.importing(None, imp(Standing.Development, outcome = Some(reading))).toOption.get
    assertEquals(events.size, 2)
    assertEquals(held(events).flatMap(_.current).map(_.value), Some(OutcomeValue.Yes))

  test(
    "an import again records nothing unless the close time or criteria changed, never a standing"
  ):
    val first = held(Question.importing(None, imp()).toOption.get)
    assertEquals(Question.importing(first, imp(Standing.Development)), Right(Nil))
    val later = closes.plusSeconds(3600)
    assertEquals(
      Question.importing(first, imp(closesAt = later)).map(_.map(_.getClass.getSimpleName)),
      Right(List("QuestionRefreshed"))
    )
    val after = held(
      Question.importing(None, imp()).toOption.get ++ Question
        .importing(first, imp(closesAt = later))
        .toOption
        .get
    )
    assertEquals(after.map(_.standing), Some(Standing.Prospective))

  test("an outcome is recorded only when it differs, naming the one it revises"):
    val q   = held(Question.importing(None, imp()).toOption.get)
    val yes = OutcomeReading(OutcomeValue.Yes, disputed = true, Some(closes))
    val e1  = Question.reading(q, yes, now)
    val q1  = e1.foldLeft(q)(Question.apply)
    assertEquals(Question.reading(q1, yes, now), Nil)
    val e2 = Question.reading(q1, yes.copy(disputed = false), now)
    e2 match
      case List(OutcomeRead(o)) => assertEquals((o.seq, o.revises), (2, Some(1)))
      case other                => fail(s"expected one outcome, got $other")
    val q2 = e2.foldLeft(q1)(Question.apply)
    assertEquals(q2.map(_.outcomes.size), Some(2))
    assert(q2.flatMap(_.current).exists(_.settled))

  test("void is never settled"):
    assert(!Outcome(1, OutcomeValue.Void, false, None, now, None).settled)
