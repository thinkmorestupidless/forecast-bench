package forecastbench.domain

/** Tokens a model call used: what it was given and what it wrote. */
final case class Usage(inputTokens: Long, outputTokens: Long):
  def +(other: Usage): Usage =
    Usage(inputTokens + other.inputTokens, outputTokens + other.outputTokens)
  def total: Long = inputTokens + outputTokens

object Usage:
  val zero: Usage = Usage(0, 0)
