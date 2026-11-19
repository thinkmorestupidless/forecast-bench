package forecastbench.support

import forecastbench.domain.OutcomeValue
import forecastbench.venues.*

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/**
 * A venue the steps fill: open and resolved questions, results, disputes, cancellations, and
 * "cannot be read". Every scenario's questions live in one venue, kept apart by their ids.
 */
final class ScriptedVenue(val name: String) extends Venue:

  private val questions            = ConcurrentHashMap[String, VenueQuestion]()
  @volatile private var unreadable = false

  def list(q: VenueQuestion): Unit = questions.put(q.venueId, q): Unit

  def update(venueId: String)(change: VenueQuestion => VenueQuestion): Unit =
    questions.computeIfPresent(venueId, (_, q) => change(q)): Unit

  def resolve(
      venueId: String,
      outcome: OutcomeValue,
      at: Instant,
      disputed: Boolean = false
  ): Unit =
    update(venueId)(q =>
      q.copy(
        outcome = Some(outcome),
        result = Some(outcome.word),
        disputed = disputed,
        resolvedAt = Some(at)
      )
    )

  def cannotBeRead(yes: Boolean): Unit = unreadable = yes

  def held(venueId: String): Option[VenueQuestion] = Option(questions.get(venueId))

  /** Only the questions a scenario listed, by the prefix of their ids. */
  @volatile var only: Option[String] = None

  private def chosen(filter: VenueFilter, resolved: Boolean) =
    questions.values.asScala.toList
      .filter(q => q.outcome.isDefined == resolved)
      .filter(q => only.forall(q.venueId.startsWith))
      .filter(q => filter.categories.isEmpty || q.category.exists(filter.categories.contains))
      .filter(q => filter.closesAfter.forall(q.closesAt.isAfter))
      .filter(q => filter.closesBefore.forall(q.closesAt.isBefore))
      .sortBy(_.venueId)
      .take(filter.limit)

  def open(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    if unreadable then Left(VenueUnreadable(name, "scripted")) else Right(chosen(filter, false))

  def resolved(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    if unreadable then Left(VenueUnreadable(name, "scripted")) else Right(chosen(filter, true))

  def one(venueId: String): Either[VenueUnreadable, Option[VenueQuestion]] =
    if unreadable then Left(VenueUnreadable(name, "scripted")) else Right(held(venueId))

object ScriptedVenue:

  /** An open binary question closing in four weeks, with criteria and a probability of 0.5. */
  def question(
      venueId: String,
      statement: String,
      closesAt: Instant,
      category: Option[String] = Some("Economics"),
      eventGroup: Option[String] = None
  ): VenueQuestion =
    VenueQuestion(
      venueId = venueId,
      eventGroup = eventGroup.getOrElse(venueId),
      statement = statement,
      criteria = Some(s"Resolves yes if: $statement"),
      closesAt = closesAt,
      category = category,
      outcomes = List("Yes", "No"),
      probability = VenueProbability
        .read(Some(0.48), Some(0.52), Some(0.5), closesAt.minusSeconds(86400L * 28)),
      result = None,
      outcome = None,
      disputed = false,
      resolvedAt = None,
      volume = Some(BigDecimal(10000))
    )
