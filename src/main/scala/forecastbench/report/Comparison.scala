package forecastbench.report

/** One question's paired difference in Brier score between two arms, with its event group. */
final case class Paired(question: String, group: String, difference: Double)

/**
 * A comparison of two arms on the questions both have a score for: the mean of `a - b` in Brier
 * score, its interval, its count and its standing. Under 30 questions it gives no difference.
 */
final case class Comparison(
    a: String,
    b: String,
    count: Int,
    standing: String,
    meanDifference: Option[Double],
    interval: Option[Interval],
    says: String,
    measures: String
)

object Comparison:

  val TooFew       = "too few"
  val Preliminary  = "preliminary"
  val Counted      = "counted"
  val NoDifference = "no difference is shown"

  def standing(count: Int): String =
    if count < 30 then TooFew else if count < 100 then Preliminary else Counted

  /** Which arm the interval says has the lower Brier score, only when it leaves out nought. */
  def says(a: String, b: String, interval: Interval): String =
    if interval.high < 0.0 then s"$a has the lower Brier score"
    else if interval.low > 0.0 then s"$b has the lower Brier score"
    else NoDifference

  /** What the difference of two arms isolates (data-model.md, Report notes). */
  def measures(a: String, b: String): String = (a, b) match
    case ("graph", "prose") | ("prose", "graph") =>
      "the structure of the appraisal, its content held equal"
    case ("evidence", "graph") | ("graph", "evidence") => "the appraisal and its structure together"
    case (x, "venue-price") if x != "venue-price"      => "the arm against the crowd"
    case ("venue-price", _)                            => "the crowd against the arm"
    case ("model-alone", "evidence") | ("evidence", "model-alone") => "the evidence pack"
    case _                                                         => "the two arms as wholes"

  /**
   * Several differences for one question (a question in several cohorts) count once, as their mean,
   * before anything else is worked out.
   */
  def of(
      a: String,
      b: String,
      paired: Seq[Paired],
      seed: Long,
      method: Scoring.Method
  ): Comparison =
    val once = paired
      .groupBy(_.question)
      .toVector
      .sortBy(_._1)
      .map((q, ps) => Paired(q, ps.head.group, ps.map(_.difference).sum / ps.size))
    val count = once.size
    val stand = standing(count)
    if stand == TooFew then
      Comparison(a, b, count, stand, None, None, s"too few questions to compare", measures(a, b))
    else
      val mean = once.map(_.difference).sum / count
      val interval = Bootstrap.mean(
        once.map(p => (p.group, p.difference)),
        seed,
        method.resamples,
        method.confidence
      )
      Comparison(
        a,
        b,
        count,
        stand,
        Some(mean),
        interval,
        interval.fold(NoDifference)(says(a, b, _)),
        measures(a, b)
      )
