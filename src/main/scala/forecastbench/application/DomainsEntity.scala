package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*

/** The experimenter's names for one venue's categories: category to domain. */
final case class Domains(names: Map[String, String]):
  def of(category: Option[String]): String = category.flatMap(names.get).getOrElse("other")

/** One per venue, by its name. Applies to questions imported afterwards. */
final class DomainsEntity extends KeyValueEntity[Domains]:
  def emptyState: Domains = Domains(Map.empty)

  def set(names: Domains): Effect[Domains] = effects.updateState(names).thenReplyState

  def get: ReadOnlyEffect[Domains] = effects.reply(currentState)

object DomainsEntity
    extends KeyValueEntity.Companion[DomainsEntity, Domains](
      componentId = ComponentId("domains"),
      stateSerializer = Codecs.serializer[Domains]("domains")
    ):
  given Serializer[Domains] = stateSerializer

  def create(context: KeyValueEntityContext) = new DomainsEntity

  val set = command("set")(_.set)
  val get = query("get")(_.get)
