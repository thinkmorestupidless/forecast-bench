package forecastbench.report

import forecastbench.domain.OutcomeValue

/**
 * The scoring methods, by version (research R11). Method 1: the Brier score `(p - o)^2` with yes as
 * one, and the log score `-ln(p')` of the probability given to what happened, with `p` held within
 * `[0.01, 0.99]` for the log score only.
 */
object Scoring:

  final case class Method(version: Int, logBound: Double, resamples: Int, confidence: Double)

  val One: Method = Method(1, 0.01, 10000, 0.95)

  val methods: Map[Int, Method] = Map(1 -> One)

  def brier(p: Double, outcome: OutcomeValue): Double =
    val o = if outcome == OutcomeValue.Yes then 1.0 else 0.0
    (p - o) * (p - o)

  def log(method: Method, p: Double, outcome: OutcomeValue): Double =
    val held = math.min(math.max(p, method.logBound), 1.0 - method.logBound)
    -math.log(if outcome == OutcomeValue.Yes then held else 1.0 - held)

  /** Brier and log for a forecast on a yes or no; none for a method the bench does not have. */
  def score(version: Int, p: Double, outcome: OutcomeValue): Option[(Double, Double)] =
    methods
      .get(version)
      .filter(_ => outcome != OutcomeValue.Void)
      .map(m => (brier(p, outcome), log(m, p, outcome)))
