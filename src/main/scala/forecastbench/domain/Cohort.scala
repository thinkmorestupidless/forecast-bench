package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.time.Instant

enum CohortStatus:
  case Drafted, Frozen

  def word: String = toString.toLowerCase

object CohortStatus:
  given JsonValueCodec[CohortStatus] = Words.codec(values.toSeq, _.word)

/** The two instants between which runs are made, fixed at freeze. */
final case class Window(from: Instant, to: Instant):
  def holds(t: Instant): Boolean = !t.isBefore(from) && !t.isAfter(to)

/** An instruction set as a cohort fixes it: its version and the SHA-256 of its text. */
final case class InstructionRef(version: Int, digest: String)

/** A run the cohort will never make, and why. */
final case class NotMade(question: String, arm: String, repetition: Int, reason: String)

/** What the gatherer made of a question's pack: `gathered`, `empty`, or `failed` with a reason. */
final case class PackNote(state: String, reason: Option[String]):
  def failed: Boolean = state == PackNote.FailedState

object PackNote:
  val Gathered: PackNote            = PackNote("gathered", None)
  val Empty: PackNote               = PackNote("empty", None)
  val FailedState                   = "failed"
  def Failed(why: String): PackNote = PackNote(FailedState, Some(why))

final case class Cohort(
    id: String,
    standing: Standing,
    model: String,
    pretendedTime: Option[Instant],
    draftedAt: Instant,
    questions: List[String],
    arms: List[String],
    window: Option[Window],
    repetitions: Int,
    budget: Option[BigDecimal],
    status: CohortStatus,
    frozenAt: Option[Instant],
    instructions: Map[String, InstructionRef],
    gathererVersion: Option[Int],
    scoringMethod: Option[Int],
    spent: BigDecimal,
    reserved: Map[String, BigDecimal],
    notMade: List[NotMade],
    packs: Map[String, PackNote],
    forecastStartedAt: Option[Instant],
    notes: List[String]
):
  def frozen: Boolean = status == CohortStatus.Frozen

  /** What every run of the cohort would be: each question, arm and repetition. */
  def runs: List[(String, String, Int)] =
    for
      q <- questions
      a <- arms
      r <- 1 to repetitions
    yield (q, a, r)

/** A question as a cohort is asked to take it: its id, standing and the time it closes. */
final case class QuestionEntry(id: String, standing: Standing, closesAt: Instant)

/** A change to a drafted cohort's settings; what is absent stays as it was. */
final case class CohortChange(
    model: Option[String] = None,
    window: Option[Window] = None,
    repetitions: Option[Int] = None,
    budget: Option[BigDecimal] = None
)

enum CohortEvent:
  case CohortDrafted(
      id: String,
      standing: Standing,
      model: String,
      pretendedTime: Option[Instant],
      at: Instant
  )
  case QuestionAdded(question: String, closesAt: Instant, at: Instant)
  case QuestionRemoved(question: String, at: Instant)
  case ArmAdded(arm: String, at: Instant)
  case ArmRemoved(arm: String, at: Instant)
  case ModelSet(model: String, at: Instant)
  case WindowSet(window: Window, at: Instant)
  case RepetitionsSet(repetitions: Int, at: Instant)
  case BudgetSet(budget: BigDecimal, at: Instant)
  case CohortFrozen(
      instructions: Map[String, InstructionRef],
      gathererVersion: Int,
      scoringMethod: Int,
      at: Instant
  )
  case PackNoted(question: String, note: PackNote, at: Instant)
  case ForecastStarted(at: Instant)
  case Reserved(run: String, estimate: BigDecimal, at: Instant)
  case Settled(run: String, cost: BigDecimal, at: Instant)
  case RunNotMade(notMade: NotMade, at: Instant)
  case Noted(note: String, at: Instant)

  def at: Instant

