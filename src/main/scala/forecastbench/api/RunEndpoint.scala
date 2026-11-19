package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.sdk.{ComponentClient, ObjectStorage}
import com.thinkmorestupidless.ankka.runtime.ViewClient
import forecastbench.application.*
import forecastbench.application.views.{RunRow, Runs}
import forecastbench.domain.*

/**
 * Runs, read only (contracts/http.md). There is no route that changes or removes a run: a `PUT`,
 * `PATCH` or `DELETE` to one is answered 405, because only `GET` is defined at that path.
 */
final class RunEndpoint(client: ComponentClient, views: ViewClient, storage: ObjectStorage)
    extends HttpEndpoint("/runs"):
  import RunEndpoint.{*, given}

  val acl: Acl = Acl.allowCallers(Callers.self)

  get("/") { () =>
    Runs
      .of(
        views,
        query.optional[String]("cohort"),
        query.optional[String]("arm"),
        query.optional[String]("question")
      )
      .toList
  }

  get("/{id}") { (id: String) =>
    val state    = held(id)
    val run      = state.run.get
    val input    = run.`given`.flatMap(GivenBucket.read(storage, _))
    val answered = run.answered.flatMap(GivenBucket.read(storage, _))
    RunRead(
      run,
      state.scores,
      input.map(_.text),
      answered.map(_.text),
      intact =
        (run.`given`.isEmpty || input.exists(_.intact)) && (run.answered.isEmpty || answered.exists(
          _.intact
        ))
    )
  }

  post("/{id}/reproduce") { (id: String) =>
    val run = held(id).run.get
    val model =
      run.model.getOrElse(throw HttpProblem(422, "the run names no model: it was not a model's"))
    val kept = run.`given`
      .flatMap(GivenBucket.read(storage, _))
      .getOrElse(throw HttpProblem(422, "what the run was given is not in the bucket"))
    if !kept.intact then
      throw HttpProblem(422, "what the run was given no longer matches its digest")
    val asked = Given
      .parse(kept.text)
      .getOrElse(throw HttpProblem(422, "what the run was given cannot be read"))
    val models = Bench.edges.models
    if models.entry(model).isEmpty then throw HttpProblem(422, s"the bench has no model '$model'")
    val f = Forecaster.forecast(models, model, asked)
    Reproduced(f.answered, f.ending.forecast, f.usage, f.latencyMs)
  }

  private def held(id: String): RunState =
    val state = client.forEventSourcedEntity(EntityId(id)).call(RunEntity.get).invoke()
    if state.run.isEmpty then Refusals.notFound(s"no run '$id'")
    state

object RunEndpoint:
  final case class RunRead(
      run: RunRecorded,
      scores: List[Score],
      `given`: Option[String],
      answered: Option[String],
      intact: Boolean
  )
  final case class Reproduced(
      answered: Option[String],
      probability: Option[Double],
      usage: Usage,
      latencyMs: Long
  )

  given JsonValueCodec[RunRead]      = Codecs.make
  given JsonValueCodec[Reproduced]   = Codecs.make
  given JsonValueCodec[List[RunRow]] = Codecs.make
