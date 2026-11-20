package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{Codecs, EntityId}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.{SqlFragment, ViewClient}
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.*
import forecastbench.application.views.Questions
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
    val frozen = Refusals.invalidAs422(
      cohort(id)
        .call(CohortEntity.freeze)
        .invoke(Freeze(fixed, GathererVersion, ScoringMethod, closes, now))
    )
    client
      .forKeyValueEntity(EntityId(WatchedEntity.Id))
      .call(WatchedEntity.watch)
      .invoke(frozen.questions): Unit
    frozen.questions.foreach { q =>
      client
        .forWorkflow(EntityId(QuestionForecast.id(id, q, 0)))
        .call(QuestionForecast.start)
        .invoke(
          CohortEndpoint.plans(frozen, List((q, "", 0, 0))).head.copy(runs = Nil, gather = true)
        ): Unit
    }
    frozen
  }

  post("/{id}/forecast") { (id: String) =>
    val held    = read(id)
    val made    = recorded(held).map(r => RunKey(r.question, r.arm, r.repetition)).distinct
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

  post("/{id}/retry-failed") { (id: String) =>
    val held    = read(id)
    val runs    = recorded(held)
    val made    = runs.map(r => RunKey(r.question, r.arm, r.repetition)).distinct
    val started = cohort(id).call(CohortEntity.startForecast).invoke(Start(made, now))
    if !started.started then Respond(Forecasting.refused(Cohort.WindowEnded, started.notMade), 409)
    else
      val again = CohortEndpoint.toRetry(runs)
      if again.isEmpty then Respond(Forecasting.started(0, 0), 202)
      else
        val round = Iterator
          .from(2)
          .find(n =>
            client
              .forWorkflow(EntityId(CohortForecast.id(id, n)))
              .call(CohortForecast.get)
              .invoke()
              .round == 0
          )
          .get
        val plans = CohortEndpoint.plans(held, again)
        client
          .forWorkflow(EntityId(CohortForecast.id(id, round)))
          .call(CohortForecast.start)
          .invoke(Round(id, round, plans)): Unit
        Respond(Forecasting.started(round, again.size), 202)
  }

  /** Scores the cohort's runs again by another method; the earlier scores are kept. */
  post("/{id}/score") { (id: String) =>
    val held = read(id)
    val version =
      query.optional[Int]("method").getOrElse(held.scoringMethod.getOrElse(ScoringMethod))
    val method = Bench.edges.scoring
      .getOrElse(version, throw HttpProblem(422, s"there is no scoring method $version"))
    val runs = recorded(held)
    val scored = runs.count(r =>
      Scorer.question(client, r.question).exists(Scorer.scoreRun(client, r.id, method, _))
    )
    Scored(version, scored)
  }

  get("/{id}")((id: String) => read(id))

  /**
   * A question's pack in the cohort; 404 when it has none, saying why when its gathering failed.
   */
  get("/{id}/packs/{question}") { (id: String, question: String) =>
    val held = read(id)
    client
      .forEventSourcedEntity(EntityId(Ids.pack(id, question)))
      .call(PackEntity.get)
      .invoke()
      .pack
      .getOrElse {
        val why = held.packs.get(question) match
          case Some(n) if n.reason.isDefined => s"the pack was not gathered: ${n.reason.get}"
          case _                             => "the pack is not gathered yet"
        Refusals.notFound(s"no pack for '$question' in '$id': $why")
      }
  }

  /** A question's appraisal in the cohort, made or failed; 404 before it is held. */
  get("/{id}/appraisals/{question}") { (id: String, question: String) =>
    read(id): Unit
    val held = appraisal(id, question)
    if !held.held then Refusals.notFound(s"no appraisal for '$question' in '$id'")
    held
  }

  private def appraisal(cohort: String, question: String): AppraisalState =
    client
      .forEventSourcedEntity(EntityId(Ids.appraisal(cohort, question)))
      .call(AppraisalEntity.get)
      .invoke()

  private val reports = Reports(client, views)

  get("/{id}/report") { (id: String) =>
    reports.of(List(id))
  }

  get("/{id}/report.md")((id: String) => Reports.page(reports.of(List(id))))

  get("/{id}/comparison") { (id: String) =>
    val a = query.optional[String]("a").getOrElse(throw HttpProblem(422, "name the arm a"))
    val b = query.optional[String]("b").getOrElse(throw HttpProblem(422, "name the arm b"))
    reports.comparison(id, a, b)
  }

  get("/{id}/calibration") { (id: String) =>
    reports.calibration(
      id,
      query.optional[String]("arm").getOrElse(throw HttpProblem(422, "name the arm"))
    )
  }

  /** The cohort's own events, with each run's recording, in the order they happened. */
  get("/{id}/history") { (id: String) =>
    val own = cohort(id).call(CohortEntity.get).invoke()
    if own.cohort.isEmpty then Refusals.notFound(s"no cohort '$id'")
    val runs = recorded(own.cohort.get).map(r =>
      Happened(r.recordedAt, s"run ${r.id} recorded, ${r.ending.word}")
    )
    val appraisals = own.cohort.get.questions.flatMap { q =>
      val a = appraisal(id, q)
      a.made
        .map(m => Happened(m.madeAt, s"appraisal of $q made, ${m.claims.size} claims"))
        .orElse(
          a.failed.map(f => Happened(f.at, s"appraisal of $q failed: ${f.reason}"))
        )
    }
    // A stable sort: what happened at one instant keeps the order it was written in.
    (own.history ++ appraisals ++ runs).sortBy(_.at)
  }

  /**
   * Every run of the cohort, read from the runs themselves and not the view, which may not yet hold
   * the run that just failed: each question, arm and repetition, attempt after attempt, a bounded
   * number of them read at once.
   */
  private def recorded(c: Cohort): List[RunRecorded] =
    forecastbench.application.Bounded
      .map(c.runs) { (q, a, r) =>
        Iterator
          .from(1)
          .map(n =>
            client
              .forEventSourcedEntity(EntityId(Ids.run(c.id, q, a, r, n)))
              .call(RunEntity.get)
              .invoke()
              .run
          )
          .takeWhile(_.isDefined)
          .flatten
          .toList
      }
      .flatten

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
   * The runs to make again: for each question, arm and repetition whose runs are all failed, the
   * next attempt. One with a forecast, or one that abstained, is never made again (FR-015).
   */
  def toRetry(runs: List[RunRecorded]): List[(String, String, Int, Int)] =
    runs
      .groupBy(r => (r.question, r.arm, r.repetition))
      .toList
      .collect {
        case ((q, a, rep), rs) if rs.forall(_.ending.isFailed) =>
          (q, a, rep, rs.map(_.attempt).max + 1)
      }
      .sorted

  /** How many runs a scoring made, by which method. */
  final case class Scored(method: Int, scored: Int)

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

  /** The gatherer's version and the scoring method a cohort frozen now is fixed to. */
  val GathererVersion = forecastbench.gather.Gatherer.Version
  val ScoringMethod   = 1

  /** Every set of instructions a cohort with these arms runs. */
  def instructionsFor(arms: List[String]): List[String] =
    val own      = arms.flatMap(Arms.named).flatMap(_.instructions)
    val appraise = arms.exists(a => a == "graph" || a == "prose")
    (own ++
      // Every frozen cohort gathers (FR-016), so every one fixes the gatherer's queries.
      Some("gatherer-queries") ++
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
  given JsonValueCodec[Scored]           = Codecs.make
  given JsonValueCodec[Pack]             = Codecs.make
  given JsonValueCodec[AppraisalState]   = Codecs.make
  export ReportEndpoint.given
  given JsonValueCodec[List[Happened]] = Codecs.make
