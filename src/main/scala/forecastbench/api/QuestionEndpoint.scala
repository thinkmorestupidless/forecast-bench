package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.{SqlFragment, ViewClient}
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.QuestionEntity
import forecastbench.application.views.{QuestionRow, Questions}
import forecastbench.domain.{Outcome, Question}

/**
 * The questions held. There is no route by which anyone states an outcome: `POST
 * /questions/{id}/outcome` is refused with 405 because only `GET` is served there (FR-032).
 */
final class QuestionEndpoint(client: ComponentClient, views: ViewClient)
    extends HttpEndpoint("/questions"):
  import QuestionEndpoint.given

  val acl: Acl = Acl.allowCallers(Callers.self)

  get("/") { () =>
    val conditions = List(
      query.optional[String]("venue").map(v => jsonText("venue") ++ sql" = $v"),
      query.optional[String]("standing").map(v => jsonText("standing") ++ sql" = $v"),
      query.optional[String]("domain").map(v => jsonText("domain") ++ sql" = $v"),
      query.optional[Boolean]("waiting").map(v => jsonText("waiting") ++ sql" = ${v.toString}")
    ).flatten
    val where = conditions.reduceOption((a, b) => a ++ SqlFragment.raw(" AND ") ++ b)
    views
      .forView(Questions)
      .ordered(where.getOrElse(SqlFragment.raw("TRUE")), SqlFragment.raw("row_key"), limit = 100000)
      .toList
  }

  get("/{id}")((id: String) => question(id))

  get("/{id}/outcome")((id: String) => question(id).current)

  private def question(id: String): Question =
    client
      .forEventSourcedEntity(EntityId(id))
      .call(QuestionEntity.get)
      .invoke()
      .question
      .getOrElse(Refusals.notFound(s"no question '$id'"))

object QuestionEndpoint:
  given JsonValueCodec[List[QuestionRow]] = Codecs.make
  given JsonValueCodec[Question]          = Codecs.make
  given JsonValueCodec[Option[Outcome]]   = Codecs.make
