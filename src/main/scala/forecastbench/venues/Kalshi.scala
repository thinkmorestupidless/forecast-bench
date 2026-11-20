package forecastbench.venues

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.thinkmorestupidless.ankka.core.Codecs
import forecastbench.domain.OutcomeValue

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import scala.collection.concurrent.TrieMap
import scala.util.Try

/**
 * The `Venue` on Kalshi's public market data (research R9), written from replies read on 2026-11-19
 * and 2026-11-20 (`src/test/resources/kalshi/`): no key. An import reads `GET /events` by `status`
 * with its markets nested and a `cursor`, so each market has its event's category without a request
 * of its own, and combinations of other markets are not listed. One market is read from
 * `/markets/{ticker}`, else from the archive of markets settled before the historical cutoff,
 * `/historical/markets/{ticker}`. Binary markets only.
 */
final class Kalshi(
    base: String = Kalshi.Base,
    http: HttpClient = Kalshi.client,
    clock: () => Instant = () => Instant.now()
) extends Venue:
  val name = "kalshi"

  import Kalshi.*

  /** Each event's category, read once, for the markets read one at a time. */
  private val categories = TrieMap.empty[String, Option[String]]

  def open(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    listed("open", filter)(_.outcome.isEmpty)

  /**
   * The settled markets of settled events. A settled event can still hold an active market, and an
   * event whose markets have moved to the archive is listed with none; neither gives a question.
   */
  def resolved(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]] =
    listed("settled", filter)(_.outcome.isDefined)

  def one(venueId: String): Either[VenueUnreadable, Option[VenueQuestion]] =
    val ticker = enc(venueId)
    fetch(s"$base/markets/$ticker").flatMap {
      case Some(body) => decodeOne(body).map(_.flatMap(question))
      case None =>
        fetch(s"$base/historical/markets/$ticker").flatMap {
          case Some(body) => decodeOne(body).map(_.flatMap(question))
          case None       => Right(None)
        }
    }

  /** The markets of the events of a status that the filter and `keep` choose, up to its limit. */
  private def listed(status: String, filter: VenueFilter)(
      keep: VenueQuestion => Boolean
  ): Either[VenueUnreadable, List[VenueQuestion]] =
    def page(
        cursor: Option[String],
        held: List[VenueQuestion],
        pages: Int
    ): Either[VenueUnreadable, List[VenueQuestion]] =
      val url = (List(s"status=$status", "limit=200", "with_nested_markets=true") ++
        cursor.map(c => s"cursor=${enc(c)}")).mkString(s"$base/events?", "&", "")
      fetch(url).flatMap {
        case None => Left(VenueUnreadable(name, "the events route answered not found"))
        case Some(body) =>
          decode[EventsPage](body).flatMap { p =>
            val chosen = for
              e <- p.events
              category = e.category.filter(_.nonEmpty)
              _        = categories.putIfAbsent(e.event_ticker, category)
              m <- e.markets
              q <- Kalshi.toQuestion(m.copy(event_ticker = e.event_ticker), category, clock())
              if keep(q) && chosenBy(filter)(q)
            yield q
            val all  = held ++ chosen
            val more = p.cursor.filter(_.nonEmpty)
            if all.size >= filter.limit || more.isEmpty || pages >= MaxPages then
              Right(all.take(filter.limit))
            else page(more, all, pages + 1)
          }
      }
    page(None, Nil, 1)

  private def chosenBy(filter: VenueFilter)(q: VenueQuestion): Boolean =
    (filter.categories.isEmpty || q.category.exists(filter.categories.contains)) &&
      filter.closesAfter.forall(t => q.closesAt.isAfter(t)) &&
      filter.closesBefore.forall(t => q.closesAt.isBefore(t)) &&
      filter.leastVolume.forall(v => q.volume.exists(_ >= v))

  /** The market as a question, its category from its event; `None` for any but a binary market. */
  private def question(m: Market): Option[VenueQuestion] =
    Kalshi.toQuestion(m, category(m.event_ticker), clock())

  private def category(event: String): Option[String] =
    categories.getOrElseUpdate(
      event,
      fetch(s"$base/events/${enc(event)}").toOption.flatten
        .flatMap(b => Try(readFromString[EventReply](b)).toOption)
        .flatMap(_.event.category.filter(_.nonEmpty))
    )

  /** A reply's body; `None` when Kalshi answers not found. */
  private def fetch(url: String): Either[VenueUnreadable, Option[String]] =
    val request = HttpRequest
      .newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(30))
      .header("Accept", "application/json")
      .GET()
      .build()
    Try(http.send(request, HttpResponse.BodyHandlers.ofString())).toEither.left
      .map(e => VenueUnreadable(name, s"Kalshi could not be reached: ${e.getMessage}"))
      .flatMap { r =>
        if r.statusCode / 100 == 2 then Right(Some(r.body))
        else if r.statusCode == 404 then Right(None)
        else Left(VenueUnreadable(name, s"Kalshi answered ${r.statusCode}"))
      }

  private def decode[A: JsonValueCodec](body: String): Either[VenueUnreadable, A] =
    Try(readFromString[A](body)).toEither.left
      .map(e => VenueUnreadable(name, s"Kalshi's reply could not be read: ${e.getMessage}"))

  private def decodeOne(body: String): Either[VenueUnreadable, Option[Market]] =
    Try(readFromString[One](body)).toEither.left
      .map(e => VenueUnreadable(name, s"Kalshi's reply could not be read: ${e.getMessage}"))
      .map(_.market)

