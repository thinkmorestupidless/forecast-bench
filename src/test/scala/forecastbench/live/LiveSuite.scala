package forecastbench.live

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.TimerRuntime
import com.thinkmorestupidless.ankka.sdk.BucketExposure
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import forecastbench.BenchService
import forecastbench.application.{Bench, Edges, Models, Settings}
import forecastbench.domain.Clock
import forecastbench.gather.Exa
import forecastbench.reasoning.Reasoning
import forecastbench.venues.{Kalshi, Polymarket}

import java.net.{InetSocketAddress, Socket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.concurrent.duration.*
import scala.util.Try

/**
 * The bench against its real edges: Kalshi, Exa, the configured model and the reasoning service of
 * `just reasoning-up`. Three development questions from Kalshi, forecast by all five arms; the
 * runs, the equality of the two shapes' content, the graph's belief in the reasoning service, and
 * the report are checked.
 *
 * It spends money, so it runs only when asked: `just live`, which sets `FORECAST_LIVE=1`, and only
 * with `ANTHROPIC_API_KEY`, `EXA_API_KEY` and the reasoning service on :9100. It installs the real
 * edges, so it runs alone in its JVM, never beside the feature suites' scripted bench.
 */
class LiveSuite extends munit.FunSuite:

  override val munitTimeout = 30.minutes

  private val ReasoningUrl = "http://localhost:9100"

  private def reachable(host: String, port: Int): Boolean =
    Try {
      val s = Socket()
      try s.connect(InetSocketAddress(host, port), 2000)
      finally s.close()
    }.isSuccess

  private val missing: List[String] =
    List(
      Option.unless(sys.env.get("FORECAST_LIVE").contains("1"))("FORECAST_LIVE=1"),
      Option.unless(sys.env.get("ANTHROPIC_API_KEY").exists(_.nonEmpty))("ANTHROPIC_API_KEY"),
      Option.unless(sys.env.get("EXA_API_KEY").exists(_.nonEmpty))("EXA_API_KEY"),
      Option.unless(reachable("localhost", 9100))("the reasoning service on :9100")
    ).flatten

  private lazy val started: (AnkkaTestKit, String) =
    val settings = Settings.load()
    Bench.install(
      Edges(
        Clock.system,
        settings,
        Map("kalshi" -> Kalshi(), "polymarket" -> Polymarket()),
        Models.fromSettings(settings),
        Exa.fromEnv(settings.limits.excerpt)
      )
    )
    val server = HttpServer.at("127.0.0.1", 0)(BenchService.endpoints*)
    val kit = AnkkaTestKit.start(
      BenchService.components,
      BenchService.extensions(TimerRuntime(), server),
      localServices = Map("reasoning" -> ReasoningUrl)
    )
    kit.declareBucket("given", BucketExposure.Private)
    sys.addShutdownHook(kit.stop())
    (kit, s"http://127.0.0.1:${server.boundPort.get}")

  private val http =
    HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()

  private def call(method: String, url: String, body: Option[String] = None): ujson.Value =
    val r = http.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .timeout(java.time.Duration.ofMinutes(5))
        .header("Content-Type", "application/json")
        .method(
          method,
          body.fold(HttpRequest.BodyPublishers.noBody())(HttpRequest.BodyPublishers.ofString)
        )
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assert(r.statusCode / 100 == 2, s"$method $url answered ${r.statusCode}: ${r.body}")
    ujson.read(r.body)

  private def bench(method: String, path: String, body: String = ""): ujson.Value =
    call(method, started._2 + path, Option.when(body.nonEmpty)(body))

  private def eventually[A](what: String, within: FiniteDuration)(check: => Option[A]): A =
    val deadline = System.nanoTime() + within.toNanos
    var held     = Try(check).toOption.flatten
    while held.isEmpty && System.nanoTime() < deadline do
      Thread.sleep(2000)
      held = Try(check).toOption.flatten
    held.getOrElse(fail(s"waited $within for $what"))

  test(
    "three development questions from Kalshi, forecast by all five arms against the real edges"
  ) {
    assume(missing.isEmpty, s"the live suite needs ${missing.mkString(", ")}")

    val imported = bench(
      "POST",
      "/venues/kalshi/import",
      """{"standing":"development","categories":["Economics"],"limit":3,"onePerEvent":true}"""
    )
    assert(imported("held").num.toInt + imported("already").num.toInt >= 3, imported.toString)
    val questions = bench("GET", "/questions?venue=kalshi").arr.toList
      .filter(_("standing").str == "development")
      .take(3)
    assertEquals(questions.size, 3)
    val ids = questions.map(_("id").str)
    // A week before the first of them closed, so every one was still open then.
    val pretended =
      questions.map(q => Instant.parse(q("closesAt").str)).min.minus(Duration.ofDays(7))

    val cohort = s"live-${System.currentTimeMillis() / 1000}"
    val model  = Settings.load().models.keys.head
    val now    = Instant.now()
    bench(
      "POST",
      "/cohorts",
      s"""{"id":"$cohort","standing":"development","model":"$model","pretendedTime":"$pretended"}"""
    )
    bench("POST", s"/cohorts/$cohort/questions", ujson.write(ujson.Obj("questions" -> ids)))
    val arms = List("venue-price", "model-alone", "evidence", "graph", "prose")
    bench("POST", s"/cohorts/$cohort/arms", ujson.write(ujson.Obj("arms" -> arms)))
    bench(
      "PUT",
      s"/cohorts/$cohort",
      s"""{"window":{"from":"$now","to":"${now.plus(
          Duration.ofHours(2)
        )}"},"repetitions":1,"budget":5}"""
    )
    bench("POST", s"/cohorts/$cohort/freeze", "{}")
    bench("POST", s"/cohorts/$cohort/forecast")

    val runs = eventually("every run to be recorded", 25.minutes) {
      Some(bench("GET", s"/runs?cohort=$cohort").arr.toList).filter(_.size == ids.size * arms.size)
    }.map(_("run"))
    val endings = runs.map(r => (r("question").str, r("arm").str) -> r("ending"))
    // No graph or prose run failed for want of the same content, nor for the reasoning service.
    endings.foreach { case ((q, arm), ending) =>
      if ending("type").str == "Failed" then
        assert(
          !Set("graph", "prose").contains(arm) ||
            !ending.toString.contains("did not hold the same content"),
          s"$q $arm: $ending"
        )
    }
    assert(endings.count(_._2("type").str == "Forecast") >= ids.size, endings.toString)

    // The graph's belief is in the reasoning service for each graph run with a forecast.
    runs.filter(r => r("arm").str == "graph" && r("ending")("type").str == "Forecast").foreach {
      r =>
        val belief = Reasoning.beliefId(Reasoning.questionId(cohort, r("question").str), 1)
        val held   = call("GET", s"$ReasoningUrl/beliefs/revisions/$belief")
        assertEqualsDouble(held("probability").num, r("ending")("probability").num, 1e-9)
    }

    val report = bench("GET", s"/cohorts/$cohort/report")
    assertEquals(report("marking").str, "development: the model may know these outcomes")
    assertEquals(report("arms").arr.map(_("arm").str).toSet, arms.toSet)
    println(ujson.write(report("arms"), indent = 2))
  }
