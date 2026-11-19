package forecastbench.steps

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.TimerRuntime
import com.thinkmorestupidless.ankka.sdk.BucketExposure
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import forecastbench.BenchService
import forecastbench.application.{Bench, Edges, Settings}
import forecastbench.support.TestClock

/**
 * One bench for the test JVM: the service's own components and routes under `AnkkaTestKit`, on a
 * real port, with the edges scripted: a settable clock, and the stand-ins later tasks add for the
 * model, the venues, the search and the reasoning service. The store is the kit's, in memory.
 *
 * Every feature suite shares it; scenarios keep apart by the ids `World` gives them, never by
 * starting a service of their own, which would cost a database and a cluster each.
 */
object ServiceFixture:

  val clock: TestClock = TestClock(TestClock.Start)

  lazy val settings: Settings = Settings.load()

  private lazy val started: (AnkkaTestKit, String) =
    Bench.install(Edges(clock, settings))
    val server = HttpServer.at("127.0.0.1", 0)(BenchService.endpoints*)
    val kit = AnkkaTestKit.start(
      BenchService.components,
      BenchService.extensions(TimerRuntime(), server)
    )
    kit.declareBucket("given", BucketExposure.Private)
    sys.addShutdownHook(kit.stop())
    val port = server.boundPort.getOrElse(throw IllegalStateException("the bench did not bind"))
    (kit, s"http://127.0.0.1:$port")

  def kit: AnkkaTestKit = started._1
  def baseUrl: String   = started._2
