package forecastbench.domain

import java.time.Instant

/**
 * The one place the bench reads the time. Windows, closing times and the pretended time decide
 * refusals, so the suites set it.
 */
trait Clock:
  def now: Instant

object Clock:
  val system: Clock = new Clock:
    def now: Instant = Instant.now()
