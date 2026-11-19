package forecastbench.domain

import forecastbench.domain.Cohort.Refusal.*
import forecastbench.domain.CohortEvent.*

import java.time.{Duration, Instant}

class CohortSuite extends munit.FunSuite:

  private val now    = Instant.parse("2027-01-04T09:00:00Z")
  private val window = Window(now, now.plus(Duration.ofDays(2)))
  private val closes = now.plus(Duration.ofDays(30))
  private val known  = Set("venue-price", "model-alone")

  private def run(held: Option[Cohort], d: Cohort.Decision): Option[Cohort] =
    d.fold(r => fail(s"refused: $r"), _.foldLeft(held)(Cohort.apply))

  private def drafted(standing: Standing = Standing.Prospective): Option[Cohort] =
    val pretended = Option.when(standing == Standing.Development)(now.minus(Duration.ofDays(400)))
    run(None, Cohort.drafting(None, "november", standing, "model-a", pretended, now))

  private def ready: Option[Cohort] =
    var c = drafted()
    c = run(
      c,
      Cohort.addingQuestions(c, List(QuestionEntry("q1", Standing.Prospective, closes)), now)
    )
    c = run(c, Cohort.addingArms(c, List("model-alone"), known, now))
    run(
      c,
      Cohort.changing(c, CohortChange(window = Some(window), budget = Some(BigDecimal(5))), now)
    )

  private def frozen: Option[Cohort] =
    val c = ready
    run(c, Cohort.freezing(c, Map.empty, 1, 1, Map("q1" -> closes), now))

  test("a development cohort names a pretended time and a prospective one does not"):
    assert(Cohort.drafting(None, "x", Standing.Development, "m", None, now).isLeft)
    assert(Cohort.drafting(None, "x", Standing.Prospective, "m", Some(now), now).isLeft)
    assert(Cohort.drafting(drafted(), "november", Standing.Prospective, "m", None, now).isLeft)

  test("a question of the other standing is refused, and the whole request with it"):
    val c = drafted()
    val d = Cohort.addingQuestions(
      c,
      List(
        QuestionEntry("q1", Standing.Prospective, closes),
        QuestionEntry("q2", Standing.Development, closes)
      ),
      now
    )
    assert(d match { case Left(Invalid(why)) => why.contains("q2"); case _ => false }, d)

  test("an arm the bench does not know is refused"):
    assert(Cohort.addingArms(drafted(), List("crystal-ball"), known, now).isLeft)

  test("repetitions under one, a budget not positive and an empty window are refused"):
    assert(Cohort.changing(drafted(), CohortChange(repetitions = Some(0)), now).isLeft)
    assert(Cohort.changing(drafted(), CohortChange(budget = Some(BigDecimal(0))), now).isLeft)
    assert(Cohort.changing(drafted(), CohortChange(window = Some(Window(now, now))), now).isLeft)

  test("a freeze is refused with no questions, no arms, no window or no budget"):
    assertEquals(
      Cohort.freezing(drafted(), Map.empty, 1, 1, Map.empty, now),
      Left(Invalid("the cohort names no questions"))
    )
    var c = drafted()
    c = run(
      c,
      Cohort.addingQuestions(c, List(QuestionEntry("q1", Standing.Prospective, closes)), now)
    )
    assertEquals(
      Cohort.freezing(c, Map.empty, 1, 1, Map.empty, now),
      Left(Invalid("the cohort names no arms"))
    )
    c = run(c, Cohort.addingArms(c, List("model-alone"), known, now))
    assertEquals(
      Cohort.freezing(c, Map.empty, 1, 1, Map.empty, now),
      Left(Invalid("the cohort has no forecast window"))
    )
    c = run(c, Cohort.changing(c, CohortChange(window = Some(window)), now))
    assertEquals(
      Cohort.freezing(c, Map.empty, 1, 1, Map.empty, now),
      Left(Invalid("the cohort has no budget"))
    )

  test("a freeze is refused naming a question that closes before the window ends"):
    val d = Cohort.freezing(ready, Map.empty, 1, 1, Map("q1" -> now.plus(Duration.ofDays(1))), now)
    assertEquals(d, Left(Invalid("the question 'q1' closes before the forecast window ends")))

  test("a frozen cohort refuses every change"):
    val c = frozen
    assert(c.get.frozen)
    List(
      Cohort.addingQuestions(c, Nil, now),
      Cohort.removingQuestion(c, "q1", now),
      Cohort.addingArms(c, List("venue-price"), known, now),
      Cohort.removingArm(c, "model-alone", now),
      Cohort.changing(c, CohortChange(repetitions = Some(2)), now),
      Cohort.freezing(c, Map.empty, 1, 1, Map.empty, now)
    ).foreach(d => assertEquals(d, Left(Conflict(Cohort.IsFrozen))))

  test("a cohort not frozen is not forecast"):
    assertEquals(Cohort.starting(ready, Set.empty, now), Left(Conflict(Cohort.IsNotFrozen)))

  test("outside the window nothing starts and every run is noted as not made, once"):
    val c     = frozen
    val after = now.plus(Duration.ofDays(3))
    val d     = Cohort.starting(c, Set.empty, after)
    assertEquals(
      d.map(_.collect { case RunNotMade(n, _) => n.reason }),
      Right(List(Cohort.WindowEnded))
    )
    val c2 = run(c, d)
    assertEquals(Cohort.starting(c2, Set.empty, after), Right(Nil))

  test("a reservation past the budget is refused and the run noted"):
    var c = frozen
    (1 to 5).foreach { i =>
      c = run(c, Cohort.reserving(c, ("q1", "model-alone", i), s"r$i", BigDecimal(1), now))
      c = run(c, Cohort.settling(c, s"r$i", BigDecimal(1), now))
    }
    assertEquals(c.get.spent, BigDecimal(5))
    val d = Cohort.reserving(c, ("q1", "model-alone", 6), "r6", BigDecimal(1), now)
    assertEquals(
      d.map(_.collect { case RunNotMade(n, _) => n.reason }),
      Right(List(Cohort.BudgetSpent))
    )

  test("a reservation after the window is refused and the run noted"):
    val d = Cohort.reserving(
      frozen,
      ("q1", "model-alone", 1),
      "r1",
      BigDecimal(0),
      now.plus(Duration.ofDays(3))
    )
    assertEquals(
      d.map(_.collect { case RunNotMade(n, _) => n.reason }),
      Right(List(Cohort.WindowEnded))
    )
