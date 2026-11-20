package forecastbench.gather

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.{ChatMessage, ModelRequest}
import com.thinkmorestupidless.ankka.core.Codecs
import forecastbench.application.{Instruction, Limits, Models}
import forecastbench.domain.*

import java.time.Instant
import scala.concurrent.duration.*

/** What a gather is asked to do: the question as an arm is given it, and the cohort's terms. */
final case class Gather(
    cohort: String,
    question: String,
    asked: QuestionGiven,
    model: String,
    pretended: Option[Instant],
    now: Instant,
    version: Int = Gatherer.Version
)

/**
 * Gatherer version 1 (research R12): three searches for a question, its statement and two queries
 * the cohort's model writes from the statement and the criteria, merged into one pack. The two
 * queries' cost is the gatherer's and is on the pack, outside any arm's.
 */
object Gatherer:

  /** The gatherer's current version: what a cohort frozen now gathers by. */
  val Version = forecastbench.domain.Pack.CurrentVersion

  final case class Queries(queries: List[String])
  private given JsonValueCodec[Queries] = Codecs.make

  val Timeout: FiniteDuration = 2.minutes

  def readQueries(reply: String): Option[List[String]] =
    forecastbench.domain.Reply
      .read[Queries](reply)
      .map(_.queries.map(_.trim).filter(_.nonEmpty))
      .filter(_.nonEmpty)

  def gather(
      g: Gather,
      models: Models,
      search: Search,
      instructions: Instruction,
      limits: Limits
  ): Either[String, Pack] =
    val request = ModelRequest(
      models.settings(g.model),
      Some(instructions.text),
      Vector(ChatMessage.User.text(Arm.block(g.asked)))
    )
    val metered = models.provider(g.model).call(request, Timeout)
    val cost    = models.cost(g.model, metered.usage)
    for
      reply <- metered.reply.left.map(why => s"the queries could not be written: $why")
      written <- readQueries(reply.text).toRight(
        "the queries could not be written: the answer was not two queries"
      )
      queries = (g.asked.statement -> "statement") :: written
        .take(limits.searches - 1)
        .map(_ -> g.model)
      found <- queries
        .foldLeft[Either[String, List[(String, String, List[SearchResult])]]](Right(Nil)) {
          case (Left(why), _) => Left(why)
          case (Right(done), (query, by)) =>
            search
              .search(query, g.pretended, limits.results)
              .left
              .map(f => s"the search failed: ${f.why}")
              .map(results => done :+ ((query, by, results)))
        }
    yield
      val documents = Pack.merge(
        found.map(_._3.map(r => Found(r.address, r.title, r.source, r.publishedAt, r.text))),
        limits.documents,
        limits.excerpt,
        g.pretended,
        g.version
      )
      Pack(
        g.cohort,
        g.question,
        g.version,
        g.now,
        found.map((q, by, results) => SearchMade(q, by, results.map(_.address))),
        documents,
        metered.usage,
        cost
      )
