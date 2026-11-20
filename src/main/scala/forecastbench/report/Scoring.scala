package forecastbench.report

import forecastbench.domain.OutcomeValue

/**
 * The scoring methods, by version (research R11). Method 1: the Brier score `(p - o)^2` with yes as
 * one, and the log score `-ln(p')` of the probability given to what happened, with `p` held within
 * `[bound, 1 - bound]` for the log score only, the bound 0.01.
 */
object Scoring:

  final case class Method(version: Int, logBound: Double, resamples: Int, confidence: Double)

  val One: Method = Method(1, 0.01, 10000, 0.95)

  /** The methods the bench has. A new one is a new value here, never a change to one. */
  val methods: Map[Int, Method] = Map(1 -> One)

  def brier(p: Double, outcome: OutcomeValue): Double =
    val o = if outcome == OutcomeValue.Yes then 1.0 else 0.0
    (p - o) * (p - o)

  def log(method: Method, p: Double, outcome: OutcomeValue): Double =
    val held = math.min(math.max(p, method.logBound), 1.0 - method.logBound)
    -math.log(if outcome == OutcomeValue.Yes then held else 1.0 - held)

  /** Brier and log for a forecast on yes or no; nothing on void. */
  def score(method: Method, p: Double, outcome: OutcomeValue): Option[(Double, Double)] =
    Option.when(outcome != OutcomeValue.Void)((brier(p, outcome), log(method, p, outcome)))

  /** An arm's forecast for a question: the mean of its repetitions' forecasts, and their spread. */
  final case class ArmForecast(mean: Double, spread: Double, repetitions: Int)

  def armForecast(forecasts: List[Double]): Option[ArmForecast] =
    Option.when(forecasts.nonEmpty) {
      val n    = forecasts.size
      val mean = forecasts.sum / n
      val sd =
        if n < 2 then 0.0 else math.sqrt(forecasts.map(f => (f - mean) * (f - mean)).sum / (n - 1))
      ArmForecast(mean, sd, n)
    }
