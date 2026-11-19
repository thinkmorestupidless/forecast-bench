package forecastbench.application

import com.thinkmorestupidless.ankka.core.ErrorCode
import com.thinkmorestupidless.ankka.testkit.EventSourcedTestKit
import forecastbench.domain.*

import java.time.Instant

class QuestionEntitySuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")

  private def imp(outcomes: Int = 2) = QuestionImport(
    "kalshi",
    "BOE",
    "BOE",
    "Will the Bank cut?",
    Some("criteria"),
    now.plusSeconds(86400 * 30),
    Some("Economics"),
    outcomes,
    "economy",
    Standing.Prospective,
    None,
    now
  )

  test("an import holds the question once and says so the second time"):
    val kit   = EventSourcedTestKit.of(QuestionEntity, "kalshi.boe")
    val first = kit.call(QuestionEntity.importQuestion)(imp())
    assert(first.replyValue.held)
    val again = kit.call(QuestionEntity.importQuestion)(imp())
    assert(!again.replyValue.held)
    assert(!again.persisted)

  test("the entity refuses a question with other than two outcomes, as a guard"):
    val kit = EventSourcedTestKit.of(QuestionEntity, "kalshi.boe")
    val r   = kit.call(QuestionEntity.importQuestion)(imp(outcomes = 3))
    assertEquals(r.error.code, ErrorCode.BadRequest)
    assertEquals(r.errorMessage, "more than two outcomes")

  test("an outcome read twice is recorded once"):
    val kit = EventSourcedTestKit.of(QuestionEntity, "kalshi.boe")
    kit.call(QuestionEntity.importQuestion)(imp()): Unit
    val reading = ReadOutcome(OutcomeReading(OutcomeValue.No, disputed = false, Some(now)), now)
    assert(kit.call(QuestionEntity.readOutcome)(reading).replyValue)
    assert(!kit.call(QuestionEntity.readOutcome)(reading).replyValue)
    assertEquals(kit.currentState.question.map(_.outcomes.size), Some(1))
