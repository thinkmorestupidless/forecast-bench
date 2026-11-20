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

  /** The venues' names, as a model could be given them. */
  val VenueNames: List[String] = List("Kalshi", "Polymarket")

  private val venueAddress =
    ("""(?i)(?:https?://)?(?:[a-z0-9-]+\.)*(?:""" +
      Pack.VenueHosts.map(java.util.regex.Pattern.quote).mkString("|") +
      """)(?:[/?#][^\s)\]>"']*)?""").r
  private val venueName =
    // A name and any word it begins: "KalshiEX", Kalshi's legal entity, is the venue's name too.
    ("""(?i)\b(?:""" + VenueNames.map(java.util.regex.Pattern.quote).mkString("|") + """)\w*""").r

  /**
   * A text with every address on a venue's host left out and every venue's name read as "the
   * venue".
   */
  def blind(text: String): String =
    venueName.replaceAllIn(venueAddress.replaceAllIn(text, ""), "the venue")

  /**
   * The block every model arm's user text begins with, and the gatherer's and the appraisal's.
   * Nothing of the venue is in it: a venue's rules often name it and link to its pages, so the
   * statement and the criteria are blinded here, where every model text starts, which covers
   * questions already held.
   */
  def block(q: QuestionGiven): String =
    s"""Question: ${blind(q.statement)}
       |Resolution criteria: ${blind(q.criteria)}
       |Closes: ${when(q.closesAt)}
       |Now: ${when(q.now)}""".stripMargin
