package forecastbench.domain

import java.time.Instant

/** An object in the bucket `given`, named by its SHA-256. */
final case class ObjectRef(sha256: String, bytes: Long)

/** One arm's answer to one question in one cohort for one repetition, recorded once. */
final case class RunRecorded(
    id: String,
    cohort: String,
    question: String,
    arm: String,
    repetition: Int,
    attempt: Int,
    ending: Ending,
    recordedAt: Instant,
    model: Option[String],
    effort: Option[String],
    thinking: Option[Boolean],
    instructions: Option[InstructionRef],
    pack: Option[String],
    appraisal: Option[String],
    `given`: Option[ObjectRef],
    answered: Option[ObjectRef],
    reasons: List[String],
    usage: Option[Usage],
    cost: BigDecimal,
    appraisalShare: BigDecimal,
    latencyMs: Long,
    venue: Option[VenueReading]
)

/** A score of a run's forecast by one scoring method, on one outcome of its question. */
final case class Score(
    method: Int,
    outcomeSeq: Int,
    outcome: OutcomeValue,
    brier: Double,
    log: Double,
    at: Instant
)

enum RunEvent:
  case Recorded(run: RunRecorded)
  case Scored(score: Score)

final case class RunState(run: Option[RunRecorded], scores: List[Score])

/** What the run entity is told of its question, to refuse as the last guard (FR-030). */
final case class QuestionNow(standing: Standing, closesAt: Instant, hasOutcome: Boolean)

/** A run's decisions. There is no decision that changes or removes a run (FR-029). */
object Run:
  import RunEvent.*

  val Held          = "the run is recorded"
  val Closed        = "the question has closed"
  val HasOutcome    = "the question has an outcome"
  val NoForecast    = "the run has no forecast"
  val NotSettled    = "the outcome is not yes or no and settled"
  val AlreadyScored = "the run is scored by that method on that outcome"

  def recording(held: RunState, run: RunRecorded, q: QuestionNow): Either[String, List[RunEvent]] =
    if held.run.isDefined then Left(Held)
    else if q.standing == Standing.Prospective && !run.recordedAt.isBefore(q.closesAt) then
      Left(Closed)
    else if q.standing == Standing.Prospective && q.hasOutcome then Left(HasOutcome)
    else Right(List(Recorded(run)))

  /** A score for a forecast, once for a method and an outcome, on a settled yes or no. */
  def scoring(
      held: RunState,
      method: Int,
      outcome: Outcome,
      score: Option[(Double, Double)],
      at: Instant
  ): Either[String, List[RunEvent]] =
    held.run.flatMap(_.ending.forecast) match
      case None                        => Left(NoForecast)
      case Some(_) if !outcome.settled => Left(NotSettled)
      case Some(_) if held.scores.exists(s => s.method == method && s.outcomeSeq == outcome.seq) =>
        Left(AlreadyScored)
      case Some(_) =>
        score match
          case Some((brier, log)) =>
            Right(List(Scored(Score(method, outcome.seq, outcome.value, brier, log, at))))
          case None => Left(s"there is no scoring method $method")

  def apply(state: RunState, event: RunEvent): RunState = event match
    case Recorded(run) => state.copy(run = Some(run))
    case Scored(score) => state.copy(scores = state.scores :+ score)
