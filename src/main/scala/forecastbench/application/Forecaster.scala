package forecastbench.application

import com.thinkmorestupidless.ankka.agent.{ChatMessage, ModelRequest}
import forecastbench.domain.*

import scala.concurrent.duration.*

/** One forecast by a model: what it was given, what it answered, and how the run ends. */
final case class Forecasted(
    input: Given,
    answered: Option[String],
    ending: Ending,
    reasons: List[String],
    usage: Usage,
    cost: BigDecimal,
    latencyMs: Long
)

/**
 * The forecaster (research R6): one call with the arm's instructions as the system text and the
 * arm's user text, no memory, the cohort's model. The reply ends the run by the table of
 * contracts/arm.md; a provider that fails is a failed run, "the model could not be reached".
 */
object Forecaster:

  val Timeout: FiniteDuration = 4.minutes

  def request(models: Models, model: String, input: Given): ModelRequest =
    ModelRequest(
      models.settings(model),
      Some(input.system),
      Vector(ChatMessage.User.text(input.user))
    )

  def forecast(models: Models, model: String, input: Given): Forecasted =
    val m = models.provider(model).call(request(models, model, input), Timeout)
    m.reply match
      case Left(_) =>
        Forecasted(
          input,
          None,
          Ending.Failed(Answer.Unreachable),
          Nil,
          m.usage,
          models.cost(model, m.usage),
          m.latencyMs
        )
      case Right(response) =>
        val text = response.text
        val (ending, reasons) =
          if response.isRefusal then (Ending.Failed(Answer.NotAProbability), Nil)
          else Answer.read(text)
        Forecasted(
          input,
          Some(text),
          ending,
          reasons,
          m.usage,
          models.cost(model, m.usage),
          m.latencyMs
        )
