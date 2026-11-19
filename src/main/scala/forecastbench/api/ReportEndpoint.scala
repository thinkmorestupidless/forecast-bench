package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.ViewClient
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.*
import forecastbench.application.views.Runs
import forecastbench.domain.*
import forecastbench.report.*

import java.nio.charset.StandardCharsets.UTF_8

/** The reports over several cohorts: `GET /reports?cohorts=a,b` and its page. */
final class ReportEndpoint(client: ComponentClient, views: ViewClient)
    extends HttpEndpoint("/reports"):
  import ReportEndpoint.given

  val acl: Acl = Acl.allowCallers(Callers.self)

  private val reports = Reports(client, views)

  get("/")(() => reports.of(cohortsAsked))

  private def cohortsAsked: List[String] =
    ReportEndpoint.cohortsAsked(query.optional[String]("cohorts"))

/**
 * The same report as a page, `GET /reports.md`: a prefix of its own, as the server routes by
 * prefix.
 */
final class ReportPageEndpoint(client: ComponentClient, views: ViewClient)
    extends HttpEndpoint("/reports.md"):

  val acl: Acl = Acl.allowCallers(Callers.self)

  private val reports = Reports(client, views)

  get("/")(() =>
    Reports.page(reports.of(ReportEndpoint.cohortsAsked(query.optional[String]("cohorts"))))
  )

object ReportEndpointNames:
  def cohortsAsked(named: Option[String]): List[String] =
    named
      .map(_.split(',').map(_.trim).filter(_.nonEmpty).toList)
      .filter(_.nonEmpty)
      .getOrElse(throw HttpProblem(422, "name the cohorts, separated by commas"))

/**
 * Reports, computed when asked for and never held (contracts/http.md): one cohort's, or several
 * cohorts' together. A cohort's own report routes are the cohort endpoint's, since two endpoints
 * cannot share the prefix `/cohorts`.
 */
final class Reports(client: ComponentClient, views: ViewClient):

  def of(ids: List[String]): Report =
    val cohorts = ids.map { id =>
      client
        .forEventSourcedEntity(EntityId(id))
        .call(CohortEntity.get)
        .invoke()
        .cohort
        .getOrElse(Refusals.notFound(s"no cohort '$id'"))
    }
    val questions = cohorts
      .flatMap(_.questions)
      .distinct
      .flatMap { q =>
        client
          .forEventSourcedEntity(EntityId(q))
          .call(QuestionEntity.get)
          .invoke()
          .question
          .map(q -> _)
      }
      .toMap
    val runs = cohorts.flatMap(c => Runs.of(views, Some(c.id), None, None).map(_.run))
    val version =
      cohorts.flatMap(_.scoringMethod).headOption.getOrElse(CohortEndpoint.ScoringMethod)
    val method = Bench.edges.scoring.getOrElse(version, Scoring.One)
    Report.build(cohorts, questions, runs, method, Bench.edges.clock.now)

  def comparison(id: String, a: String, b: String): Comparison =
    val r = of(List(id))
    r.comparisons
      .find(c => c.a == a && c.b == b)
      .orElse(r.comparisons.find(c => c.a == b && c.b == a).map(Reports.flip))
      .getOrElse(throw HttpProblem(422, s"the cohort does not name both '$a' and '$b'"))

  def calibration(id: String, arm: String): ArmCalibration =
    of(List(id)).calibration
      .find(_.arm == arm)
      .getOrElse(throw HttpProblem(422, s"the cohort does not name the arm '$arm'"))

object Reports:
  def page(r: Report): Bytes =
    Bytes("text/markdown; charset=utf-8", Markdown.render(r).getBytes(UTF_8))

  /** The comparison the other way round: the difference and its interval negated. */
  def flip(c: Comparison): Comparison =
    val interval = c.interval.map(i => Interval(-i.high, -i.low))
    c.copy(
      a = c.b,
      b = c.a,
      meanDifference = c.meanDifference.map(-_),
      interval = interval,
      says = interval.fold(c.says)(Comparison.says(c.b, c.a, _))
    )

object ReportEndpoint:
  export ReportEndpointNames.cohortsAsked
  given JsonValueCodec[Report]         = Codecs.make
  given JsonValueCodec[Comparison]     = Codecs.make
  given JsonValueCodec[ArmCalibration] = Codecs.make
