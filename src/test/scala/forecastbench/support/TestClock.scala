package forecastbench.support

import forecastbench.domain.Clock

import java.time.{Duration, Instant}

/** A clock the steps set: windows, closing times and pretended times decide refusals. */
final class TestClock(start: Instant) extends Clock:
  @volatile private var at: Instant = start

  def now: Instant = at

  def set(instant: Instant): Unit = at = instant

  def advance(by: Duration): Unit = at = at.plus(by)

object TestClock:
  /** A fixed start, so a scenario's times are the same in every run. */
  val Start: Instant = Instant.parse("2027-01-04T09:00:00Z")
