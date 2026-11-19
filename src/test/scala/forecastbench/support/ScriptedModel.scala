package forecastbench.support

import com.thinkmorestupidless.ankka.agent.*

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

/**
 * The model the suites forecast with. A scenario says what it answers to a request whose text holds
 * a word of its own (its question's statement), so the scenarios sharing the one bench never read
 * each other's answers; anything unscripted answers a probability of a half.
 */
final class ScriptedModel extends ModelProvider:
  val name      = "scripted"
  val modelName = "scripted"

  private val rules =
    ConcurrentLinkedQueue[(String, ModelRequest => Either[String, ModelResponse])]()
  private val seen = ConcurrentLinkedQueue[ModelRequest]()

  /** Answers the requests whose system or user text holds `word`; the latest rule wins. */
  def when(word: String)(answer: ModelRequest => Either[String, ModelResponse]): Unit =
    rules.add(word -> answer): Unit

  def replies(word: String, text: String, usage: TokenUsage = ScriptedModel.Usage): Unit =
    when(word)(_ => Right(ModelResponse(text, usage = usage)))

  def unreachable(word: String): Unit = when(word)(_ => Left("the scripted model is down"))

  /** Every request whose text holds `word`, in the order they came. */
  def requests(word: String): Vector[ModelRequest] =
    seen.asScala.toVector.filter(r => ScriptedModel.text(r).contains(word))

  def complete(request: ModelRequest): Future[ModelResponse] =
    seen.add(request)
    val text = ScriptedModel.text(request)
    rules.asScala.toVector.reverse.find((w, _) => text.contains(w)) match
      case Some((_, answer)) =>
        answer(request).fold(why => Future.failed(ModelCallFailed(name, why)), Future.successful)
      case None =>
        Future.successful(ModelResponse(ScriptedModel.forecast(0.5), usage = ScriptedModel.Usage))

object ScriptedModel:
  /** What every scripted call uses unless a scenario says: 1,000 tokens in and 200 out. */
  val Usage: TokenUsage = TokenUsage(1000, 200)

  def forecast(p: Double, reasons: List[String] = Nil): String =
    val r = reasons.map(x => s""""$x"""").mkString(",")
    s"""{"probability": $p, "abstain": null, "reasons": [$r], "rationale": "scripted"}"""

  def text(r: ModelRequest): String =
    (r.systemMessage.toVector ++ r.messages.collect { case ChatMessage.User(content) =>
      content.collect { case MessageContent.Text(t) => t }.mkString
    }).mkString("\n")
