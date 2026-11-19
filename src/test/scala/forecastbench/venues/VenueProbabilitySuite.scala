package forecastbench.venues

import java.time.Instant

class VenueProbabilitySuite extends munit.FunSuite:

  private val at = Instant.parse("2027-01-04T09:00:00Z")

  test("the middle of bid and ask when they are within 0.10"):
    val r = VenueProbability.read(Some(0.55), Some(0.61), Some(0.40), at)
    assertEquals(r.map(_.probability), Some(0.58))
    assertEquals(r.map(_.rule), Some("mid"))

  test("a spread of exactly 0.10 is still the middle"):
    assertEquals(VenueProbability.read(Some(0.40), Some(0.50), None, at).map(_.rule), Some("mid"))

  test("the last price when the spread is wider than 0.10"):
    val r = VenueProbability.read(Some(0.30), Some(0.70), Some(0.52), at)
    assertEquals(r.map(_.probability), Some(0.52))
    assertEquals(r.map(_.rule), Some("last"))

  test("the last price when bid or ask is missing"):
    assertEquals(VenueProbability.read(None, Some(0.70), Some(0.66), at).map(_.rule), Some("last"))

  test("none when there is no usable bid and ask and no last price"):
    assertEquals(VenueProbability.read(Some(0.2), Some(0.9), None, at), None)
    assertEquals(VenueProbability.read(None, None, None, at), None)

  test("what was read is kept with the reading"):
    val r = VenueProbability.read(Some(0.55), Some(0.61), Some(0.40), at).get
    assertEquals((r.bid, r.ask, r.last, r.observedAt), (Some(0.55), Some(0.61), Some(0.40), at))
