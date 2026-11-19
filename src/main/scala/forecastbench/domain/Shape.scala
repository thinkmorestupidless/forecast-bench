package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.time.Instant

/** A claim's one stance on the yes hypothesis. */
enum Stance:
  case Supports, Contradicts

  def word: String = toString.toLowerCase

object Stance:
  given JsonValueCodec[Stance]            = Words.codec(values.toSeq, _.word)
  def parse(text: String): Option[Stance] = Words.parse(values.toSeq, _.word, text)

/** A claim as the graph and prose arms are both given it (contracts/arm.md). */
final case class ClaimGiven(
    id: String,
    statement: String,
    stance: Stance,
    document: Int,
    source: String,
    publishedAt: Option[Instant],
    excerpt: String
)

/** The one value both arms are rendered from: the appraisal as read back from the graph. */
final case class AppraisalContent(claims: List[ClaimGiven], openPoints: List[String])
