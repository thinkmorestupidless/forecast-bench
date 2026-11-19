package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*
import forecastbench.report.Scoring

/** The runs of one question, in every cohort, by id. */
final case class QuestionRuns(runs: List[String])

/**
 * One per question. A consumer has no view client, so the scorer cannot ask the runs view for a
 * question's runs; the consumer of run events writes them here as they are recorded.
 */
final class QuestionRunsEntity extends KeyValueEntity[QuestionRuns]:
  def emptyState: QuestionRuns = QuestionRuns(Nil)

  def add(run: String): Effect[QuestionRuns] =
    if currentState.runs.contains(run) then effects.reply(currentState)
    else effects.updateState(QuestionRuns(currentState.runs :+ run)).thenReplyState

  def get: ReadOnlyEffect[QuestionRuns] = effects.reply(currentState)

object QuestionRunsEntity
    extends KeyValueEntity.Companion[QuestionRunsEntity, QuestionRuns](
      componentId = ComponentId("question-runs"),
      stateSerializer = Codecs.serializer[QuestionRuns]("question-runs")
    ):
  given Serializer[QuestionRuns] = stateSerializer

  def create(context: KeyValueEntityContext) = new QuestionRunsEntity

  val add = command("add")(_.add)
  val get = query("get")(_.get)

/**
 * Scoring (FR-033 to FR-035): a run with a forecast, on its question's outcome when that is yes or
 * no and settled, by a scoring method. Each method and outcome scores a run once; a revised outcome
 * scores it again, and the earlier score is kept.
 */
object Scorer:

  /** Scores one run by the method on its question's current outcome. True when a score was made. */
  def scoreRun(
      client: ComponentClient,
      runId: String,
      method: Scoring.Method,
      question: Question
  ): Boolean =
    val run = client.forEventSourcedEntity(EntityId(runId)).call(RunEntity.get).invoke()
    val made = for
      recorded <- run.run
      p        <- recorded.ending.forecast
      outcome  <- question.current.filter(_.settled)
      if !run.scores.exists(s => s.method == method.version && s.outcomeSeq == outcome.seq)
      (brier, log) <- Scoring.score(method, p, outcome.value)
    yield try
      client
        .forEventSourcedEntity(EntityId(runId))
        .call(RunEntity.score)
        .invoke(ScoreRun(method.version, outcome, brier, log, Bench.edges.clock.now)): Unit
      true
    catch case e: CommandError if e.code == ErrorCode.Conflict => false
    made.getOrElse(false)

  /** The method a cohort fixed when it was frozen. */
  def methodOf(client: ComponentClient, cohort: String): Option[Scoring.Method] =
    client
      .forEventSourcedEntity(EntityId(cohort))
      .call(CohortEntity.get)
      .invoke()
      .cohort
      .flatMap(_.scoringMethod)
      .flatMap(Bench.edges.scoring.get)

  def question(client: ComponentClient, id: String): Option[Question] =
    client.forEventSourcedEntity(EntityId(id)).call(QuestionEntity.get).invoke().question

  /** Every run of a question, each by its own cohort's method. */
  def scoreQuestion(client: ComponentClient, q: Question): Int =
    val runs = client.forKeyValueEntity(EntityId(q.id)).call(QuestionRunsEntity.get).invoke().runs
    runs.count { id =>
      val cohort =
        client.forEventSourcedEntity(EntityId(id)).call(RunEntity.get).invoke().run.map(_.cohort)
      cohort.flatMap(methodOf(client, _)).exists(scoreRun(client, id, _, q))
    }

/**
 * On a run recorded: noted under its question, and scored at once if the outcome is already known.
 */
final class ScoreOnRecord(ctx: ConsumerContext) extends Consumer[RunEvent, Nothing]:
  private val client = ctx.componentClient

  def onMessage(event: RunEvent): Effect = event match
    case RunEvent.Recorded(run) =>
      client
        .forKeyValueEntity(EntityId(run.question))
        .call(QuestionRunsEntity.add)
        .invoke(run.id): Unit
      for
        q      <- Scorer.question(client, run.question)
        method <- Scorer.methodOf(client, run.cohort)
      do Scorer.scoreRun(client, run.id, method, q): Unit
      effects.done()
    case _: RunEvent.Scored => effects.ignore()

object ScoreOnRecord
    extends Consumer.Companion[ScoreOnRecord, RunEvent, Nothing](
      componentId = ComponentId("score-on-record"),
      source = ChangeSource.eventsOf(RunEntity)
    ):
  def create(ctx: ConsumerContext) = new ScoreOnRecord(ctx)

/** On an outcome read: the question's runs scored on it. */
final class ScoreOnOutcome(ctx: ConsumerContext) extends Consumer[QuestionEvent, Nothing]:
  private val client = ctx.componentClient

  def onMessage(event: QuestionEvent): Effect = event match
    case _: QuestionEvent.OutcomeRead =>
      Scorer.question(client, messageContext.subject).foreach(Scorer.scoreQuestion(client, _))
      effects.done()
    case _ => effects.ignore()

object ScoreOnOutcome
    extends Consumer.Companion[ScoreOnOutcome, QuestionEvent, Nothing](
      componentId = ComponentId("score-on-outcome"),
      source = ChangeSource.eventsOf(QuestionEntity)
    ):
  def create(ctx: ConsumerContext) = new ScoreOnOutcome(ctx)
