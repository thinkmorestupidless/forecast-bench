package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*
import forecastbench.venues.VenueQuestion

import scala.concurrent.duration.*

/** One run the workflow is to make. */
final case class RunPlan(arm: String, repetition: Int, attempt: Int)

/** What the cohort fixed, carried to every step so none reads the cohort again. */
final case class QuestionPlan(
    cohort: String,
    question: String,
    model: String,
    pretendedTime: Option[java.time.Instant],
    instructions: Map[String, InstructionRef],
    runs: List[RunPlan],
    parent: Option[String]
)

/** What the venue said when the workflow read it: the reading, or why there was none. */
final case class VenueSaid(reading: Option[VenueReading], unreadable: Boolean)

final case class QuestionForecastState(
    plan: Option[QuestionPlan],
    venue: Option[VenueSaid],
    pending: List[RunPlan],
    made: List[String],
    stopped: Option[String],
    ended: Boolean
)

/**
 * The forecast of one question in one cohort (research R5): the venue read again, then each arm's
 * runs, one run a step so no step outlives its timeout, then the cohort's workflow told it ended. A
 * run's cost is reserved on the cohort before its model is called; a refusal ends the workflow. No
 * step is retried: a failure is a failed run, made again only when the experimenter asks.
 */
final class QuestionForecast(context: WorkflowContext) extends Workflow[QuestionForecastState]:
  import QuestionForecast.*

  private val client = context.componentClient

  def emptyState: QuestionForecastState = QuestionForecastState(None, None, Nil, Nil, None, false)

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(5.minutes)
      .defaultRecovery(RecoverStrategy.maxRetries(0))
      .build

  def start(plan: QuestionPlan): Effect[Done] =
    if currentState.plan.isDefined then effects.reply(Done)
    else
      val order = Arms.all.map(_.name)
      val runs  = plan.runs.sortBy(r => (order.indexOf(r.arm), r.repetition))
      effects
        .updateState(currentState.copy(plan = Some(plan), pending = runs))
        .transitionTo(QuestionForecast.refresh.ref)
        .thenReply(Done)

  def get: ReadOnlyEffect[QuestionForecastState] = effects.reply(currentState)

  private def plan = currentState.plan.get
  private def now  = Bench.edges.clock.now

  private def question: Question =
    client
      .forEventSourcedEntity(EntityId(plan.question))
      .call(QuestionEntity.get)
      .invoke()
      .question
      .getOrElse(throw IllegalStateException(s"no question '${plan.question}'"))

  /** The question as its venue has it now: its probability for venue-price, and any outcome. */
  def refreshStep: StepEffect =
    val q = question
    val said = Bench.edges.venues.get(q.venue).map(_.one(q.venueId)) match
      case Some(Right(Some(vq))) =>
        readOutcome(vq)
        VenueSaid(vq.probability, unreadable = false)
      case Some(Right(None)) => VenueSaid(None, unreadable = false)
      case _                 => VenueSaid(None, unreadable = true)
    next(currentState.copy(venue = Some(said)))

  private def readOutcome(vq: VenueQuestion): Unit =
    vq.outcome.foreach { value =>
      client
        .forEventSourcedEntity(EntityId(plan.question))
        .call(QuestionEntity.readOutcome)
        .invoke(ReadOutcome(OutcomeReading(value, vq.disputed, vq.resolvedAt), now)): Unit
    }

  def venuePriceStep: StepEffect =
    val run  = currentState.pending.head
    val said = currentState.venue.getOrElse(VenueSaid(None, unreadable = true))
    val ending = said.reading match
      case Some(r)                 => Ending.Forecast(r.probability)
      case None if said.unreadable => Ending.Failed(VenueUnreadable)
      case None                    => Ending.Abstained(NoVenueProbability)
    val recorded = blank(run).copy(ending = ending, venue = said.reading)
    record(recorded)
    next(made(run, recorded.id))

  def modelStep: StepEffect =
    val run = currentState.pending.head
    val arm = Arms.named(run.arm).get
    val q   = question
    val asked =
      QuestionGiven(q.statement, q.criteria, q.closesAt, plan.pretendedTime.getOrElse(now))
    arm.`given`(asked, None, None) match
      case Left(Abstain(reason)) =>
        val recorded = blank(run).copy(ending = Ending.Abstained(reason), model = Some(plan.model))
        record(recorded)
        next(made(run, recorded.id))
      case Right(userGiven) =>
        val ref    = arm.instructions.flatMap(plan.instructions.get)
        val system = ref.flatMap(r => Instructions.at(arm.instructions.get, r.version)).map(_.text)
        val input  = userGiven.copy(system = system.getOrElse(""))
        val runId  = Ids.run(plan.cohort, plan.question, run.arm, run.repetition, run.attempt)
        val models = Bench.edges.models
        val reservation = cohort
          .call(CohortEntity.reserve)
          .invoke(
            Reserve(
              RunKey(plan.question, run.arm, run.repetition),
              runId,
              Estimate.of(models, plan.model, input.text),
              now
            )
          )
        if !reservation.granted then
          stepEffects
            .updateState(currentState.copy(stopped = reservation.reason, pending = Nil))
            .thenTransitionTo(QuestionForecast.finish.ref)
        else
          val givenRef  = GivenBucket.keep(context.storage, input.text)
          val forecast  = Forecaster.forecast(models, plan.model, input)
          val answerRef = forecast.answered.map(GivenBucket.keep(context.storage, _))
          val entry     = models.entry(plan.model)
          val recorded = blank(run).copy(
            ending = forecast.ending,
            model = Some(plan.model),
            effort = entry.flatMap(_.effort),
            thinking = entry.map(_.thinking),
            instructions = ref,
            `given` = Some(givenRef),
            answered = answerRef,
            reasons = forecast.reasons,
            usage = Some(forecast.usage),
            cost = forecast.cost,
            latencyMs = forecast.latencyMs
          )
          record(recorded)
          cohort.call(CohortEntity.settle).invoke(Settle(runId, forecast.cost, now)): Unit
          next(made(run, runId))

  /** Tells the cohort's workflow this question is done, and ends. */
  def finishStep: StepEffect =
    plan.parent.foreach { parent =>
      client
        .forWorkflow(EntityId(parent))
        .call(CohortForecast.questionEnded)
        .invoke(plan.question): Unit
    }
    stepEffects.updateState(currentState.copy(ended = true)).thenEnd

  private def cohort = client.forEventSourcedEntity(EntityId(plan.cohort))

  private def made(run: RunPlan, id: String) =
    currentState.copy(
      pending = currentState.pending.filterNot(_ == run),
      made = currentState.made :+ id
    )

  /** On to the step of the next run, or to the end. */
  private def next(state: QuestionForecastState): StepEffect =
    val to = state.pending.headOption match
      case None                                     => QuestionForecast.finish.ref
      case Some(r) if r.arm == Arms.venuePrice.name => QuestionForecast.venuePrice.ref
      case Some(_)                                  => QuestionForecast.model.ref
    stepEffects.updateState(state).thenTransitionTo(to)

  private def blank(run: RunPlan): RunRecorded = RunRecorded(
    id = Ids.run(plan.cohort, plan.question, run.arm, run.repetition, run.attempt),
    cohort = plan.cohort,
    question = plan.question,
    arm = run.arm,
    repetition = run.repetition,
    attempt = run.attempt,
    ending = Ending.Failed("not made"),
    recordedAt = now,
    model = None,
    effort = None,
    thinking = None,
    instructions = None,
    pack = None,
    appraisal = None,
    `given` = None,
    answered = None,
    reasons = Nil,
    usage = None,
    cost = BigDecimal(0),
    appraisalShare = BigDecimal(0),
    latencyMs = 0,
    venue = None
  )

  /** The run, recorded once; a run refused as already recorded or too late is left as it is. */
  private def record(run: RunRecorded): Unit =
    val q = question
    try
      client
        .forEventSourcedEntity(EntityId(run.id))
        .call(RunEntity.record)
        .invoke(Record(run, QuestionNow(q.standing, q.closesAt, q.current.isDefined))): Unit
    catch case e: CommandError if e.code == ErrorCode.Conflict => ()

object QuestionForecast
    extends Workflow.Companion[QuestionForecast, QuestionForecastState](
      componentId = ComponentId("question-forecast"),
      stateSerializer = Codecs.serializer[QuestionForecastState]("question-forecast")
    ):
  given Serializer[QuestionPlan] = Codecs.serializer[QuestionPlan]("question-plan")

  val VenueUnreadable    = "the venue could not be read"
  val NoVenueProbability = "the venue gave no probability"

  /** One workflow for a question in a cohort for each round of forecasting: 1, then each retry. */
  def id(cohort: String, question: String, round: Int): String =
    s"${Ids.pack(cohort, question)}.$round"

  def create(context: WorkflowContext) = new QuestionForecast(context)

  val refresh    = step("refresh")(_.refreshStep)
  val venuePrice = step("venue-price")(_.venuePriceStep)
  val model      = step("model-alone")(_.modelStep)
  val finish     = step("finish")(_.finishStep)

  val start = command("start")(_.start)
  val get   = query("get")(_.get)
