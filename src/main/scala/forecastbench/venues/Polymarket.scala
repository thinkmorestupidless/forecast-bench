package forecastbench.venues

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.thinkmorestupidless.ankka.core.Codecs
import forecastbench.domain.OutcomeValue

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, OffsetDateTime}
import scala.util.Try

/**
 * The `Venue` on Polymarket's Gamma (research R9), written from replies read on 2026-11-19
 * (`src/test/resources/polymarket/`): no key; `GET /markets/keyset` by `closed`, its cursor sent
 * back as `after_cursor`, the tags asked for with `include_tag=true`. Binary only: a market whose
 * JSON-encoded `outcomes` are exactly Yes and No.
 */
final class Polymarket(
    base: String = Polymarket.Base,
    http: HttpClient = Polymarket.client,
    clock: () => Instant = () => Instant.now()
) extends Venue:
  val name = "polymarket"

  import Polymarket.*

  def open(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    listed(closed = false, filter)

  def resolved(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    listed(closed = true, filter).map(_.filter(_.outcome.isDefined))

  def one(venueId: String): Either[VenueUnreadable, Option[VenueQuestion]] =
    fetch(s"$base/markets/${enc(venueId)}?include_tag=true").flatMap {
      case None       => Right(None)
      case Some(body) => decode[Market](body).map(m => toQuestion(m, clock()))
    }

  private def listed(
      closed: Boolean,
      filter: VenueFilter
  ): Either[VenueUnreadable, List[VenueQuestion]] =
    val params = List(
      Some(s"closed=$closed"),
      Some("limit=500"),
      Some("include_tag=true"),
      filter.closesAfter.map(t => s"end_date_min=$t"),
      filter.closesBefore.map(t => s"end_date_max=$t"),
      filter.leastVolume.map(v => s"volume_num_min=$v")
    ).flatten
    def page(
        cursor: Option[String],
        held: List[VenueQuestion],
        pages: Int
    ): Either[VenueUnreadable, List[VenueQuestion]] =
      val url = (params ++ cursor.map(c => s"after_cursor=${enc(c)}"))
        .mkString(s"$base/markets/keyset?", "&", "")
      fetch(url).flatMap {
        case None => Left(VenueUnreadable(name, "the keyset route answered not found"))
        case Some(body) =>
          decode[Page](body).flatMap { p =>
            val all  = held ++ p.markets.flatMap(toQuestion(_, clock())).filter(chosenBy(filter))
            val more = p.next_cursor.filter(_.nonEmpty)
            if all.size >= filter.limit || more.isEmpty || p.markets.isEmpty || pages >= MaxPages
            then Right(all.take(filter.limit))
            else page(more, all, pages + 1)
          }
      }
    page(None, Nil, 1)

  private def chosenBy(filter: VenueFilter)(q: VenueQuestion): Boolean =
    (filter.categories.isEmpty || q.category.exists(filter.categories.contains)) &&
      filter.closesAfter.forall(t => q.closesAt.isAfter(t)) &&
      filter.closesBefore.forall(t => q.closesAt.isBefore(t)) &&
      filter.leastVolume.forall(v => q.volume.exists(_ >= v))

  private def fetch(url: String): Either[VenueUnreadable, Option[String]] =
    val request = HttpRequest
      .newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(30))
      .header("Accept", "application/json")
      .GET()
      .build()
    Try(http.send(request, HttpResponse.BodyHandlers.ofString())).toEither.left
      .map(e => VenueUnreadable(name, s"Polymarket could not be reached: ${e.getMessage}"))
      .flatMap { r =>
        if r.statusCode / 100 == 2 then Right(Some(r.body))
        else if r.statusCode == 404 then Right(None)
        else Left(VenueUnreadable(name, s"Polymarket answered ${r.statusCode}"))
      }

  private def decode[A: JsonValueCodec](body: String): Either[VenueUnreadable, A] =
    Try(readFromString[A](body)).toEither.left
      .map(e => VenueUnreadable(name, s"Polymarket's reply could not be read: ${e.getMessage}"))

object Polymarket:
  val Base = "https://gamma-api.polymarket.com"

  /** At most this many pages of five hundred markets are read for one import. */
  val MaxPages = 20

  lazy val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  private def enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)

  final case class Tag(label: String = "")
  final case class EventRef(id: String = "")

  /**
   * The fields of a market the bench reads. `outcomes`, `outcomePrices` and the statuses are JSON
   * in a string.
   */
  final case class Market(
      id: String,
      question: String = "",
      description: String = "",
      outcomes: String = "[]",
      outcomePrices: String = "[]",
      endDate: Option[String] = None,
      closed: Boolean = false,
      closedTime: Option[String] = None,
      umaResolutionStatuses: Option[String] = None,
      bestBid: Option[Double] = None,
      bestAsk: Option[Double] = None,
      lastTradePrice: Option[Double] = None,
      volumeNum: Option[Double] = None,
      events: List[EventRef] = Nil,
      tags: List[Tag] = Nil
  )
  final case class Page(markets: List[Market] = Nil, next_cursor: Option[String] = None)

  given JsonValueCodec[Market]       = Codecs.make
  given JsonValueCodec[Page]         = Codecs.make
  given JsonValueCodec[List[String]] = Codecs.make

  private def strings(json: String): List[String] =
    Try(readFromString[List[String]](json)).getOrElse(Nil)

  /** Gamma writes `closedTime` as `2026-11-04 10:53:59.942479+00`. */
  private val ClosedTime =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]X")

  def instant(text: String): Option[Instant] =
    Try(Instant.parse(text)).toOption
      .orElse(Try(OffsetDateTime.parse(text, ClosedTime).toInstant).toOption)

  /**
   * A market as a question; `None` unless its outcomes are Yes and No and it has an end date. A
   * closed market's result is read from `outcomePrices`: `["1","0"]` yes, `["0","1"]` no, anything
   * else void. It is disputed while the last of its resolution statuses is `disputed`. The category
   * is the first tag.
   */
  def toQuestion(m: Market, now: Instant): Option[VenueQuestion] =
    for
      _      <- Option.when(strings(m.outcomes) == List("Yes", "No"))(())
      closes <- m.endDate.flatMap(instant)
    yield
      val prices = strings(m.outcomePrices)
      val traded = m.volumeNum.exists(_ > 0)
      val outcome =
        if !m.closed then None
        else
          prices match
            case List("1", "0") => Some(OutcomeValue.Yes)
            case List("0", "1") => Some(OutcomeValue.No)
            case _              => Some(OutcomeValue.Void)
      VenueQuestion(
        venueId = m.id,
        eventGroup = m.events.headOption.map(_.id).filter(_.nonEmpty).getOrElse(m.id),
        statement = m.question,
        criteria = Option(m.description.trim).filter(_.nonEmpty),
        closesAt = closes,
        category = m.tags.headOption.map(_.label).filter(_.nonEmpty),
        outcomes = List("Yes", "No"),
        probability =
          if m.closed then None
          else
            VenueProbability.read(m.bestBid, m.bestAsk, m.lastTradePrice.filter(_ => traded), now)
        ,
        result = Option.when(m.closed)(prices.mkString("[", ",", "]")),
        outcome = outcome,
        disputed = m.umaResolutionStatuses.map(strings).flatMap(_.lastOption).contains("disputed"),
        resolvedAt = m.closedTime.flatMap(instant),
        volume = m.volumeNum.map(BigDecimal(_))
      )
