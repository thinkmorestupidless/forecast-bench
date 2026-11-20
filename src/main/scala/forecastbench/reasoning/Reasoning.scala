package forecastbench.reasoning

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray, writeToArray}
import com.thinkmorestupidless.ankka.sdk.ServiceClient
import forecastbench.domain.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal

/**
 * The bench's client of the reasoning service (contracts/reasoning.md), over the `ServiceClient`
 * for `services("reasoning")`. Every call answers a value: what was asked for, or why not, which is
 * `the reasoning service could not be reached` when no answer came or the service answered `503`,
 * and otherwise the refusal's `rule`. Every write is safe to send again.
 */
final class Reasoning(client: ServiceClient):
  import Reasoning.*

  private def call(
      method: String,
      path: String,
      body: Option[Array[Byte]]
  ): Either[String, Array[Byte]] =
    try
      val r = client.request(method, path, body, body.map(_ => "application/json"))
      if r.status / 100 == 2 then Right(r.body)
      else if r.status == 503 then Left(Unreachable)
      else
        Left(
          Try(readFromArray[Refusal](r.body)).toOption
            .flatMap(_.rule)
            .getOrElse(s"the reasoning service answered ${r.status}")
        )
    catch case NonFatal(_) => Left(Unreachable)

  private def post[B: JsonValueCodec](path: String, body: B) =
    call("POST", path, Some(writeToArray(body)))

  private def decode[R: JsonValueCodec](bytes: Array[Byte]): Either[String, R] =
    Try(readFromArray[R](bytes)).toEither.left.map(e =>
      s"the reasoning service's answer could not be read: ${e.getMessage}"
    )

  def holder(id: String, name: String): Either[String, Unit] =
    post("/holders", HolderBody(id, "model", name)).map(_ => ())

  def question(id: String, statement: String): Either[String, Unit] =
    post(
      "/questions",
      QuestionBody(id, statement.take(500), List(Hypothesis("yes", "Yes"), Hypothesis("no", "No")))
    ).map(_ => ())

  def source(name: String): Either[String, String] =
    val id = sourceId(name)
    post("/sources", SourceBody(id, name.take(200))).map(_ => id)

  /** The evidence of a document; its id is the service's. A time later than now is left out. */
  def evidence(source: String, document: Document, now: Instant): Either[String, String] =
    post(
      "/evidence",
      EvidenceBody(
        source,
        document.address,
        document.excerpt,
        document.publishedAt.filterNot(_.isAfter(now))
      )
    )
      .flatMap(decode[Identified])
      .map(_.id)

  def claim(
      id: String,
      holder: String,
      statement: String,
      evidence: String,
      hypothesis: String,
      stance: Stance
  ): Either[String, Unit] =
    post(
      "/claims",
      ClaimBody(id, holder, statement, List(evidence), List(StanceBody(hypothesis, stance.word)))
    ).map(_ => ())

  /** Waits for the graph to hold the claim; not caught up is a failed read. */
  def await(claim: String, limitMs: Int = 30000): Either[String, Unit] =
    post("/graph/wait", WaitBody("claim", claim, limitMs))
      .flatMap(decode[Waited])
      .flatMap(w => if w.caughtUp then Right(()) else Left(NotCaughtUp))

  /** The claims taking the stance on the hypothesis, oldest first, as the graph answers them. */
  def cases(hypothesis: String, stance: Stance): Either[String, List[Support]] =
    val q = s"hypothesis=${URLEncoder.encode(hypothesis, UTF_8)}&stance=${stance.word}"
    call("GET", s"/answers/case?$q", None).flatMap(decode[Case]).map(_.claims)

  def belief(
      id: String,
      holder: String,
      hypothesis: String,
      probability: Double,
      restsOn: List[String]
  ): Either[String, Unit] =
    post(
      "/beliefs/revisions",
      RevisionBody(id, holder, hypothesis, probability, restsOn.map(RestsOn(_)))
    ).map(_ => ())

  /**
   * The content both arms are rendered from (research R8): the appraisal's claims read back from
   * the graph's two cases, each placed at the document of the pack its evidence is, and its open
   * points.
   */
  def content(
      question: String,
      pack: Pack,
      openPoints: List[String]
  ): Either[String, AppraisalContent] =
    val hyp = hypothesis(question)
    def claimOf(stance: Stance)(s: Support): Either[String, ClaimGiven] =
      for
        e <- s.evidence.headOption.toRight(s"the claim ${s.claim.id} derives from no evidence")
        doc <- pack.documents
          .find(d => e.locator.contains(d.address))
          .toRight(s"the claim ${s.claim.id} derives from no document of the pack")
      yield ClaimGiven(
        short(s.claim.id),
        s.claim.statement.getOrElse(""),
        stance,
        doc.n,
        e.source.name,
        e.publishedAt,
        e.excerpt.getOrElse("")
      )
    def read(stance: Stance) =
      cases(hyp, stance).flatMap(
        _.filter(_.claim.id.startsWith(s"$question.c"))
          .foldLeft[Either[String, List[ClaimGiven]]](Right(Nil)) {
            case (Left(w), _)     => Left(w)
            case (Right(done), s) => claimOf(stance)(s).map(done :+ _)
          }
      )
    for
      yes <- read(Stance.Supports)
      no  <- read(Stance.Contradicts)
    yield AppraisalContent(
      (yes ++ no).sortBy(c => c.id.drop(1).toIntOption.getOrElse(Int.MaxValue)),
      openPoints
    )

