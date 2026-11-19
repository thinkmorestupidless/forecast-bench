package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.{SqlFragment, ViewClient}
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.*
import forecastbench.application.views.{Questions, Runs}
import forecastbench.domain.*

import java.time.Instant

/** A cohort from its draft to its freeze, and everything read from it (contracts/http.md). */
final class CohortEndpoint(client: ComponentClient, views: ViewClient)
    extends HttpEndpoint("/cohorts"):
  import CohortEndpoint.{*, given}

  val acl: Acl = Acl.allowCallers(Callers.self)

  private def now: Instant = Bench.edges.clock.now

  private def cohort(id: String) = client.forEventSourcedEntity(EntityId(id))

  private def model(name: String): String =
    if Bench.edges.settings.models.contains(name) then name
    else throw HttpProblem(422, s"the bench has no model '$name'")

  postBody("/") { (body: DraftRequest) =>
    val id = Ids.cohort(body.id).fold(why => throw HttpProblem(422, why), identity)
    val standing = Standing
      .parse(body.standing)
      .getOrElse(
        throw HttpProblem(422, s"'${body.standing}' is neither prospective nor development")
      )
    val drafted = Refusals.invalidAs422(
      cohort(id)
        .call(CohortEntity.draft)
        .invoke(Draft(standing, model(body.model), body.pretendedTime, now))
    )
    Respond(drafted, 201)
  }

  putBody("/{id}") { (id: String, body: ChangeRequest) =>
    val change = CohortChange(
      model = body.model.map(model),
      window = body.window,
      repetitions = body.repetitions,
      budget = body.budget
    )
    Refusals.invalidAs422(cohort(id).call(CohortEntity.change).invoke(Change(change, now)))
  }

  postBody("/{id}/questions") { (id: String, body: QuestionsRequest) =>
    val held = read(id)
    val ids  = body.questions.getOrElse(Nil) ++ body.select.toList.flatMap(selected(held, _))
    val entries = ids.distinct.map { q =>
      val question = client
        .forEventSourcedEntity(EntityId(q))
        .call(QuestionEntity.get)
        .invoke()
        .question
        .getOrElse(throw HttpProblem(422, s"no question '$q'"))
      QuestionEntry(question.id, question.standing, question.closesAt)
    }
    Refusals.invalidAs422(
      cohort(id).call(CohortEntity.addQuestions).invoke(AddQuestions(entries, now))
    )
  }

  delete("/{id}/questions/{question}") { (id: String, question: String) =>
    Refusals.invalidAs422(
      cohort(id).call(CohortEntity.removeQuestion).invoke(RemoveQuestion(question, now))
    )
  }

  postBody("/{id}/arms") { (id: String, body: ArmsRequest) =>
    Refusals.invalidAs422(cohort(id).call(CohortEntity.addArms).invoke(AddArms(body.arms, now)))
  }

  delete("/{id}/arms/{arm}") { (id: String, arm: String) =>
    Refusals.invalidAs422(cohort(id).call(CohortEntity.removeArm).invoke(RemoveArm(arm, now)))
  }

  postBody("/{id}/freeze") { (id: String, body: FreezeRequest) =>
    val held  = read(id)
    val named = body.instructions.getOrElse(Map.empty)
    val fixed = CohortEndpoint
      .instructionsFor(held.arms)
      .map { name =>
        name -> Instructions
          .fixed(name, named.get(name))
          .fold(why => throw HttpProblem(422, why), _.ref)
      }
      .toMap
    val closes = held.questions.flatMap { q =>
      client
        .forEventSourcedEntity(EntityId(q))
        .call(QuestionEntity.get)
        .invoke()
        .question
        .map(q -> _.closesAt)
    }.toMap
    Refusals.invalidAs422(
      cohort(id)
        .call(CohortEntity.freeze)
        .invoke(Freeze(fixed, GathererVersion, ScoringMethod, closes, now))
    )
  }

  post("/{id}/forecast") { (id: String) =>
    val held = read(id)
    val made = Runs
      .of(views, Some(id), None, None)
      .map(r => RunKey(r.run.question, r.run.arm, r.run.repetition))
      .distinct
      .toList
    val started = cohort(id).call(CohortEntity.startForecast).invoke(Start(made, now))
    if !started.started then
      Respond(
        Forecasting.refused(
          started.notMade.headOption.fold(Cohort.WindowEnded)(_.reason),
          started.notMade
        ),
        409
      )
    else
      val have = made.map(k => (k.question, k.arm, k.repetition)).toSet
      val plans = CohortEndpoint.plans(
        held,
        held.runs.filterNot(have.contains).map((q, a, r) => (q, a, r, 1))
      )
      client
        .forWorkflow(EntityId(CohortForecast.id(id, 1)))
        .call(CohortForecast.start)
        .invoke(Round(id, 1, plans)): Unit
      Respond(Forecasting.started(1, plans.map(_.runs.size).sum), 202)
  }

  get("/{id}")((id: String) => read(id))

  private def read(id: String): Cohort =
    cohort(id)
      .call(CohortEntity.get)
      .invoke()
      .cohort
      .getOrElse(Refusals.notFound(s"no cohort '$id'"))

  private def selected(held: Cohort, s: Select): List[String] =
    val conditions = List(
      Some(jsonText("venue") ++ sql" = ${s.venue}"),
      Some(jsonText("standing") ++ sql" = ${held.standing.word}"),
      s.domain.map(d => jsonText("domain") ++ sql" = $d"),
      s.closesAfter.map(t =>
        SqlFragment.raw("(payload::jsonb->>'closesAt')::timestamptz > ") ++ sql"$t"
      ),
      s.closesBefore.map(t =>
        SqlFragment.raw("(payload::jsonb->>'closesAt')::timestamptz < ") ++ sql"$t"
      )
    ).flatten
    views
      .forView(Questions)
      .ordered(
        conditions.reduce((a, b) => a ++ SqlFragment.raw(" AND ") ++ b),
        SqlFragment.raw("row_key"),
        limit = s.limit.getOrElse(500)
      )
      .map(_.id)
      .toList

