package forecastbench

import com.thinkmorestupidless.ankka.runtime.{Ankka, ProjectionRuntime}

/**
 * The whole service definition.
 *
 * Registration is explicit, so this is the complete inventory of what the bench hosts, and a
 * component left out fails here at startup rather than at its first request.
 *
 * Locally this needs Postgres (`just up`). In an ankka deployment the platform provides the
 * database and every `ANKKA_*` variable.
 */
@main def run(): Unit =
  val service = Ankka.service
    .withExtension(ProjectionRuntime())
    .start()

  sys.addShutdownHook(service.terminate())
  scala.concurrent.Await
    .result(service.whenTerminated, scala.concurrent.duration.Duration.Inf): Unit
