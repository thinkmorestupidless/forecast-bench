package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*

final case class AppraisalState(made: Option[AppraisalMade], failed: Option[AppraisalFailed]):
  def held: Boolean = made.isDefined || failed.isDefined

/**
 * One question's appraisal in one cohort, `<cohort>.<qkey>`: made or failed, once (FR-020). A
 * second of either is refused and the appraisal is as it was.
 */
final class AppraisalEntity extends EventSourcedEntity[AppraisalState, AppraisalEvent]:

  def emptyState: AppraisalState = AppraisalState(None, None)

  def applyEvent(event: AppraisalEvent): AppraisalState = event match
    case AppraisalEvent.Made(m)   => currentState.copy(made = Some(m))
    case AppraisalEvent.Failed(f) => currentState.copy(failed = Some(f))

  private def once(event: AppraisalEvent): Effect[AppraisalState] =
    if currentState.held then effects.error(AppraisalEntity.Held, ErrorCode.Conflict)
    else effects.persist(event).thenReplyState

  def make(made: AppraisalMade): Effect[AppraisalState]     = once(AppraisalEvent.Made(made))
  def fail(failed: AppraisalFailed): Effect[AppraisalState] = once(AppraisalEvent.Failed(failed))

  def get: ReadOnlyEffect[AppraisalState] = effects.reply(currentState)

object AppraisalEntity
    extends EventSourcedEntity.Companion[AppraisalEntity, AppraisalState, AppraisalEvent](
      componentId = ComponentId("appraisal"),
      stateSerializer = Codecs.serializer[AppraisalState]("appraisal"),
      eventSerializer = Codecs.serializer[AppraisalEvent]("appraisal-event")
    ):
  given Serializer[AppraisalMade]   = Codecs.serializer[AppraisalMade]("appraisal-made")
  given Serializer[AppraisalFailed] = Codecs.serializer[AppraisalFailed]("appraisal-failed")

  val Held = "the appraisal is held"

  def create(context: EventSourcedEntityContext) = new AppraisalEntity

  val make = command("make")(_.make)
  val fail = command("fail")(_.fail)
  val get  = query("get")(_.get)
