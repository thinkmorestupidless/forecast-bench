package forecastbench.application

import com.thinkmorestupidless.ankka.core.ErrorCode
import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import forecastbench.domain.*

import java.time.Instant

class PackEntitySuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")
  private val doc =
    Document(1, "news.example", "https://news.example/1", Some(now), "excerpt", "title")
  private val pack = Pack(
    "november",
    "kalshi.q",
    1,
    now,
    List(SearchMade("q", "statement", List(doc.address))),
    List(doc),
    Usage(1, 1),
    BigDecimal(0)
  )

  test("a second gather is refused and the pack is as it was"):
    val kit = EventSourcedTestKit.of(PackEntity, "november.abc")
    kit.call(PackEntity.gather)(pack): Unit
    val again = kit.call(PackEntity.gather)(pack.copy(documents = Nil))
    assertEquals(again.error.code, ErrorCode.Conflict)
    assertEquals(kit.currentState.pack, Some(pack))