object Kalshi:
  val Base = "https://api.elections.kalshi.com/trade-api/v2"

  /**
   * At most this many pages of two hundred events are read for one import: each takes about a
   * second, and the import is one request to the bench, which answers within twenty.
   */
  val MaxPages = 10

  lazy val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  private def enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)

  /** The fields of a market the bench reads; Kalshi's prices are dollars, as strings. */
  final case class Market(
      ticker: String,
      event_ticker: String = "",
      market_type: String = "",
      title: String = "",
      rules_primary: String = "",
      rules_secondary: String = "",
      close_time: String = "",
      status: String = "",
      result: String = "",
      settlement_value_dollars: Option[String] = None,
      settlement_ts: Option[String] = None,
      yes_bid_dollars: Option[String] = None,
      yes_ask_dollars: Option[String] = None,
      last_price_dollars: Option[String] = None,
      volume_fp: Option[String] = None
  )
  final case class EventWithMarkets(
      event_ticker: String,
      category: Option[String] = None,
      markets: List[Market] = Nil
  )
  final case class EventsPage(events: List[EventWithMarkets] = Nil, cursor: Option[String] = None)
  final case class One(market: Option[Market] = None)
  final case class Event(category: Option[String] = None)
  final case class EventReply(event: Event = Event())

  given JsonValueCodec[EventsPage] = Codecs.make
  given JsonValueCodec[One]        = Codecs.make
  given JsonValueCodec[EventReply] = Codecs.make

  private def dollars(s: Option[String]): Option[Double] =
    s.flatMap(v => Try(v.toDouble).toOption)

  /**
   * A market as a question. A bid of nought and an ask of one are no orders on that side, and a
   * last price with no volume is no trade, so none of them is read as a price. A finalised market's
   * result is yes or no; a settlement value between nought and one is void. A market is disputed
   * while its status is `disputed` or `amended`.
   */
  def toQuestion(m: Market, category: Option[String], now: Instant): Option[VenueQuestion] =
    for
      _      <- Option.when(m.market_type == "binary")(())
      closes <- Try(Instant.parse(m.close_time)).toOption
    yield
      val traded = dollars(m.volume_fp).exists(_ > 0)
      val bid    = dollars(m.yes_bid_dollars).filter(_ > 0.0)
      val ask    = dollars(m.yes_ask_dollars).filter(_ < 1.0)
      val last   = dollars(m.last_price_dollars).filter(_ => traded)
      val settled =
        Set("finalized", "settled", "disputed", "amended").contains(m.status) && m.result.nonEmpty
      val value = dollars(m.settlement_value_dollars)
      val outcome =
        if !settled then None
        else if value.exists(v => v > 0.0 && v < 1.0) then Some(OutcomeValue.Void)
        else OutcomeValue.parse(m.result).orElse(Some(OutcomeValue.Void))
      val rules = List(m.rules_primary, m.rules_secondary).map(_.trim).filter(_.nonEmpty)
      VenueQuestion(
        venueId = m.ticker,
        eventGroup = if m.event_ticker.isEmpty then m.ticker else m.event_ticker,
        statement = m.title,
        criteria = Option.when(rules.nonEmpty)(rules.mkString("\n\n")),
        closesAt = closes,
        category = category,
        outcomes = List("Yes", "No"),
        probability = if settled then None else VenueProbability.read(bid, ask, last, now),
        result = Option.when(m.result.nonEmpty)(m.result),
        outcome = outcome,
        disputed = m.status == "disputed" || m.status == "amended",
        resolvedAt = m.settlement_ts.flatMap(t => Try(Instant.parse(t)).toOption),
        volume = dollars(m.volume_fp).map(BigDecimal(_))
      )