/** A cohort's decisions: each answers the events to persist, or the refusal. */
object Cohort:
  import CohortEvent.*

  val IsFrozen       = "the cohort is frozen"
  val IsNotFrozen    = "the cohort is not frozen"
  val BudgetSpent    = "the budget was spent"
  val WindowEnded    = "the forecast window ended"
  val WindowNotBegun = "the forecast window has not begun"

  /** Why a refusal is refused: a conflict with the cohort's state, or a request that is invalid. */
  enum Refusal:
    case Conflict(why: String)
    case Invalid(why: String)

  import Refusal.*

  type Decision = Either[Refusal, List[CohortEvent]]

  def drafting(
      held: Option[Cohort],
      id: String,
      standing: Standing,
      model: String,
      pretended: Option[Instant],
      now: Instant
  ): Decision =
    if held.isDefined then Left(Conflict(s"the cohort '$id' is held"))
    else if standing == Standing.Development && pretended.isEmpty then
      Left(Invalid("a development cohort names a pretended time"))
    else if standing == Standing.Prospective && pretended.isDefined then
      Left(Invalid("a prospective cohort has no pretended time"))
    else Right(List(CohortDrafted(id, standing, model, pretended, now)))

  private def drafted(held: Option[Cohort])(decide: Cohort => Decision): Decision =
    held match
      case None                => Left(Invalid("no such cohort"))
      case Some(c) if c.frozen => Left(Conflict(IsFrozen))
      case Some(c)             => decide(c)

  /** Refused whole when any question's standing is not the cohort's. */
  def addingQuestions(held: Option[Cohort], entries: List[QuestionEntry], now: Instant): Decision =
    drafted(held) { c =>
      entries.find(_.standing != c.standing) match
        case Some(e) =>
          Left(
            Invalid(
              s"the question '${e.id}' is ${e.standing.word} and the cohort is ${c.standing.word}"
            )
          )
        case None =>
          Right(
            entries
              .filterNot(e => c.questions.contains(e.id))
              .distinctBy(_.id)
              .map(e => QuestionAdded(e.id, e.closesAt, now))
          )
    }

  def removingQuestion(held: Option[Cohort], q: String, now: Instant): Decision =
    drafted(held)(c =>
      if c.questions.contains(q) then Right(List(QuestionRemoved(q, now)))
      else Left(Invalid(s"the cohort does not name '$q'"))
    )

  def addingArms(
      held: Option[Cohort],
      arms: List[String],
      known: Set[String],
      now: Instant
  ): Decision =
    drafted(held)(c =>
      arms.find(a => !known.contains(a)) match
        case Some(a) => Left(Invalid(s"the bench knows no arm '$a'"))
        case None    => Right(arms.distinct.filterNot(c.arms.contains).map(ArmAdded(_, now)))
    )

  def removingArm(held: Option[Cohort], arm: String, now: Instant): Decision =
    drafted(held)(c =>
      if c.arms.contains(arm) then Right(List(ArmRemoved(arm, now)))
      else Left(Invalid(s"the cohort does not name the arm '$arm'"))
    )

  def changing(held: Option[Cohort], s: CohortChange, now: Instant): Decision =
    drafted(held) { _ =>
      if s.repetitions.exists(_ < 1) then Left(Invalid("repetitions are at least 1"))
      else if s.budget.exists(_ <= 0) then Left(Invalid("a budget is more than nought"))
      else if s.window.exists(w => !w.to.isAfter(w.from)) then
        Left(Invalid("a forecast window ends after it begins"))
      else
        Right(
          s.model.map(ModelSet(_, now)).toList ++
            s.window.map(WindowSet(_, now)) ++
            s.repetitions.map(RepetitionsSet(_, now)) ++
            s.budget.map(BudgetSet(_, now))
        )
    }

  /**
   * Freezing fixes everything. Refused for no questions, no arms, no window, no budget, or a
   * prospective question that closes before the window ends, named (FR-006). `closes` is each
   * question's close time as the venue last gave it.
   */
  def freezing(
      held: Option[Cohort],
      instructions: Map[String, InstructionRef],
      gathererVersion: Int,
      scoringMethod: Int,
      closes: Map[String, Instant],
      now: Instant
  ): Decision =
    drafted(held) { c =>
      if c.questions.isEmpty then Left(Invalid("the cohort names no questions"))
      else if c.arms.isEmpty then Left(Invalid("the cohort names no arms"))
      else if c.window.isEmpty then Left(Invalid("the cohort has no forecast window"))
      else if c.budget.isEmpty then Left(Invalid("the cohort has no budget"))
      else
        val early =
          if c.standing == Standing.Development then None
          else c.questions.find(q => closes.get(q).exists(_.isBefore(c.window.get.to)))
        early match
          case Some(q) =>
            Left(Invalid(s"the question '$q' closes before the forecast window ends"))
          case None => Right(List(CohortFrozen(instructions, gathererVersion, scoringMethod, now)))
    }

  private def frozenOnly(held: Option[Cohort])(decide: Cohort => Decision): Decision =
    held match
      case Some(c) if c.frozen => decide(c)
      case _                   => Left(Conflict(IsNotFrozen))

  /**
   * A question's pack is noted once: a gathering that ends after its step was abandoned, and noted
   * failed, changes nothing.
   */
  def notingPack(held: Option[Cohort], q: String, note: PackNote, now: Instant): Decision =
    frozenOnly(held)(c => Right(if c.packs.contains(q) then Nil else List(PackNoted(q, note, now))))

  def noting(held: Option[Cohort], note: String, now: Instant): Decision =
    frozenOnly(held)(_ => Right(List(Noted(note, now))))

  /**
   * Starting the arms. Outside the window, every run not yet made is noted as never to be made, and
   * nothing starts: the caller is told which (FR-007, FR-008).
   */
  def starting(held: Option[Cohort], made: Set[(String, String, Int)], now: Instant): Decision =
    frozenOnly(held) { c =>
      val w = c.window.get
      if w.holds(now) then
        Right(if c.forecastStartedAt.isEmpty then List(ForecastStarted(now)) else Nil)
      else
        val why = if now.isBefore(w.from) then WindowNotBegun else WindowEnded
        Right(notMadeFor(c, c.runs.filterNot(made.contains), why, now))
    }

  private def notMadeFor(c: Cohort, runs: List[(String, String, Int)], why: String, now: Instant) =
    val noted = c.notMade.map(n => (n.question, n.arm, n.repetition)).toSet
    runs.filterNot(noted.contains).map((q, a, r) => RunNotMade(NotMade(q, a, r, why), now))

  /**
   * One run's cost held against the budget before its model is called, so two questions cannot both
   * spend the last of it. Refused, and the run noted as not made, when the budget would be passed
   * or the window has ended.
   */
  def reserving(
      held: Option[Cohort],
      run: (String, String, Int),
      runId: String,
      estimate: BigDecimal,
      now: Instant
  ): Decision =
    frozenOnly(held) { c =>
      val committed = c.spent + c.reserved.values.sum
      if !c.window.get.holds(now) then
        Right(
          notMadeFor(
            c,
            List(run),
            if now.isBefore(c.window.get.from) then WindowNotBegun else WindowEnded,
            now
          )
        )
      else if committed + estimate > c.budget.get || committed >= c.budget.get then
        Right(notMadeFor(c, List(run), BudgetSpent, now))
      else Right(List(Reserved(runId, estimate, now)))
    }

  /** A run that will not be made, and why: its question closed, or has an outcome (FR-030). */
  def notMaking(
      held: Option[Cohort],
      run: (String, String, Int),
      why: String,
      now: Instant
  ): Decision =
    frozenOnly(held)(c => Right(notMadeFor(c, List(run), why, now)))

  /**
   * A reservation settled at what its run cost; a run with no reservation in hand, already settled
   * (by `abandon`, while its step ran on past its timeout), changes nothing, so no cost is counted
   * twice.
   */
  def settling(held: Option[Cohort], runId: String, cost: BigDecimal, now: Instant): Decision =
    frozenOnly(held)(c =>
      Right(if c.reserved.contains(runId) then List(Settled(runId, cost, now)) else Nil)
    )

  def apply(held: Option[Cohort], event: CohortEvent): Option[Cohort] = event match
    case CohortDrafted(id, standing, model, pretended, at) =>
      Some(
        Cohort(
          id,
          standing,
          model,
          pretended,
          at,
          Nil,
          Nil,
          None,
          1,
          None,
          CohortStatus.Drafted,
          None,
          Map.empty,
          None,
          None,
          BigDecimal(0),
          Map.empty,
          Nil,
          Map.empty,
          None,
          Nil
        )
      )
    case other => held.map(c => step(c, other))

  private def step(c: Cohort, event: CohortEvent): Cohort = event match
    case _: CohortDrafted       => c
    case QuestionAdded(q, _, _) => c.copy(questions = c.questions :+ q)
    case QuestionRemoved(q, _)  => c.copy(questions = c.questions.filterNot(_ == q))
    case ArmAdded(a, _)         => c.copy(arms = c.arms :+ a)
    case ArmRemoved(a, _)       => c.copy(arms = c.arms.filterNot(_ == a))
    case ModelSet(m, _)         => c.copy(model = m)
    case WindowSet(w, _)        => c.copy(window = Some(w))
    case RepetitionsSet(n, _)   => c.copy(repetitions = n)
    case BudgetSet(b, _)        => c.copy(budget = Some(b))
    case CohortFrozen(i, g, s, at) =>
      c.copy(
        status = CohortStatus.Frozen,
        frozenAt = Some(at),
        instructions = i,
        gathererVersion = Some(g),
        scoringMethod = Some(s)
      )
    case PackNoted(q, note, _)      => c.copy(packs = c.packs.updated(q, note))
    case ForecastStarted(at)        => c.copy(forecastStartedAt = Some(at))
    case Reserved(run, estimate, _) => c.copy(reserved = c.reserved.updated(run, estimate))
    case Settled(run, cost, _)      => c.copy(reserved = c.reserved - run, spent = c.spent + cost)
    case RunNotMade(n, _)           => c.copy(notMade = c.notMade :+ n)
    case Noted(note, _)             => c.copy(notes = c.notes :+ note)
