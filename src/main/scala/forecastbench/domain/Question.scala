package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.time.Instant

/** What a venue says happened: yes, no, or void (cancelled, or settled at neither). */
enum OutcomeValue:
  case Yes, No, Void

  def word: String = toString.toLowerCase

object OutcomeValue:
  given JsonValueCodec[OutcomeValue]            = Words.codec(values.toSeq, _.word)
  def parse(text: String): Option[OutcomeValue] = Words.parse(values.toSeq, _.word, text)

/** Prospective: open at its venue when first imported. Development: already resolved. For ever. */
enum Standing:
  case Prospective, Development

  def word: String = toString.toLowerCase

object Standing:
  given JsonValueCodec[Standing]            = Words.codec(values.toSeq, _.word)
  def parse(text: String): Option[Standing] = Words.parse(values.toSeq, _.word, text)

/** One outcome as read from the venue; a later one revises it and both are kept. */
final case class Outcome(
    seq: Int,
    value: OutcomeValue,
    disputed: Boolean,
    resolvedAt: Option[Instant],
    readAt: Instant,
    revises: Option[Int]
):
  /** Yes or no and not disputed: the only outcome a forecast is scored on. */
  def settled: Boolean = !disputed && value != OutcomeValue.Void

/** What the venue reports of an outcome, before the bench holds it. */
final case class OutcomeReading(value: OutcomeValue, disputed: Boolean, resolvedAt: Option[Instant])

/** A question as an import gives it to the entity. */
final case class QuestionImport(
    venue: String,
    venueId: String,
    eventGroup: String,
    statement: String,
    criteria: Option[String],
    closesAt: Instant,
    category: Option[String],
    outcomes: Int,
    domain: String,
    standing: Standing,
    outcome: Option[OutcomeReading],
    now: Instant
)

final case class Question(
    id: String,
    venue: String,
    venueId: String,
    eventGroup: String,
    statement: String,
    criteria: String,
    closesAt: Instant,
    category: Option[String],
    domain: String,
    standing: Standing,
    importedAt: Instant,
    outcomes: List[Outcome]
):
  def current: Option[Outcome] = outcomes.lastOption
  def waiting: Boolean         = current.isEmpty

enum QuestionEvent:
  case QuestionImported(
      id: String,
      venue: String,
      venueId: String,
      eventGroup: String,
      statement: String,
      criteria: String,
      closesAt: Instant,
      category: Option[String],
      domain: String,
      standing: Standing,
      importedAt: Instant
  )
  case QuestionRefreshed(closesAt: Instant, criteria: String, at: Instant)
  case OutcomeRead(outcome: Outcome)

object Question:
  import QuestionEvent.*

  /**
   * The events of an import. The first holds the question, with its standing for ever; a later one
   * records only a changed close time or criteria, and never a standing. A development question's
   * outcome arrives with its import.
   */
  def importing(held: Option[Question], q: QuestionImport): Either[String, List[QuestionEvent]] =
    val criteria = q.criteria.map(_.trim).filter(_.nonEmpty)
    if q.outcomes != 2 then Left("more than two outcomes")
    else if criteria.isEmpty then Left("no resolution criteria")
    else
      held match
        case None =>
          val imported = QuestionImported(
            Ids.question(q.venue, q.venueId),
            q.venue,
            q.venueId,
            q.eventGroup,
            q.statement,
            criteria.get,
            q.closesAt,
            q.category,
            q.domain,
            q.standing,
            q.now
          )
          val outcome = q.outcome.filter(_ => q.standing == Standing.Development).toList.flatMap {
            r => reading(None, r, q.now)
          }
          Right(imported :: outcome)
        case Some(h) =>
          if h.closesAt != q.closesAt || h.criteria != criteria.get then
            Right(List(QuestionRefreshed(q.closesAt, criteria.get, q.now)))
          else Right(Nil)

  /**
   * An outcome is recorded only when it differs from the current one, naming the one it revises.
   */
  def reading(held: Option[Question], r: OutcomeReading, now: Instant): List[QuestionEvent] =
    val current = held.flatMap(_.current)
    val same    = current.exists(c => c.value == r.value && c.disputed == r.disputed)
    if same then Nil
    else
      List(
        OutcomeRead(
          Outcome(
            seq = current.fold(1)(_.seq + 1),
            value = r.value,
            disputed = r.disputed,
            resolvedAt = r.resolvedAt,
            readAt = now,
            revises = current.map(_.seq)
          )
        )
      )

  def apply(held: Option[Question], event: QuestionEvent): Option[Question] = event match
    case e: QuestionImported =>
      Some(
        Question(
          e.id,
          e.venue,
          e.venueId,
          e.eventGroup,
          e.statement,
          e.criteria,
          e.closesAt,
          e.category,
          e.domain,
          e.standing,
          e.importedAt,
          Nil
        )
      )
    case QuestionRefreshed(closesAt, criteria, _) =>
      held.map(_.copy(closesAt = closesAt, criteria = criteria))
    case OutcomeRead(outcome) => held.map(q => q.copy(outcomes = q.outcomes :+ outcome))
