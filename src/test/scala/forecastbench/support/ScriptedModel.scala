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

  private val seen = ConcurrentLinkedQueue[ModelRequest]()

  /** Each kind of request is answered by rules of its own: a forecast's never answers a query. */
  private val rules = ScriptedModel.Task.values
    .map(t => t -> ConcurrentLinkedQueue[(String, ModelRequest => Either[String, ModelResponse])]())
    .toMap

  /** Answers the forecasting requests whose text holds `word`; the latest rule wins. */
  def when(word: String)(answer: ModelRequest => Either[String, ModelResponse]): Unit =
    whenTask(ScriptedModel.Task.Forecast, word)(answer)

  def whenQueries(word: String)(answer: ModelRequest => Either[String, ModelResponse]): Unit =
    whenTask(ScriptedModel.Task.Queries, word)(answer)

  /** Answers the appraisal's reading of a document whose text holds `word`. */
  def whenReading(word: String)(answer: ModelRequest => Either[String, ModelResponse]): Unit =
    whenTask(ScriptedModel.Task.Reading, word)(answer)

  def whenOpenPoints(word: String)(answer: ModelRequest => Either[String, ModelResponse]): Unit =
    whenTask(ScriptedModel.Task.OpenPoints, word)(answer)

  def whenTask(task: ScriptedModel.Task, word: String)(
      answer: ModelRequest => Either[String, ModelResponse]
  ): Unit = rules(task).add(word -> answer): Unit

  def replies(word: String, text: String, usage: TokenUsage = ScriptedModel.Usage): Unit =
    when(word)(_ => Right(ModelResponse(text, usage = usage)))

  def unreachable(word: String): Unit = when(word)(_ => Left("the scripted model is down"))

  /** Every forecasting request whose text holds `word`, in the order they came. */
  def requests(word: String): Vector[ModelRequest] = tasks(ScriptedModel.Task.Forecast, word)

  /** Every request for the gatherer's queries whose text holds `word`. */
  def queryRequests(word: String): Vector[ModelRequest] = tasks(ScriptedModel.Task.Queries, word)

  def tasks(task: ScriptedModel.Task, word: String): Vector[ModelRequest] =
    seen.asScala.toVector.filter(r =>
      ScriptedModel.Task.of(r) == task && ScriptedModel.text(r).contains(word)
    )

  def complete(request: ModelRequest): Future[ModelResponse] =
    seen.add(request)
    val text = ScriptedModel.text(request)
    val task = ScriptedModel.Task.of(request)
    rules(task).asScala.toVector.reverse.find((w, _) => text.contains(w)) match
      case Some((_, answer)) =>
        answer(request).fold(why => Future.failed(ModelCallFailed(name, why)), Future.successful)
      case None =>
        Future.successful(
          ModelResponse(ScriptedModel.default(task, request), usage = ScriptedModel.Usage)
        )

object ScriptedModel:
  /** What every scripted call uses unless a scenario says: 1,000 tokens in and 200 out. */
  val Usage: TokenUsage = TokenUsage(1000, 200)

  def forecast(p: Double, reasons: List[String] = Nil): String =
    val r = reasons.map(x => s""""$x"""").mkString(",")
    s"""{"probability": $p, "abstain": null, "reasons": [$r], "rationale": "scripted"}"""

  /** What a request asks for, by its instructions' first words. */
  enum Task:
    case Forecast, Queries, Reading, OpenPoints

  object Task:
    def of(r: ModelRequest): Task =
      val system = r.systemMessage.getOrElse("")
      if system.startsWith("You write search queries") then Queries
      else if system.startsWith("You read one document") then Reading
      else if system.startsWith("You find what the evidence leaves open") then OpenPoints
      else Forecast

  def writesQueries(r: ModelRequest): Boolean = Task.of(r) == Task.Queries

  def default(task: Task, r: ModelRequest): String = task match
    case Task.Forecast => forecast(0.5)
    case Task.Queries  => queries(r)
    case Task.Reading =>
      claims(List(("The document bears on the question", "supports", quoteOf(r))))
    case Task.OpenPoints =>
      openPoints(List("Whether what the documents report will hold until the question closes"))

  /** The excerpt of the one document a reading request gives. */
  def excerptOf(r: ModelRequest): String =
    text(r.copy(systemMessage = None))
      .split("\nDocument:\n", 2)
      .lift(1)
      .map(_.linesIterator.drop(1).mkString("\n"))
      .getOrElse("")

  /** The first few words of the document a reading request gives: a quote that is in it. */
  def quoteOf(r: ModelRequest, words: Int = 4): String =
    excerptOf(r).trim.split("\\s+").take(words).mkString(" ")

  private def str(s: String) = ujson.write(ujson.Str(s))

  def claims(claims: List[(String, String, String)]): String =
    claims
      .map((st, stance, q) =>
        s"""{"statement": ${str(st)}, "stance": ${str(stance)}, "quote": ${str(q)}}"""
      )
      .mkString("""{"claims": [""", ", ", "]}")

  def openPoints(points: List[String]): String =
    points.map(str).mkString("""{"openPoints": [""", ", ", "]}")

  /** Two queries that repeat the statement, so a scenario's scripted search finds them by it. */
  def queries(r: ModelRequest): String =
    val statement = text(r.copy(systemMessage = None)).linesIterator
      .find(_.startsWith("Question: "))
      .map(_.stripPrefix("Question: "))
      .getOrElse("")
    s"""{"queries": ["latest on $statement", "figures for $statement"]}"""

  def text(r: ModelRequest): String =
    (r.systemMessage.toVector ++ r.messages.collect { case ChatMessage.User(content) =>
      content.collect { case MessageContent.Text(t) => t }.mkString
    }).mkString("\n")
