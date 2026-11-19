package forecastbench.application.views

import com.thinkmorestupidless.ankka.core.{Codecs, ComponentId}
import com.thinkmorestupidless.ankka.runtime.{SqlFragment, ViewClient}
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.*
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.application.RunEntity
import forecastbench.domain.*

/** A run as recorded, with every score it has been given. */
final case class RunRow(run: RunRecorded, scores: List[Score])

final class RunsView extends View[RunEvent, RunRow]:

  def onChange(event: RunEvent): Effect = event match
    case RunEvent.Recorded(run) => effects.updateRow(RunRow(run, Nil))
    case RunEvent.Scored(score) =>
      rowState.fold(effects.ignore())(r => effects.updateRow(r.copy(scores = r.scores :+ score)))

object Runs
    extends View.Companion[RunsView, RunEvent, RunRow](
      componentId = ComponentId("runs"),
      source = ChangeSource.eventsOf(RunEntity),
      rowSerializer = Codecs.serializer[RunRow]("run-row")
    ):
  def create(ctx: ViewComponentContext) = new RunsView

  /** Far past any cohort's runs: a report reads them all (research R4). */
  val Every = 1000000

  /** The runs of a cohort, of an arm of it, or of a question, in the order of their ids. */
  def of(
      views: ViewClient,
      cohort: Option[String],
      arm: Option[String],
      question: Option[String]
  ): Vector[RunRow] =
    val conditions = List(
      cohort.map(c => jsonText("run", "cohort") ++ sql" = $c"),
      arm.map(a => jsonText("run", "arm") ++ sql" = $a"),
      question.map(q => jsonText("run", "question") ++ sql" = $q")
    ).flatten
    views
      .forView(Runs)
      .ordered(
        conditions
          .reduceOption((a, b) => a ++ SqlFragment.raw(" AND ") ++ b)
          .getOrElse(SqlFragment.raw("TRUE")),
        SqlFragment.raw("row_key"),
        limit = Every
      )