object CohortEndpoint:

  /** The question workflows' plans for these runs: question, arm, repetition and attempt. */
  def plans(c: Cohort, runs: List[(String, String, Int, Int)]): List[QuestionPlan] =
    runs.groupBy(_._1).toList.sortBy(_._1).map { (q, rs) =>
      QuestionPlan(
        c.id,
        q,
        c.model,
        c.pretendedTime,
        c.instructions,
        rs.map((_, a, r, n) => RunPlan(a, r, n)).sortBy(p => (p.arm, p.repetition)),
        None
      )
    }

  /**
   * A round of forecasting started and how many runs it is to make; or a refusal, as every refusal
   * reads, with the runs it means.
   */
  final case class Forecasting(
      status: Int,
      round: Option[Int],
      runs: Option[Int],
      error: Option[String],
      notMade: List[NotMade]
  )
  object Forecasting:
    def started(round: Int, runs: Int) = Forecasting(202, Some(round), Some(runs), None, Nil)
    def refused(why: String, notMade: List[NotMade]) =
      Forecasting(409, None, None, Some(why), notMade)

  /** The only gatherer and the only scoring method this feature has. */
  val GathererVersion = 1
  val ScoringMethod   = 1

  /** Every set of instructions a cohort with these arms runs. */
  def instructionsFor(arms: List[String]): List[String] =
    val own      = arms.flatMap(Arms.named).flatMap(_.instructions)
    val evidence = arms.flatMap(Arms.named).exists(_.takesEvidence)
    val appraise = arms.exists(a => a == "graph" || a == "prose")
    (own ++
      Option.when(evidence)("gatherer-queries") ++
      Option.when(appraise)("appraisal-reader") ++
      Option.when(appraise)("appraisal-open-points")).distinct

  final case class DraftRequest(
      id: String,
      standing: String,
      model: String,
      pretendedTime: Option[Instant]
  )
  final case class ChangeRequest(
      model: Option[String],
      window: Option[Window],
      repetitions: Option[Int],
      budget: Option[BigDecimal]
  )
  final case class Select(
      venue: String,
      domain: Option[String],
      closesAfter: Option[Instant],
      closesBefore: Option[Instant],
      limit: Option[Int]
  )
  final case class QuestionsRequest(questions: Option[List[String]], select: Option[Select])
  final case class ArmsRequest(arms: List[String])
  final case class FreezeRequest(instructions: Option[Map[String, Int]])

  given JsonValueCodec[DraftRequest]     = Codecs.make
  given JsonValueCodec[ChangeRequest]    = Codecs.make
  given JsonValueCodec[QuestionsRequest] = Codecs.make
  given JsonValueCodec[ArmsRequest]      = Codecs.make
  given JsonValueCodec[FreezeRequest]    = Codecs.make
  given JsonValueCodec[Cohort]           = Codecs.make
  given JsonValueCodec[Forecasting]      = Codecs.make
