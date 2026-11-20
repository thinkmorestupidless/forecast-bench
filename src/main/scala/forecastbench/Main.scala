package forecastbench

import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{EndpointClients, HttpEndpoint, HttpServer}
import com.thinkmorestupidless.ankka.runtime.{
  Ankka,
  ProjectionRuntime,
  RuntimeExtension,
  TimerRuntime
}
import forecastbench.api.*
import forecastbench.application.*
import forecastbench.application.views.*
import forecastbench.domain.Clock

/**
 * The whole service definition.
 *
 * Registration is explicit, so `components` is the complete inventory of what the bench hosts and
 * `endpoints` the complete list of what it serves; a component left out fails at startup rather
 * than at its first request. The suites start the same two lists under the test kit, with scripted
 * edges installed in place of the real ones.
 */
object BenchService:

  def components: Seq[ComponentDescriptor] = Seq(
    QuestionEntity.descriptor,
    Questions.descriptor,
    DomainsEntity.descriptor,
    CohortEntity.descriptor,
    Cohorts.descriptor,
    RunEntity.descriptor,
    Runs.descriptor,
    QuestionForecast.descriptor,
    CohortForecast.descriptor,
    WatchedEntity.descriptor,
    ReadOutcomes.descriptor,
    QuestionRunsEntity.descriptor,
    ScoreOnRecord.descriptor,
    ScoreOnOutcome.descriptor,
    PackEntity.descriptor,
    Packs.descriptor,
    AppraisalEntity.descriptor,
    Appraisals.descriptor
  )

  def endpoints: Seq[EndpointClients => HttpEndpoint] = Seq(
    clients => VenueEndpoint(clients.componentClient),
    clients => QuestionEndpoint(clients.componentClient, clients.viewClient),
    clients => CohortEndpoint(clients.componentClient, clients.viewClient),
    clients => RunEndpoint(clients.componentClient, clients.viewClient, clients.storage),
    clients => OutcomeEndpoint(clients.componentClient),
    clients => ReportEndpoint(clients.componentClient, clients.viewClient),
    clients => ReportPageEndpoint(clients.componentClient, clients.viewClient)
  )

  /** The extensions of a service, in the order they start. */
  def extensions(timers: TimerRuntime, http: RuntimeExtension): Seq[RuntimeExtension] =
    Seq(ProjectionRuntime(), timers, http)

/**
 * Locally this needs Postgres (`just up`), and the reasoning stack (`just reasoning-up`) for the
 * graph and prose arms. In an ankka deployment the platform provides the database and every
 * `ANKKA_*` variable. The venues need no key; without `ANTHROPIC_API_KEY` or `EXA_API_KEY` the
 * service still starts, and each call that needs the key fails, naming it.
 */
@main def run(): Unit =
  val settings = Settings.load()
  Bench.install(
    Edges(
      Clock.system,
      settings,
      venues = Map(
        "kalshi"     -> forecastbench.venues.Kalshi(),
        "polymarket" -> forecastbench.venues.Polymarket()
      ),
      Models.fromSettings(settings),
      forecastbench.gather.Exa.fromEnv(settings.limits.excerpt)
    )
  )
  val timers  = TimerRuntime()
  val builder = Ankka.service.registerAll(BenchService.components)
  val service = BenchService
    .extensions(timers, HttpServer.of(BenchService.endpoints*))
    .foldLeft(builder)(_.withExtension(_))
    .start()
  timers.timerScheduler.createRecurringTimer(
    ReadOutcomes.TimerName,
    settings.outcomesEvery,
    settings.outcomesEvery,
    ReadOutcomes.read.deferred("all")
  )

  sys.addShutdownHook(service.terminate())
  scala.concurrent.Await
    .result(service.whenTerminated, scala.concurrent.duration.Duration.Inf): Unit
