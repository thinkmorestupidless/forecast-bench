package forecastbench.application

import com.thinkmorestupidless.ankka.core.ErrorCode
import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import forecastbench.domain.*

import java.time.{Duration, Instant}

class RunEntitySuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")
  private val run = RunRecorded(
    "c.q.model-alone.1.1",
    "c",
    "q",
    "model-alone",
    1,
    1,
    Ending.Forecast(0.6),
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

  test("a second record is refused and the run is as it was"):
    val kit = EventSourcedTestKit.of(RunEntity, run.id)
    kit.call(RunEntity.record)(Record(run, open)): Unit
    val again = kit.call(RunEntity.record)(Record(run.copy(ending = Ending.Forecast(0.9)), open))
    assertEquals(again.error.code, ErrorCode.Conflict)
    assertEquals(kit.currentState.run.map(_.ending), Some(Ending.Forecast(0.6)))

  test("a run has no command that changes its forecast or removes it"):
    val handlers = RunEntity.descriptor.toString
    // The companion's handlers are exactly these three.
    assertEquals(List(RunEntity.record, RunEntity.score, RunEntity.get).size, 3)
    assert(!handlers.contains("delete"), handlers)

  test("a score for a method and an outcome is made once"):
    val kit = EventSourcedTestKit.of(RunEntity, run.id)
    kit.call(RunEntity.record)(Record(run, open)): Unit
    val yes = Outcome(1, OutcomeValue.Yes, false, Some(now), now, None)
    assertEquals(
      kit
        .call(RunEntity.score)(ScoreRun(1, yes, now))
        .replyValue
        .scores
        .map(s => (s.method, s.brier)),
      List((1, (0.6 - 1) * (0.6 - 1)))
    )
    assertEquals(kit.call(RunEntity.score)(ScoreRun(1, yes, now)).error.code, ErrorCode.Conflict)