object Reasoning:
  val Unreachable = "the reasoning service could not be reached"
  val NotCaughtUp = "the graph had not caught up with the appraisal"

  def questionId(cohort: String, question: String): String  = Ids.pack(cohort, question)
  def hypothesis(questionId: String): String                = s"$questionId/yes"
  def appraiser(cohort: String): String                     = s"$cohort.appraisal"
  def graphHolder(cohort: String, repetition: Int): String  = s"$cohort.graph.r$repetition"
  def sourceId(name: String): String                        = s"src.${Ids.sha256(name).take(12)}"
  def claimId(questionId: String, n: Int): String           = s"$questionId.c$n"
  def beliefId(questionId: String, repetition: Int): String = s"$questionId.b$repetition"

  /** A claim's id as the arms are given it: `c<n>`. */
  def short(claimId: String): String = claimId.drop(claimId.lastIndexOf('.') + 1)

  final case class HolderBody(id: String, kind: String, name: String)
  final case class Hypothesis(id: String, statement: String)
  final case class QuestionBody(id: String, statement: String, hypotheses: List[Hypothesis])
  final case class SourceBody(id: String, name: String)
  final case class EvidenceBody(
      source: String,
      locator: String,
      excerpt: String,
      publishedAt: Option[Instant]
  )
  final case class Identified(id: String)
  final case class StanceBody(hypothesis: String, stance: String)
  final case class ClaimBody(
      id: String,
      holder: String,
      statement: String,
      derivesFrom: List[String],
      stances: List[StanceBody]
  )
  final case class WaitBody(kind: String, id: String, limitMs: Int)
  final case class Waited(caughtUp: Boolean)
  final case class Claimed(id: String, statement: Option[String] = None)
  final case class SourceRead(id: String, name: String)
  final case class EvidenceRead(
      id: String,
      source: SourceRead,
      locator: Option[String] = None,
      excerpt: Option[String] = None,
      publishedAt: Option[Instant] = None
  )
  final case class Support(claim: Claimed, evidence: List[EvidenceRead] = Nil)
  final case class Case(claims: List[Support] = Nil)
  final case class RestsOn(claim: String)
  final case class RevisionBody(
      id: String,
      holder: String,
      hypothesis: String,
      probability: Double,
      restsOn: List[RestsOn]
  )
  final case class Refusal(rule: Option[String] = None)

  given JsonValueCodec[HolderBody]   = Wire.make
  given JsonValueCodec[QuestionBody] = Wire.make
  given JsonValueCodec[SourceBody]   = Wire.make
  given JsonValueCodec[EvidenceBody] = Wire.make
  given JsonValueCodec[Identified]   = Wire.make
  given JsonValueCodec[ClaimBody]    = Wire.make
  given JsonValueCodec[WaitBody]     = Wire.make
  given JsonValueCodec[Waited]       = Wire.make
  given JsonValueCodec[Case]         = Wire.make
  given JsonValueCodec[RevisionBody] = Wire.make
  given JsonValueCodec[Refusal]      = Wire.make
