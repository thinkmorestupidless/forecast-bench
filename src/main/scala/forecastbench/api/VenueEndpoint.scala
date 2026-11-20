package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, CommandError, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.{Bench, Domains, DomainsEntity, QuestionEntity}
import forecastbench.domain.*
import forecastbench.venues.*

import java.time.Instant

/** Questions imported from a venue, and the experimenter's names for its categories. */
final class VenueEndpoint(client: ComponentClient) extends HttpEndpoint("/venues"):
  import VenueEndpoint.{*, given}

  val acl: Acl = Acl.allowCallers(Callers.self)

  postBody("/{venue}/import") { (name: String, body: ImportRequest) =>
    val venue = venueNamed(name)
    val standing = body.standing.fold(Standing.Prospective)(s =>
      Standing
        .parse(s)
        .getOrElse(throw HttpProblem(422, s"'$s' is neither prospective nor development"))
    )
    val filter = VenueFilter(
      categories = body.categories.getOrElse(Nil),
      closesAfter = body.closesAfter,
      closesBefore = body.closesBefore,
      leastVolume = body.leastVolume,
      // The venue is asked for enough to choose from: the limit counts the questions held, after
      // those left out and, with `onePerEvent`, after an event's markets are made one.
      limit = math.max(body.limit.getOrElse(VenueEndpoint.Limit), VenueEndpoint.Limit)
    )
    val read =
      if standing == Standing.Development then venue.resolved(filter) else venue.open(filter)
    val listed = read.fold(_ => throw HttpProblem(503, "the venue could not be read"), identity)
    val (kept, leftOut) = VenueEndpoint.screen(listed)
    val chosen          = kept.filter(q => filter.leastVolume.forall(v => q.volume.exists(_ >= v)))
    val one =
      (if body.onePerEvent.getOrElse(true) then VenueEndpoint.onePerEvent(chosen) else chosen)
        .take(body.limit.getOrElse(VenueEndpoint.Limit))
    val domains = client.forKeyValueEntity(EntityId(name)).call(DomainsEntity.get).invoke()
    val now     = Bench.edges.clock.now
    val results = one.map { q =>
      val id = Ids.question(name, q.venueId)
      try
        val r = client
          .forEventSourcedEntity(EntityId(id))
          .call(QuestionEntity.importQuestion)
          .invoke(VenueEndpoint.toImport(name, q, domains, standing, now))
        Right(r.held)
      catch case e: CommandError => Left(LeftOut(q.venueId, e.message))
    }
    ImportResult(
      held = results.count(_ == Right(true)),
      already = results.count(_ == Right(false)),
      leftOut = leftOut ++ results.collect { case Left(l) => l }
    )
  }

  putBody("/{venue}/domains") { (name: String, names: Map[String, String]) =>
    venueNamed(name): Unit
    client.forKeyValueEntity(EntityId(name)).call(DomainsEntity.set).invoke(Domains(names)).names
  }

  get("/{venue}/domains") { (name: String) =>
    venueNamed(name): Unit
    client.forKeyValueEntity(EntityId(name)).call(DomainsEntity.get).invoke().names
  }

  private def venueNamed(name: String): Venue =
    Bench.edges.venues.getOrElse(name, throw HttpProblem.notFound(s"no venue '$name'"))

object VenueEndpoint:

  final case class ImportRequest(
      standing: Option[String],
      categories: Option[List[String]],
      closesAfter: Option[Instant],
      closesBefore: Option[Instant],
      leastVolume: Option[BigDecimal],
      onePerEvent: Option[Boolean],
      limit: Option[Int]
  )
  final case class LeftOut(venueId: String, reason: String)
  final case class ImportResult(held: Int, already: Int, leftOut: List[LeftOut])

  given JsonValueCodec[ImportRequest]       = Codecs.make
  given JsonValueCodec[ImportResult]        = Codecs.make
  given JsonValueCodec[Map[String, String]] = Codecs.make

  /** Questions the bench cannot hold, left out with the reason (FR-003). */
  def screen(listed: List[VenueQuestion]): (List[VenueQuestion], List[LeftOut]) =
    val (kept, out) = listed.partition(q => reason(q).isEmpty)
    (kept, out.map(q => LeftOut(q.venueId, reason(q).get)))

  private def reason(q: VenueQuestion): Option[String] =
    if q.outcomes.size != 2 then Some("more than two outcomes")
    else if q.criteria.forall(_.trim.isEmpty) then Some("no resolution criteria")
    else None

  /**
   * Of an event's markets, the one whose probability is nearest a half (research R9), so a ladder
   * of strikes is one question; with no probability to go by, the first by its id.
   */
  /**
   * The most questions an import holds unless it says fewer, and the least it reads to choose from.
   */
  val Limit = 500

  def onePerEvent(questions: List[VenueQuestion]): List[VenueQuestion] =
    questions
      .groupBy(_.eventGroup)
      .values
      .map(
        _.sortBy(q => (q.probability.fold(1.0)(r => math.abs(r.probability - 0.5)), q.venueId)).head
      )
      .toList
      .sortBy(_.venueId)

  def toImport(
      venue: String,
      q: VenueQuestion,
      domains: Domains,
      standing: Standing,
      now: Instant
  ): QuestionImport =
    QuestionImport(
      venue = venue,
      venueId = q.venueId,
      eventGroup = q.eventGroup,
      statement = q.statement,
      criteria = q.criteria,
      closesAt = q.closesAt,
      category = q.category,
      outcomes = q.outcomes.size,
      domain = domains.of(q.category),
      standing = standing,
      outcome = q.outcome.map(o => OutcomeReading(o, q.disputed, q.resolvedAt)),
      now = now
    )
