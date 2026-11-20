package forecastbench.domain

import java.time.{Duration, Instant}

class RunSuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")

  private def run(ending: Ending = Ending.Forecast(0.6)) = RunRecorded(
    "c.q.model-alone.1.1",
    "c",
    "q",
    "model-alone",
    1,
    1,
    ending,
    now,
    Some("model-a"),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Nil,
    None,
    BigDecimal(0),
    BigDecimal(0),
    0,
    None
  )

  private val open =
    QuestionNow(Standing.Prospective, now.plus(Duration.ofDays(9)), hasOutcome = false)
  private val empty = RunState(None, Nil)
  private def held(r: RunRecorded = run()) =
    Run.recording(empty, r, open).toOption.get.foldLeft(empty)(Run.apply)
  private val yes = Outcome(1, OutcomeValue.Yes, false, Some(now), now, None)

  test("a run is recorded once"):
    assertEquals(Run.recording(held(), run(), open), Left(Run.Held))

  test("no run for a prospective question that has closed or has an outcome"):
    assertEquals(Run.recording(empty, run(), open.copy(closesAt = now)), Left(Run.Closed))
    assertEquals(Run.recording(empty, run(), open.copy(hasOutcome = true)), Left(Run.HasOutcome))

  test("a development question's run is recorded though it has closed and has an outcome"):
    val dev = QuestionNow(Standing.Development, now.minus(Duration.ofDays(9)), hasOutcome = true)
    assert(Run.recording(empty, run(), dev).isRight)

  test("a score only for a forecast, on a settled outcome, once for a method and an outcome"):
    val s = held()
    val e = Run.scoring(s, 1, yes, Some((0.16, 0.51)), now)
    assert(e.isRight)
    val scored = e.toOption.get.foldLeft(s)(Run.apply)
    assertEquals(Run.scoring(scored, 1, yes, Some((0.16, 0.51)), now), Left(Run.AlreadyScored))
    assert(
      Run.scoring(scored, 1, yes.copy(seq = 2, revises = Some(1)), Some((0.16, 0.51)), now).isRight
    )
    assertEquals(
      Run.scoring(held(run(Ending.Abstained("x"))), 1, yes, Some((0, 0)), now),
      Left(Run.NoForecast)
    )
    assertEquals(
      Run.scoring(s, 1, yes.copy(disputed = true), Some((0, 0)), now),
      Left(Run.NotSettled)
    )
    assertEquals(
      Run.scoring(s, 1, yes.copy(value = OutcomeValue.Void), Some((0, 0)), now),
      Left(Run.NotSettled)
    )
