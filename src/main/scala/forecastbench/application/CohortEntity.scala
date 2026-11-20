package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*
import forecastbench.domain.Cohort.Refusal
import forecastbench.domain.CohortEvent.*

import java.time.Instant

/** One line of a cohort's own history: when, and what happened. */
final case class Happened(at: Instant, what: String)

final case class CohortState(cohort: Option[Cohort], history: List[Happened])

final case class Draft(
    standing: Standing,
    model: String,
    pretendedTime: Option[Instant],
    now: Instant
)
final case class AddQuestions(entries: List[QuestionEntry], now: Instant)
final case class RemoveQuestion(question: String, now: Instant)
final case class AddArms(arms: List[String], now: Instant)
final case class RemoveArm(arm: String, now: Instant)
final case class Change(change: CohortChange, now: Instant)
final case class Freeze(
    instructions: Map[String, InstructionRef],
    gathererVersion: Int,
    scoringMethod: Int,
    closes: Map[String, Instant],
    now: Instant
)
final case class NotePack(question: String, note: PackNote, now: Instant)
final case class Note(note: String, now: Instant)
final case class RunKey(question: String, arm: String, repetition: Int)
final case class Start(made: List[RunKey], now: Instant)
final case class Started(started: Boolean, notMade: List[NotMade])
final case class Reserve(run: RunKey, runId: String, estimate: BigDecimal, now: Instant)
final case class Reservation(granted: Boolean, reason: Option[String])
final case class Settle(runId: String, cost: BigDecimal, now: Instant)
final case class NotMaking(run: RunKey, reason: String, now: Instant)

/**
 * One experiment. Every change to a drafted cohort is an event; a frozen one refuses them all
 * (FR-006). Its budget is decided here, by the one writer, so two questions cannot both spend the
 * last of it (research R5).
 */
final class CohortEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[CohortState, CohortEvent]:

  def emptyState: CohortState = CohortState(None, Nil)

  def applyEvent(event: CohortEvent): CohortState =
    val line = CohortEntity.describe(event).map(Happened(event.at, _))
    CohortState(Cohort(currentState.cohort, event), currentState.history ++ line)

  private def held = currentState.cohort

  private def decide[R](decision: Cohort.Decision)(reply: CohortState => R): Effect[R] =
    decision match
      case Left(Refusal.Conflict(why)) => effects.error(why, ErrorCode.Conflict)
      case Left(Refusal.Invalid(why)) if held.isEmpty && why == "no such cohort" =>
        effects.error(s"no cohort '${context.entityId}'", ErrorCode.NotFound)
      case Left(Refusal.Invalid(why)) => effects.error(why, ErrorCode.BadRequest)
      case Right(Nil)                 => effects.reply(reply(currentState))
      case Right(events)              => effects.persistAll(events).thenReply(reply)

  private def cohort(s: CohortState): Cohort = s.cohort.get

  def draft(d: Draft): Effect[Cohort] =
    decide(Cohort.drafting(held, context.entityId, d.standing, d.model, d.pretendedTime, d.now))(
      cohort
    )

  def addQuestions(a: AddQuestions): Effect[Cohort] =
    decide(Cohort.addingQuestions(held, a.entries, a.now))(cohort)

  def removeQuestion(r: RemoveQuestion): Effect[Cohort] =
    decide(Cohort.removingQuestion(held, r.question, r.now))(cohort)

  def addArms(a: AddArms): Effect[Cohort] =
    decide(Cohort.addingArms(held, a.arms, Arms.names, a.now))(cohort)

  def removeArm(r: RemoveArm): Effect[Cohort] =
    decide(Cohort.removingArm(held, r.arm, r.now))(cohort)

  def change(c: Change): Effect[Cohort] = decide(Cohort.changing(held, c.change, c.now))(cohort)

  def freeze(f: Freeze): Effect[Cohort] =
    decide(
      Cohort.freezing(held, f.instructions, f.gathererVersion, f.scoringMethod, f.closes, f.now)
    )(
      cohort
    )

  def notePack(n: NotePack): Effect[Cohort] =
    decide(Cohort.notingPack(held, n.question, n.note, n.now))(cohort)

  def note(n: Note): Effect[Cohort] = decide(Cohort.noting(held, n.note, n.now))(cohort)

  def startForecast(s: Start): Effect[Started] =
    val made     = s.made.map(k => (k.question, k.arm, k.repetition)).toSet
    val inWindow = held.flatMap(_.window).exists(_.holds(s.now))
    decide(Cohort.starting(held, made, s.now))(st =>
      if inWindow then Started(true, Nil)
      else
        val unmade = cohort(st).runs.filterNot(made.contains).toSet
        Started(false, cohort(st).notMade.filter(n => unmade((n.question, n.arm, n.repetition))))
    )

  def reserve(r: Reserve): Effect[Reservation] =
    val key = (r.run.question, r.run.arm, r.run.repetition)
    decide(Cohort.reserving(held, key, r.runId, r.estimate, r.now))(st =>
      if cohort(st).reserved.contains(r.runId) then Reservation(true, None)
      else
        val why =
          cohort(st).notMade.find(n => (n.question, n.arm, n.repetition) == key).map(_.reason)
        Reservation(false, why.orElse(Some(Cohort.BudgetSpent)))
    )

  def settle(s: Settle): Effect[Cohort] =
    decide(Cohort.settling(held, s.runId, s.cost, s.now))(cohort)

  def notMade(n: NotMaking): Effect[Cohort] =
    decide(Cohort.notMaking(held, (n.run.question, n.run.arm, n.run.repetition), n.reason, n.now))(
      cohort
    )

  def get: ReadOnlyEffect[CohortState] = effects.reply(currentState)

