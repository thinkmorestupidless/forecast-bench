package forecastbench.domain

import java.time.Instant

/**
 * A claim of an appraisal as held: its id in the reasoning service, its document and its evidence.
 */
final case class ClaimRef(
    id: String,
    document: Int,
    evidence: String,
    stance: Stance,
    statement: String
)

/** An appraisal made (data-model.md): what it found and what it cost, once. */
final case class AppraisalMade(
    cohort: String,
    question: String,
    model: String,
    readerVersion: Int,
    openPointsVersion: Int,
    reasoningQuestion: String,
    claims: List[ClaimRef],
    openPoints: List[String],
    usage: Usage,
    cost: BigDecimal,
    madeAt: Instant
)

/** An appraisal failed: why, the claim or call it names, and what it cost before it failed. */
final case class AppraisalFailed(
    reason: String,
    naming: Option[String],
    usage: Usage,
    cost: BigDecimal,
    at: Instant
)

enum AppraisalEvent:
  case Made(made: AppraisalMade)
  case Failed(failed: AppraisalFailed)

/** A claim as the reader states it for one document (research R7, step 1). */
final case class ReaderClaim(statement: String, stance: String, quote: String)

/** A reader's claim once checked: its stance read, and the document it derives from. */
final case class CheckedClaim(document: Document, statement: String, stance: Stance, quote: String)

/** Why a claim did not hold, and the claim it names. */
final case class ClaimRefused(reason: String, naming: String)

object Appraisal:

  val MaxStatement = 1000

  val NotInDocument =
    "the claim derives from no document of the pack: its quote is not in the document"
  val NoStance     = "the claim's stance is neither supports nor contradicts"
  val BadStatement = s"the claim's statement is not 1 to $MaxStatement characters"
  val Failed       = "the appraisal failed"

  /** White space of any kind and length read as one space, and none at either end. */
  def normalise(s: String): String = s.trim.split("\\s+").filter(_.nonEmpty).mkString(" ")

  /** How a refused claim is named: its document and its place there, and its first words. */
  def naming(document: Document, k: Int, statement: String): String =
    s"claim $k of document [${document.n}]: ${statement.take(80)}"

  /**
   * The check of research R7 step 2 for one document's claims, in order; the first claim that does
   * not hold fails the appraisal, naming it (FR-022).
   */
  def check(
      document: Document,
      claims: List[ReaderClaim]
  ): Either[ClaimRefused, List[CheckedClaim]] =
    val excerpt = normalise(document.excerpt)
    claims.zipWithIndex.foldLeft[Either[ClaimRefused, List[CheckedClaim]]](Right(Nil)) {
      case (Left(refused), _) => Left(refused)
      case (Right(done), (c, i)) =>
        val name      = naming(document, i + 1, c.statement)
        val statement = c.statement.trim
        val quote     = normalise(c.quote)
        Stance.parse(c.stance.trim.toLowerCase) match
          case None => Left(ClaimRefused(NoStance, name))
          case Some(_) if statement.isEmpty || statement.length > MaxStatement =>
            Left(ClaimRefused(BadStatement, name))
          case Some(_) if quote.isEmpty || !excerpt.contains(quote) =>
            Left(ClaimRefused(NotInDocument, name))
          case Some(stance) => Right(done :+ CheckedClaim(document, statement, stance, quote))
    }
