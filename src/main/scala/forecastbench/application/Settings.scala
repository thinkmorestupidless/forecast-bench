package forecastbench.application

import com.typesafe.config.{Config, ConfigFactory}

import java.time.LocalDate
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.*

/** What a model costs, per million tokens, and the day the prices were read. */
final case class Prices(inputPerMillion: BigDecimal, outputPerMillion: BigDecimal, read: LocalDate):

  /** US dollars to six places. */
  def cost(inputTokens: Long, outputTokens: Long): BigDecimal =
    ((BigDecimal(inputTokens) * inputPerMillion + BigDecimal(outputTokens) * outputPerMillion) /
      BigDecimal(1000000)).setScale(6, BigDecimal.RoundingMode.HALF_UP)

/** One model a cohort may name (research R6). */
final case class ModelEntry(
    name: String,
    provider: String,
    model: String,
    maxTokens: Int,
    thinking: Boolean,
    effort: Option[String],
    prices: Prices
)

final case class Limits(documents: Int, excerpt: Int, searches: Int, results: Int)

/**
 * How the graph is read for an appraisal (contracts/reasoning.md): each claim waited for up to
 * `waitLimit`, and a read not caught up made again `attempts` times in all, `pause` apart.
 */
final case class GraphRead(waitLimit: FiniteDuration, attempts: Int, pause: FiniteDuration)

/** The bench's settings, read once at start from `bench` in the configuration. */
final case class Settings(
    models: Map[String, ModelEntry],
    concurrency: Int,
    limits: Limits,
    venues: Map[String, String],
    outcomesEvery: FiniteDuration,
    graphRead: GraphRead
)

object Settings:

  def load(): Settings = from(ConfigFactory.load())

  /** Refuses a model with no price: no run's cost could be computed. */
  def from(config: Config): Settings =
    val bench  = config.getConfig("bench")
    val models = bench.getConfigList("models").asScala.toList.map(model)
    val limits = bench.getConfig("limits")
    val venues = bench.getConfig("venues")
    Settings(
      models = models.map(m => m.name -> m).toMap,
      concurrency = bench.getInt("concurrency"),
      limits = Limits(
        documents = limits.getInt("documents"),
        excerpt = limits.getInt("excerpt"),
        searches = limits.getInt("searches"),
        results = limits.getInt("results")
      ),
      venues = venues.root.keySet.asScala.toList.map(k => k -> venues.getString(k)).toMap,
      outcomesEvery = bench.getDuration("outcomes-every").toScala,
      graphRead = GraphRead(
        waitLimit = bench.getDuration("graph-read.wait-limit").toScala,
        attempts = bench.getInt("graph-read.attempts"),
        pause = bench.getDuration("graph-read.pause").toScala
      )
    )

  private def model(c: Config): ModelEntry =
    val name = c.getString("name")
    if !c.hasPath("prices") then
      throw IllegalArgumentException(
        s"bench.models '$name' has no prices: a run's cost cannot be computed without them"
      )
    val p = c.getConfig("prices")
    ModelEntry(
      name = name,
      provider = c.getString("provider"),
      model = c.getString("model"),
      maxTokens = c.getInt("max-tokens"),
      thinking = c.getBoolean("thinking"),
      effort = if c.hasPath("effort") then Some(c.getString("effort")) else None,
      prices = Prices(
        BigDecimal(p.getString("input-per-million")),
        BigDecimal(p.getString("output-per-million")),
        LocalDate.parse(p.getString("read"))
      )
    )
