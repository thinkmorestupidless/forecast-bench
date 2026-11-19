package forecastbench.application

import com.thinkmorestupidless.ankka.agent.{AnthropicProvider, Effort, ModelProvider, ModelSettings}

/** The models a cohort may name, each behind the metering wrapper, and what a call costs. */
final class Models(entries: Map[String, ModelEntry], providers: Map[String, MeteredProvider]):

  def entry(name: String): Option[ModelEntry] = entries.get(name)

  def provider(name: String): MeteredProvider =
    providers.getOrElse(name, throw IllegalArgumentException(s"the bench has no model '$name'"))

  def settings(name: String): ModelSettings =
    val e = entries(name)
    ModelSettings(
      modelName = e.model,
      maxTokens = Some(e.maxTokens),
      effort = e.effort.flatMap(Models.effort),
      thinking = e.thinking
    )

  def cost(name: String, usage: forecastbench.domain.Usage): BigDecimal =
    entries(name).prices.cost(usage.inputTokens, usage.outputTokens)

object Models:

  def effort(word: String): Option[Effort] = Effort.values.find(_.toString.equalsIgnoreCase(word))

  /**
   * The real providers, by the name in each entry. A model with no key starts and fails each call.
   */
  def fromSettings(settings: Settings): Models =
    Models(
      settings.models,
      settings.models.map { (name, e) =>
        val provider: ModelProvider = e.provider match
          case "anthropic" =>
            AnthropicProvider.fromEnv(
              e.model,
              AnthropicProvider.Defaults(e.maxTokens.toLong, e.thinking, e.effort.flatMap(effort))
            )
          case other =>
            throw IllegalArgumentException(
              s"bench.models '$name' names the provider '$other', which the bench has no client for"
            )
        name -> MeteredProvider(provider)
      }
    )

  /** One provider for every model: a test's scripted model. */
  def all(settings: Settings, provider: ModelProvider): Models =
    Models(settings.models, settings.models.map((name, _) => name -> MeteredProvider(provider)))
