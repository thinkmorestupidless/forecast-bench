package forecastbench.venues

import forecastbench.domain.{OutcomeValue, VenueReading}

import java.time.Instant

/**
 * A venue questions are imported from and outcomes read from (contracts/ports.md). Never called
 * from an entity: from an endpoint, a workflow step or the outcome reader.
 */
trait Venue:
  def name: String
  def open(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]]
  def resolved(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]]
  def one(venueId: String): Either[VenueUnreadable, Option[VenueQuestion]]

final case class VenueFilter(
    categories: List[String] = Nil,
    closesAfter: Option[Instant] = None,
    closesBefore: Option[Instant] = None,
    leastVolume: Option[BigDecimal] = None,
    limit: Int = 500
)

final case class VenueQuestion(
    venueId: String,
    eventGroup: String,
    statement: String,
    criteria: Option[String],
    closesAt: Instant,
    category: Option[String],
    outcomes: List[String],
    probability: Option[VenueReading],
    result: Option[String],
    outcome: Option[OutcomeValue],
    disputed: Boolean,
    resolvedAt: Option[Instant],
    volume: Option[BigDecimal]
)

final case class VenueUnreadable(venue: String, why: String)
