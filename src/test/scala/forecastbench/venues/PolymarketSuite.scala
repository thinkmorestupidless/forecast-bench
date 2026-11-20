package forecastbench.venues

import com.sun.net.httpserver.HttpServer
import forecastbench.domain.OutcomeValue

import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/** The Polymarket venue against replies recorded from Gamma (`src/test/resources/polymarket/`). */
class PolymarketSuite extends munit.FunSuite:

  private def recorded(file: String): String =
    String(getClass.getResourceAsStream(s"/polymarket/$file").readAllBytes(), "UTF-8")

  private val now = Instant.parse("2026-11-19T19:00:00Z")

  private val gamma = FunFixture[(Polymarket, ConcurrentLinkedQueue[String])](
    setup = _ =>
      val asked  = ConcurrentLinkedQueue[String]()
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext(
        "/",
        ex =>
          val uri = ex.getRequestURI.toString
          asked.add(uri)
          val body = ex.getRequestURI.getPath match
            case "/markets/keyset" if uri.contains("after_cursor=") =>
              Some(recorded("last-page.json"))
            case "/markets/keyset" if uri.contains("closed=false") => Some(recorded("open.json"))
            case "/markets/keyset"          => Some(recorded("last-page.json"))
            case "/markets/562793"          => Some(recorded("resolved-yes.json"))
            case "/markets/12"              => Some(recorded("resolved-void.json"))
            case "/markets/562793-disputed" => Some(recorded("disputed.json"))
            case _                          => None
          val (status, bytes) = body match
            case Some(b) => (200, b.getBytes("UTF-8"))
            case None =>
              (404, """{"type":"not found error","error":"id not found"}""".getBytes("UTF-8"))
          ex.getResponseHeaders.add("Content-Type", "application/json")
          ex.sendResponseHeaders(status, bytes.length.toLong)
          ex.getResponseBody.write(bytes)
          ex.close()
      )
      server.start()
      sys.addShutdownHook(server.stop(0)): Unit
      (Polymarket(s"http://127.0.0.1:${server.getAddress.getPort}", clock = () => now), asked)
    ,
    teardown = _ => ()
  )

  gamma.test("an open import reads Yes and No markets only, with the first tag and the event") {
    (venue, asked) =>
      val open = venue.open(VenueFilter()).toOption.get
      assertEquals(open.map(_.venueId), List("559651"))
      val xi = open.head
      assertEquals(xi.statement, "Xi Jinping out before 2027?")
      assertEquals(xi.eventGroup, "30828")
      assertEquals(xi.category, Some("HFC"))
      assertEquals(xi.closesAt, Instant.parse("2027-01-01T04:59:00Z"))
      assert(xi.criteria.exists(_.startsWith("This market will resolve to \"Yes\"")))
      val p = xi.probability.get
      assertEquals((p.probability, p.rule), (0.015, "mid"))
      assertEquals(xi.outcome, None)
      assert(asked.asScala.exists(_.contains("include_tag=true")))
      assert(asked.asScala.exists(_.contains("after_cursor=recorded-cursor")))
  }

  gamma.test("a closed market whose prices are one and nought resolved yes, when it closed") {
    (venue, _) =>
      val q = venue.one("562793").toOption.flatten.get
      assertEquals(q.outcome, Some(OutcomeValue.Yes))
      assertEquals(q.disputed, false)
      assertEquals(q.resolvedAt, Some(Instant.parse("2026-11-04T10:53:59.942479Z")))
      assertEquals(q.probability, None)
      assertEquals(q.category, Some("Senate"))
  }

  gamma.test("a closed market with any other prices is void") { (venue, _) =>
    val q = venue.one("12").toOption.flatten.get
    assertEquals(q.outcome, Some(OutcomeValue.Void))
    assertEquals(q.resolvedAt, Some(Instant.parse("2020-11-02T16:31:01Z")))
  }

  gamma.test("a market is disputed while its last resolution status is disputed") { (venue, _) =>
    assertEquals(venue.one("562793-disputed").toOption.flatten.map(_.disputed), Some(true))
  }

  gamma.test("a market Gamma does not hold is none") { (venue, _) =>
    assertEquals(venue.one("999999999"), Right(None))
  }

  gamma.test("a filter by category is applied to what Gamma lists") { (venue, _) =>
    assertEquals(venue.open(VenueFilter(categories = List("Politics"))).toOption.get, Nil)
    assertEquals(venue.open(VenueFilter(categories = List("HFC"))).toOption.get.size, 1)
  }

  test("a venue that cannot be reached is unreadable"):
    val down = Polymarket("http://127.0.0.1:1", clock = () => now)
    assert(down.open(VenueFilter()).isLeft)
    assert(down.one("1").isLeft)
