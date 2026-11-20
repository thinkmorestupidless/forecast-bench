package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.Codecs

import java.time.Instant

/** The venue's probability and how it was read: the rule (`mid` or `last`) and what it read. */
final case class VenueReading(
    probability: Double,
    rule: String,
    bid: Option[Double],
    ask: Option[Double],
    last: Option[Double],
    observedAt: Instant
)

/** How a run ended: with a forecast, abstained with a reason, or failed with a reason. */
enum Ending:
  case Forecast(probability: Double)
  case Abstained(reason: String)
  case Failed(reason: String)

  def forecast: Option[Double] = this match
    case Forecast(p) => Some(p)
    case _           => None

  def word: String = this match
    case _: Forecast  => "forecast"
    case _: Abstained => "abstained"
    case _: Failed    => "failed"

/** The answer a model is asked for (contracts/arm.md). */
final case class Answer(
    probability: Option[Double],
    abstain: Option[String],
    reasons: Option[List[String]],
    rationale: Option[String]
)

object Answer:

  val NotAProbability = "the answer was not a probability"
  val Unreachable     = "the model could not be reached"

  private given JsonValueCodec[Answer] = Codecs.make

  /**
   * How a reply ends a run, and the reasons it named. A reply is read as JSON by `Reply`: itself,
   * optionally in a fenced block, else its last JSON object. Anything with no object of the form, a
   * probability outside nought to one, or both or neither of `probability` and `abstain`, is not a
   * probability.
   */
  def read(reply: String): (Ending, List[String]) =
    val answer = Reply.read[Answer](reply)
    answer match
      case Some(Answer(Some(p), None, reasons, _)) if p >= 0.0 && p <= 1.0 && !p.isNaN =>
        (Ending.Forecast(p), reasons.getOrElse(Nil))
      case Some(Answer(None, Some(why), _, _)) if why.trim.nonEmpty =>
        (Ending.Abstained(why.trim), Nil)
      case _ => (Ending.Failed(NotAProbability), Nil)
