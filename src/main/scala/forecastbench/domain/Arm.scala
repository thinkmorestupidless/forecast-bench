package forecastbench.domain

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

/** What every model arm is told of a question (contracts/arm.md). */
final case class QuestionGiven(statement: String, criteria: String, closesAt: Instant, now: Instant)

/**
 * What a model is given: the system text (the arm's instructions) and the user text. An arm makes
 * the user text; the step that runs it gives the system text, which is the version of the arm's
 * instructions the cohort fixed at freeze, so `given` answers it empty.
 */
final case class Given(system: String, user: String):
  /** The exact text kept in the bucket and given again to reproduce a run. */
  def text: String = s"$system\n${Given.Rule}\n$user"

object Given:
  val Rule: String = "-" * 72

  def parse(text: String): Option[Given] =
    text.split(s"\n$Rule\n", 2) match
      case Array(system, user) => Some(Given(system, user))
      case _                   => None

final case class Abstain(reason: String)

/**
 * One way of answering a question with a probability. Runs, scores and reports hold an arm by its
 * name only, so an arm added later is a value here and, when it uses a model, a directory of
 * instructions (FR-010, SC-010).
 */
trait Arm:
  def name: String
  def takesEvidence: Boolean
  def usesModel: Boolean

  /** The directory under `instructions/`, when the arm uses a model. */
  def instructions: Option[String]

  def `given`(
      q: QuestionGiven,
      pack: Option[Pack],
      appraisal: Option[AppraisalContent]
  ): Either[Abstain, Given]

object Arm:
  private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

  def when(t: Instant): String = time.format(t)

  /** The block every model arm's user text begins with. Nothing of the venue is in it. */
  def block(q: QuestionGiven): String =
    s"""Question: ${q.statement}
       |Resolution criteria: ${q.criteria}
       |Closes: ${when(q.closesAt)}
       |Now: ${when(q.now)}""".stripMargin
