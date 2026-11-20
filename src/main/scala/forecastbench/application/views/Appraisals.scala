package forecastbench.application.views

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.application.AppraisalEntity
import forecastbench.domain.AppraisalEvent

/** Each appraisal in brief: made or failed, its claims and open points, its cost, why it failed. */
final case class AppraisalRow(
    status: String,
    claims: Int,
    openPoints: Int,
    cost: BigDecimal,
    reason: Option[String]
)

final class AppraisalsView extends View[AppraisalEvent, AppraisalRow]:
  def onChange(event: AppraisalEvent): Effect = event match
    case AppraisalEvent.Made(m) =>
      effects.updateRow(AppraisalRow("made", m.claims.size, m.openPoints.size, m.cost, None))
    case AppraisalEvent.Failed(f) =>
      effects.updateRow(AppraisalRow("failed", 0, 0, f.cost, Some(f.reason)))

object Appraisals
    extends View.Companion[AppraisalsView, AppraisalEvent, AppraisalRow](
      componentId = ComponentId("appraisals"),
      source = ChangeSource.eventsOf(AppraisalEntity),
      rowSerializer = Codecs.serializer[AppraisalRow]("appraisal-row")
    ):
  def create(ctx: ViewComponentContext) = new AppraisalsView
