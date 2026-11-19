package forecastbench.reasoning

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.thinkmorestupidless.ankka.agent.{ChatMessage, ModelRequest}
import com.thinkmorestupidless.ankka.core.Codecs
import forecastbench.application.{Instruction, Models}
import forecastbench.domain.*

import java.time.Instant
import java.util.concurrent.{Callable, Executors}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** What one appraisal is asked to do: a question's pack, in its cohort, with the cohort's model. */
final case class Appraise(
    cohort: String,
    question: String,
    asked: QuestionGiven,
    pack: Pack,
    model: String,
    repetitions: Int,
    now: Instant
)

/**
 * The appraisal of research R7: each document read for claims, every claim checked against its
 * document, the open points the claims leave, and the records written to the reasoning service.
 * Code calls the model and writes the records; the model calls nothing.
 */
object Appraiser:

  final case class Stated(claims: List[ReaderClaim])
  final case class Open(openPoints: List[String])
  private given JsonValueCodec[Stated] = Codecs.make
  private given JsonValueCodec[Open]   = Codecs.make

  val Timeout: FiniteDuration = 3.minutes

  val NotClaims     = "the reader's answer was not claims"
  val NotOpenPoints = "the answer was not open points"

  private def json(reply: String): String =
    reply.trim.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim

  /** The claims for the open-points call: each with its stance and its document. */
  def claimsText(claims: List[CheckedClaim]): String =
    claims.zipWithIndex
      .map((c, i) =>
        s"- [c${i + 1}] ${c.statement} (${c.stance.word} yes; from [${c.document.n}] ${c.document.source})"
      )
      .mkString("\n")

  def appraise(
      a: Appraise,
      models: Models,
      reasoning: Reasoning,
      reader: Instruction,
      openPoints: Instruction
  ): Either[AppraisalFailed, AppraisalMade] =
    var usage = Usage.zero
    var cost  = BigDecimal(0)
    def failed(reason: String, naming: Option[String] = None) =
      AppraisalFailed(reason, naming, usage, cost, a.now)

    def ask(system: String, user: String): Either[String, String] =
      val request =
        ModelRequest(models.settings(a.model), Some(system), Vector(ChatMessage.User.text(user)))
      val metered = models.provider(a.model).call(request, Timeout)
      synchronized {
        usage = usage + metered.usage
        cost = cost + models.cost(a.model, metered.usage)
      }
      metered.reply.map(_.text).left.map(_ => "the model could not be reached")

    // 1. Each document read for claims, the documents at once.
    val pool = Executors.newVirtualThreadPerTaskExecutor()
    val read =
      try
        pool
          .invokeAll(a.pack.documents.map { d =>
            (
                () => ask(reader.text, Arm.block(a.asked) + "\nDocument:\n" + Arms.document(d))
            ): Callable[Either[String, String]]
          }.asJava)
          .asScala
          .toList
          .map(_.get)
      finally pool.shutdown()

    val result = for
      // 2. Every claim checked against its document; the first that does not hold fails it.
      checked <- a.pack.documents
        .zip(read)
        .foldLeft[Either[AppraisalFailed, List[CheckedClaim]]](Right(Nil)) {
          case (Left(f), _) => Left(f)
          case (Right(done), (d, reply)) =>
            for
              text <- reply.left.map(failed(_, Some(s"document [${d.n}]")))
              stated <- scala.util
                .Try(readFromString[Stated](json(text)))
                .toEither
                .left
                .map(_ => failed(NotClaims, Some(s"document [${d.n}]")))
              ok <- Appraisal
                .check(d, stated.claims)
                .left
                .map(r => failed(r.reason, Some(r.naming)))
            yield done ++ ok
        }
      // 3. The open points the claims leave.
      open <- ask(openPoints.text, Arm.block(a.asked) + "\nClaims:\n" + claimsText(checked)).left
        .map(failed(_, Some("the open points")))
        .flatMap(t =>
          scala.util
            .Try(readFromString[Open](json(t)))
            .toEither
            .left
            .map(_ => failed(NotOpenPoints, Some("the open points")))
        )
        .map(_.openPoints.map(_.trim).filter(_.nonEmpty))
      // 4. The records written, in order; each write is safe to send again.
      claims <- write(a, reasoning, checked).left.map(failed(_, Some("the reasoning service")))
    yield AppraisalMade(
      a.cohort,
      a.question,
      a.model,
      reader.version,
      openPoints.version,
      Reasoning.questionId(a.cohort, a.question),
      claims,
      open,
      usage,
      cost,
      a.now
    )
    result.map(_.copy(usage = usage, cost = cost)).left.map(_.copy(usage = usage, cost = cost))

  /**
   * The holders, the question, and for each claim its source, its evidence and itself (research
   * R8).
   */
  private def write(
      a: Appraise,
      r: Reasoning,
      checked: List[CheckedClaim]
  ): Either[String, List[ClaimRef]] =
    val q      = Reasoning.questionId(a.cohort, a.question)
    val holder = Reasoning.appraiser(a.cohort)
    val holders = (holder -> s"${a.cohort} appraisal") ::
      (1 to a.repetitions).toList.map(n =>
        Reasoning.graphHolder(a.cohort, n) -> s"${a.cohort} graph arm, repetition $n"
      )
    for
      _ <- holders.foldLeft[Either[String, Unit]](Right(()))((e, h) =>
        e.flatMap(_ => r.holder(h._1, h._2))
      )
      _ <- r.question(q, a.asked.statement)
      refs <- checked.zipWithIndex.foldLeft[Either[String, List[ClaimRef]]](Right(Nil)) {
        case (Left(w), _) => Left(w)
        case (Right(done), (c, i)) =>
          val id = Reasoning.claimId(q, i + 1)
          for
            source   <- r.source(c.document.source)
            evidence <- r.evidence(source, c.document, a.now)
            _ <- r.claim(id, holder, c.statement, evidence, Reasoning.hypothesis(q), c.stance)
          yield done :+ ClaimRef(id, c.document.n, evidence, c.stance, c.statement)
      }
    yield refs
