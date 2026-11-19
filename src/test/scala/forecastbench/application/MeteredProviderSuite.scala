package forecastbench.application

import com.thinkmorestupidless.ankka.agent.*
import forecastbench.domain.Usage

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future
import scala.concurrent.duration.*

class MeteredProviderSuite extends munit.FunSuite:

  private def request =
    ModelRequest(ModelSettings("m"), Some("system"), Vector(ChatMessage.User.text("user")))

  private def answering(response: => Future[ModelResponse]): ModelProvider = new ModelProvider:
    val name                      = "scripted"
    val modelName                 = "m"
    def complete(r: ModelRequest) = response

  test("a call's tokens are read from its reply and its time is measured"):
    val clock = AtomicLong(0)
    val p = MeteredProvider(
      answering {
        clock.addAndGet(1250000000L);
        Future.successful(ModelResponse("hi", usage = TokenUsage(120, 30)))
      },
      () => clock.get
    )
    val m = p.call(request, 10.seconds)
    assertEquals(m.reply.map(_.text), Right("hi"))
    assertEquals(m.usage, Usage(120, 30))
    assertEquals(m.latencyMs, 1250L)

  test("a provider that errors is a value, not a thrown failure"):
    val m = MeteredProvider(answering(Future.failed(ModelCallFailed("scripted", "down"))))
      .call(request, 10.seconds)
    assert(m.reply.isLeft)
    assertEquals(m.usage, Usage.zero)

  test("a provider that does not answer within the timeout is a value"):
    val m = MeteredProvider(answering(Future.never)).call(request, 50.millis)
    assert(m.reply.isLeft)

  test("cost is the model's prices times its tokens, to six places"):
    val p = Prices(BigDecimal(4), BigDecimal(20), java.time.LocalDate.parse("2026-11-03"))
    assertEquals(p.cost(1000, 500), BigDecimal("0.014000"))
