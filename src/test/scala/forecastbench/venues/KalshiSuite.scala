package forecastbench.venues

import com.sun.net.httpserver.HttpServer
import forecastbench.domain.OutcomeValue

import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The Kalshi venue against replies recorded from Kalshi (`src/test/resources/kalshi/`). */
class KalshiSuite extends munit.FunSuite:

  private def recorded(file: String): String =
    String(getClass.getResourceAsStream(s"/kalshi/$file").readAllBytes(), "UTF-8")

  private val Fed       = "KXFEDDECISION-27JAN-H0"
  private val Football  = "KXNFL3QBTTS-26NOV22TENDAL-Y"
  private val Finalized = "KXNCAAWBTOTAL-26NOV19FORDUKE-166"
  private val Archived  = "KXMVECROSSCATEGORY-S2026D5F1AB3B371-F911198ED54"
  private val now       = Instant.parse("2026-11-19T19:00:00Z")

  /** A loopback Kalshi serving the recorded replies; every path asked, in order. */
  private val kalshi = FunFixture[(Kalshi, ConcurrentLinkedQueue[String])](
    setup = _ =>
      val asked  = ConcurrentLinkedQueue[String]()
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext(
        "/",
        ex =>
          val uri = ex.getRequestURI.toString
          asked.add(uri)
          val path = ex.getRequestURI.getPath
          val body = path match
            case "/markets" if uri.contains("cursor=recorded-cursor") =>
              Some(recorded("last-page.json"))
            case "/markets" if uri.contains("status=open")  => Some(recorded("open.json"))
            case "/markets"                                 => Some(recorded("last-page.json"))
            case "/historical/markets"                      => Some(recorded("last-page.json"))
            case p if p.startsWith("/events/KXFEDDECISION") => Some(recorded("event.json"))
            case s"/markets/$t" if t == Finalized           => Some(recorded("finalized.json"))
            case s"/historical/markets/$t" if t == Archived => Some(recorded("historical.json"))
            case _                                          => None
          val (status, bytes) = body match
            case Some(b) => (200, b.getBytes("UTF-8"))
            case None =>
              (404, """{"error":{"code":"not_found","message":"not found"}}""".getBytes("UTF-8"))
          ex.getResponseHeaders.add("Content-Type", "application/json")
          ex.sendResponseHeaders(status, bytes.length.toLong)
          ex.getResponseBody.write(bytes)
          ex.close()
      )
      server.start()
      sys.addShutdownHook(server.stop(0)): Unit
      (Kalshi(s"http://127.0.0.1:${server.getAddress.getPort}", clock = () => now), asked)
    ,
    teardown = _ => ()
  )

  kalshi.test("an open import reads binary markets only, with their event's category and price") {
    (venue, asked) =>
      val open = venue.open(VenueFilter()).toOption.get
      assertEquals(open.map(_.venueId), List(Fed, Football))
      val fed = open.head
      assertEquals(
        fed.statement,
        "Will the Federal Reserve Hike rates by 0bps at their January 2027 meeting?"
      )
      assertEquals(fed.eventGroup, "KXFEDDECISION-27JAN")
      assertEquals(fed.category, Some("Economics"))
      assertEquals(fed.closesAt, Instant.parse("2027-01-27T18:59:00Z"))
      assertEquals(fed.outcomes, List("Yes", "No"))
      assert(fed.criteria.exists(_.nonEmpty))
      assertEquals(fed.outcome, None)
      val p = fed.probability.get
      assertEquals((p.probability, p.rule), (0.395, "mid"))
      assert(fed.volume.exists(_ > 0))
      // The second page is asked for by the cursor, and each event once.
      assert(asked.asScala.exists(_.contains("cursor=recorded-cursor")))
      assert(
        asked.asScala.forall(u =>
          !u.startsWith("/markets") || u.contains("mve_filter=exclude") || !u.contains("?")
        )
      )
      assertEquals(asked.asScala.count(_.startsWith("/events/KXFEDDECISION-27JAN")), 1)
  }

  kalshi.test("an untraded market with a wide spread gives no probability") { (venue, _) =>
    val football = venue.open(VenueFilter()).toOption.get.find(_.venueId == Football).get
    assertEquals(football.probability, None)
  }

  kalshi.test("a filter by category and by volume is applied to what Kalshi lists") { (venue, _) =>
    assertEquals(
      venue.open(VenueFilter(categories = List("Economics"))).toOption.get.map(_.venueId),
      List(Fed)
    )
    assertEquals(
      venue.open(VenueFilter(leastVolume = Some(BigDecimal(1)))).toOption.get.map(_.venueId),
      List(Fed)
    )
    assertEquals(venue.open(VenueFilter(limit = 1)).toOption.get.size, 1)
  }

  kalshi.test("a finalised market is read with its result and when it settled") { (venue, _) =>
    val q = venue.one(Finalized).toOption.flatten.get
    assertEquals(q.outcome, Some(OutcomeValue.No))
    assertEquals(q.result, Some("no"))
    assertEquals(q.disputed, false)
    assertEquals(q.resolvedAt, Some(Instant.parse("2026-11-19T18:02:19.149186Z")))
    assertEquals(q.probability, None)
  }

  kalshi.test("a market settled before the cutoff is read from the historical archive") {
    (venue, asked) =>
      val q = venue.one(Archived).toOption.flatten.get
      assertEquals(q.outcome, Some(OutcomeValue.No))
      // The live route first, then the archive; then the event, for the category.
      val markets = asked.asScala.toList.filterNot(_.startsWith("/events/"))
      assertEquals(
        markets.takeRight(2),
        List(s"/markets/$Archived", s"/historical/markets/$Archived")
      )
  }

  kalshi.test("a market Kalshi does not hold is none") { (venue, _) =>
    assertEquals(venue.one("NOT-A-MARKET"), Right(None))
  }

  kalshi.test("a venue that cannot be reached is unreadable") { (_, _) =>
    val down = Kalshi("http://127.0.0.1:1", clock = () => now)
    assert(down.open(VenueFilter()).isLeft)
    assert(down.one(Fed).isLeft)
  }

  private val finalized = Kalshi.Market(
    ticker = "T",
    event_ticker = "E",
    market_type = "binary",
    title = "t",
    close_time = "2026-11-19T17:59:49Z",
    status = "finalized",
    result = "yes",
    settlement_value_dollars = Some("1.0000")
  )

  test("a settlement value between nought and one is void"):
    val q =
      Kalshi.toQuestion(finalized.copy(settlement_value_dollars = Some("0.5000")), None, now).get
    assertEquals(q.outcome, Some(OutcomeValue.Void))

  test("a market is disputed while its status is disputed or amended, and keeps its result"):
    List("disputed", "amended").foreach { status =>
      val q = Kalshi.toQuestion(finalized.copy(status = status), None, now).get
      assertEquals((q.disputed, q.outcome), (true, Some(OutcomeValue.Yes)), status)
    }

  test("a scalar market is not a question"):
    assertEquals(Kalshi.toQuestion(finalized.copy(market_type = "scalar"), None, now), None)

  test("an active market has no outcome though Kalshi gives it an empty result"):
    val q = Kalshi.toQuestion(finalized.copy(status = "active", result = ""), None, now).get
    assertEquals((q.outcome, q.result), (None, None))
