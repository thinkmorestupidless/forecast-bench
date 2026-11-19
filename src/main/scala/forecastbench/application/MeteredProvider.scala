package forecastbench.application

import com.thinkmorestupidless.ankka.agent.{ModelProvider, ModelRequest, ModelResponse}
import forecastbench.domain.Usage

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/**
 * One call's reply with what it used and how long it took; or why the model could not be reached.
 */
final case class Metered(reply: Either[String, ModelResponse], usage: Usage, latencyMs: Long)

/**
 * Every model call the bench makes goes through here (research R6): it times the call and reads its
 * tokens from the reply, so each run carries its own cost, tokens and latency. A provider that
 * errors or does not answer within the timeout is a value, never a thrown failure, so the step
 * records the run as failed rather than failing the workflow.
 */
final class MeteredProvider(
    val provider: ModelProvider,
    nanos: () => Long = () => System.nanoTime()
):

  def call(request: ModelRequest, timeout: FiniteDuration): Metered =
    val start = nanos()
    val reply =
      try Right(Await.result(provider.complete(request.copy(timeout = Some(timeout))), timeout))
      catch case NonFatal(e) => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    val latencyMs = (nanos() - start) / 1000000L
    val usage = reply.fold(
      _ => Usage.zero,
      r => Usage(r.usage.inputTokens.toLong, r.usage.outputTokens.toLong)
    )
    Metered(reply, usage, latencyMs)
