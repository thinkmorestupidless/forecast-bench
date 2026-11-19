package forecastbench.application

import forecastbench.domain.Clock
import forecastbench.venues.Venue

/**
 * What the bench's components reach outside the platform: the clock, the settings, and (as later
 * tasks add them) the venues, the search and the models.
 *
 * A companion is a singleton the runtime asks to `create` a component, so a component cannot be
 * handed these at construction. They are installed once, before the service starts, by `Main` or by
 * the suites' fixture, and read through `Bench.edges`. One service in a JVM, so one set.
 */
final case class Edges(clock: Clock, settings: Settings, venues: Map[String, Venue])

object Bench:

  @volatile private var held: Option[Edges] = None

  def install(edges: Edges): Unit = held = Some(edges)

  def edges: Edges =
    held.getOrElse(
      throw IllegalStateException("Bench.install was not called before the service started")
    )
