package forecastbench.application.views

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.application.PackEntity
import forecastbench.domain.PackGathered

import java.time.Instant

/** Each pack in brief: its cohort and question, how many documents and searches, and when. */
final case class PackRow(
    cohort: String,
    question: String,
    documents: Int,
    searches: Int,
    gatheredAt: Instant
)

final class PacksView extends View[PackGathered, PackRow]:
  def onChange(event: PackGathered): Effect =
    val p = event.pack
    effects.updateRow(
      PackRow(p.cohort, p.question, p.documents.size, p.searches.size, p.gatheredAt)
    )

object Packs
    extends View.Companion[PacksView, PackGathered, PackRow](
      componentId = ComponentId("packs"),
      source = ChangeSource.eventsOf(PackEntity),
      rowSerializer = Codecs.serializer[PackRow]("pack-row")
    ):
  def create(ctx: ViewComponentContext) = new PacksView
