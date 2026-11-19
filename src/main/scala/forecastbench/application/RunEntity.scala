package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*
import forecastbench.report.Scoring

import java.time.Instant

final case class Record(run: RunRecorded, question: QuestionNow)
final case class ScoreRun(method: Int, outcome: Outcome, now: Instant)

/**
 * One run, recorded once. It has `record`, `score` and `get`, and nothing else: no command could
 * change a forecast or remove a run, which is stronger than a check that refuses it (FR-029).
 */
final class RunEntity extends EventSourcedEntity[RunState, RunEvent]:

  def emptyState: RunState = RunState(None, Nil)

  def applyEvent(event: RunEvent): RunState = Run(currentState, event)

  def record(r: Record): Effect[RunState] =
    Run.recording(currentState, r.run, r.question) match
      case Left(why) if why == Run.Held => effects.error(why, ErrorCode.Conflict)
      case Left(why)                    => effects.error(why, ErrorCode.Conflict)
      case Right(events)                => effects.persistAll(events).thenReplyState

  def score(s: ScoreRun): Effect[RunState] =
    val p      = currentState.run.flatMap(_.ending.forecast).getOrElse(0.0)
    val scored = Scoring.score(s.method, p, s.outcome.value)
    Run.scoring(currentState, s.method, s.outcome, scored, s.now) match
      case Left(why)     => effects.error(why, ErrorCode.Conflict)
      case Right(events) => effects.persistAll(events).thenReplyState

  def get: ReadOnlyEffect[RunState] = effects.reply(currentState)

object RunEntity
    extends EventSourcedEntity.Companion[RunEntity, RunState, RunEvent](
      componentId = ComponentId("run"),
      stateSerializer = Codecs.serializer[RunState]("run"),
      eventSerializer = Codecs.serializer[RunEvent]("run-event")
    ):
  given Serializer[RunState] = stateSerializer
  given Serializer[Record]   = Codecs.serializer[Record]("run-record")
  given Serializer[ScoreRun] = Codecs.serializer[ScoreRun]("run-score")

  def create(context: EventSourcedEntityContext) = new RunEntity

  val record = command("record")(_.record)
  val score  = command("score")(_.score)
  val get    = query("get")(_.get)
