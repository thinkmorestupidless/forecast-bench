package forecastbench.application.views

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.application.CohortEntity
import forecastbench.domain.*

/**
 * Every cohort, as its events make it. A frozen cohort's row names its questions, which is how the
 * outcome reader finds what to read without asking every cohort.
 */
final case class CohortRow(
    id: String,
    standing: Standing,
    status: CohortStatus,
    model: String,
    questions: List[String],
    arms: List[String],
    repetitions: Int,
    budget: Option[BigDecimal],
    spent: BigDecimal,
    window: Option[Window],
    notMade: Int
)

final class CohortsView extends View[CohortEvent, CohortRow]:

  def onChange(event: CohortEvent): Effect =
    val before = rowState.map(r =>
      // Only what the row keeps is needed to apply the next event to it.
      Cohort(
        r.id,
        r.standing,
        r.model,
        None,
        event.at,
        r.questions,
        r.arms,
        r.window,
        r.repetitions,
        r.budget,
        r.status,
        None,
        Map.empty,
        None,
        None,
        r.spent,
        Map.empty,
        Nil,
        Map.empty,
        None,
        Nil
      )
    )
    Cohort(before, event) match
      case None => effects.ignore()
      case Some(c) =>
        val notMade = rowState.fold(0)(_.notMade) + (event match
          case _: CohortEvent.RunNotMade => 1
          case _                         => 0)
        effects.updateRow(
          CohortRow(
            c.id,
            c.standing,
            c.status,
            c.model,
            c.questions,
            c.arms,
            c.repetitions,
            c.budget,
            c.spent,
            c.window,
            notMade
          )
        )

object Cohorts
    extends View.Companion[CohortsView, CohortEvent, CohortRow](
      componentId = ComponentId("cohorts"),
      source = ChangeSource.eventsOf(CohortEntity),
      rowSerializer = Codecs.serializer[CohortRow]("cohort-row")
    ):
  def create(ctx: ViewComponentContext) = new CohortsView
