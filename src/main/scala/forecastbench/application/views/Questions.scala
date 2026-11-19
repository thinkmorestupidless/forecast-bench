package forecastbench.application.views

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.application.QuestionEntity
import forecastbench.domain.*
import forecastbench.domain.QuestionEvent.*

import java.time.Instant

/** A question without its outcome list, with its current outcome. */
final case class QuestionRow(
    id: String,
    venue: String,
    venueId: String,
    eventGroup: String,
    statement: String,
    criteria: String,
    closesAt: Instant,
    category: Option[String],
    domain: String,
    standing: Standing,
    importedAt: Instant,
    waiting: Boolean,
    outcome: Option[Outcome]
)

final class QuestionsView extends View[QuestionEvent, QuestionRow]:

  def onChange(event: QuestionEvent): Effect = event match
    case e: QuestionImported =>
      effects.updateRow(
        QuestionRow(
          e.id,
          e.venue,
          e.venueId,
          e.eventGroup,
          e.statement,
          e.criteria,
          e.closesAt,
          e.category,
          e.domain,
          e.standing,
          e.importedAt,
          waiting = true,
          outcome = None
        )
      )
    case QuestionRefreshed(closesAt, criteria, _) =>
      rowState.fold(effects.ignore())(r =>
        effects.updateRow(r.copy(closesAt = closesAt, criteria = criteria))
      )
    case OutcomeRead(outcome) =>
      rowState.fold(effects.ignore())(r =>
        effects.updateRow(r.copy(waiting = false, outcome = Some(outcome)))
      )

object Questions
    extends View.Companion[QuestionsView, QuestionEvent, QuestionRow](
      componentId = ComponentId("questions"),
      source = ChangeSource.eventsOf(QuestionEntity),
      rowSerializer = Codecs.serializer[QuestionRow]("question-row")
    ):
  def create(ctx: ViewComponentContext) = new QuestionsView
