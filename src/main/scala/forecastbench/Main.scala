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
    Packs.descriptor
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
 * Locally this needs Postgres (`just up`). In an ankka deployment the platform provides the
 * database and every `ANKKA_*` variable.
 */
@main def run(): Unit =
  val settings = Settings.load()
  Bench.install(
    Edges(
      Clock.system,
      settings,
      venues = Map.empty,
      Models.fromSettings(settings),
      forecastbench.gather.NoSearch
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
