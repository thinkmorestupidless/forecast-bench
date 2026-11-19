package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*

final case class PackState(pack: Option[Pack])

/**
 * One question's pack in one cohort, `<cohort>.<qkey>`. It is gathered once: a second `gather` is
 * refused and the pack is as it was (FR-017). There is no command that changes it.
 */
final class PackEntity extends EventSourcedEntity[PackState, PackGathered]:

  def emptyState: PackState = PackState(None)

  def applyEvent(event: PackGathered): PackState = PackState(Some(event.pack))

  def gather(pack: Pack): Effect[PackState] =
    if currentState.pack.isDefined then effects.error(PackEntity.Gathered, ErrorCode.Conflict)
    else effects.persist(PackGathered(pack)).thenReplyState

  def get: ReadOnlyEffect[PackState] = effects.reply(currentState)

object PackEntity
    extends EventSourcedEntity.Companion[PackEntity, PackState, PackGathered](
      componentId = ComponentId("pack"),
      stateSerializer = Codecs.serializer[PackState]("pack"),
      eventSerializer = Codecs.serializer[PackGathered]("pack-gathered")
    ):
  given Serializer[PackState] = stateSerializer
  given Serializer[Pack]      = Codecs.serializer[Pack]("pack-value")

  val Gathered = "the pack is gathered"

  def create(context: EventSourcedEntityContext) = new PackEntity

  val gather = command("gather")(_.gather)
  val get    = query("get")(_.get)