object CohortEntity
    extends EventSourcedEntity.Companion[CohortEntity, CohortState, CohortEvent](
      componentId = ComponentId("cohort"),
      stateSerializer = Codecs.serializer[CohortState]("cohort"),
      eventSerializer = Codecs.serializer[CohortEvent]("cohort-event")
    ):
  given Serializer[CohortState]  = stateSerializer
  given Serializer[Cohort]       = Codecs.serializer[Cohort]("cohort-value")
  given Serializer[Draft]        = Codecs.serializer[Draft]("cohort-draft")
  given Serializer[AddQuestions] = Codecs.serializer[AddQuestions]("cohort-add-questions")
  given Serializer[RemoveQuestion] =
    Codecs.serializer[RemoveQuestion]("cohort-remove-question")
  given Serializer[AddArms]     = Codecs.serializer[AddArms]("cohort-add-arms")
  given Serializer[RemoveArm]   = Codecs.serializer[RemoveArm]("cohort-remove-arm")
  given Serializer[Change]      = Codecs.serializer[Change]("cohort-change")
  given Serializer[Freeze]      = Codecs.serializer[Freeze]("cohort-freeze")
  given Serializer[NotePack]    = Codecs.serializer[NotePack]("cohort-note-pack")
  given Serializer[Note]        = Codecs.serializer[Note]("cohort-note")
  given Serializer[Start]       = Codecs.serializer[Start]("cohort-start")
  given Serializer[Started]     = Codecs.serializer[Started]("cohort-started")
  given Serializer[Reserve]     = Codecs.serializer[Reserve]("cohort-reserve")
  given Serializer[Reservation] = Codecs.serializer[Reservation]("cohort-reservation")
  given Serializer[NotMaking]   = Codecs.serializer[NotMaking]("cohort-not-making")
  given Serializer[Settle]      = Codecs.serializer[Settle]("cohort-settle")

  /** The line a cohort event adds to its history; reservations are not history. */
  def describe(event: CohortEvent): Option[String] = event match
    case e: CohortDrafted       => Some(s"drafted, ${e.standing.word}, with the model ${e.model}")
    case QuestionAdded(q, _, _) => Some(s"question $q added")
    case QuestionRemoved(q, _)  => Some(s"question $q removed")
    case ArmAdded(a, _)         => Some(s"arm $a added")
    case ArmRemoved(a, _)       => Some(s"arm $a removed")
    case ModelSet(m, _)         => Some(s"model set to $m")
    case WindowSet(w, _)        => Some(s"forecast window set from ${w.from} to ${w.to}")
    case RepetitionsSet(n, _)   => Some(s"repetitions set to $n")
    case BudgetSet(b, _)        => Some(s"budget set to $b")
    case _: CohortFrozen        => Some("frozen")
    case PackNoted(q, n, _)     => Some(s"pack for $q ${n.state}${n.reason.fold("")(r => s": $r")}")
    case _: ForecastStarted     => Some("forecast started")
    case RunNotMade(n, _) =>
      Some(s"run ${n.arm} ${n.repetition} for ${n.question} not made: ${n.reason}")
    case Noted(note, _)           => Some(note)
    case _: Reserved | _: Settled => None

  def create(context: EventSourcedEntityContext) = new CohortEntity(context)

  val draft          = command("draft")(_.draft)
  val addQuestions   = command("add-questions")(_.addQuestions)
  val removeQuestion = command("remove-question")(_.removeQuestion)
  val addArms        = command("add-arms")(_.addArms)
  val removeArm      = command("remove-arm")(_.removeArm)
  val change         = command("change")(_.change)
  val freeze         = command("freeze")(_.freeze)
  val notePack       = command("note-pack")(_.notePack)
  val note           = command("note")(_.note)
  val startForecast  = command("start-forecast")(_.startForecast)
  val reserve        = command("reserve")(_.reserve)
  val settle         = command("settle")(_.settle)
  val notMade        = command("not-made")(_.notMade)
  val get            = query("get")(_.get)
