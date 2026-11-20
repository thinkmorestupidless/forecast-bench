package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*
import forecastbench.reasoning.Reasoning
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
    parent: Option[String],
    /**
     * Round 0 of a question, started at freeze: it gathers the pack, appraises it when the cohort
     * names the graph or the prose arm, and makes no run.
     */
    gather: Boolean = false
)

/** What the venue said when the workflow read it: the reading, or why there was none. */
final case class VenueSaid(reading: Option[VenueReading], unreadable: Boolean)

final case class QuestionForecastState(
    plan: Option[QuestionPlan],
    venue: Option[VenueSaid],
    pending: List[RunPlan],
    made: List[String],
    stopped: Option[String],
    ended: Boolean,
    /** How many times the run in hand has waited for its pack or its appraisal. */
    waited: Int = 0
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

  def emptyState: QuestionForecastState =
    QuestionForecastState(None, None, Nil, Nil, None, false)

  /**
   * Each step's timeout covers the waits it bounds: a model step reads the graph (three reads of up
   * to 30 seconds and two pauses of 10) and calls the model (up to `Forecaster.Timeout`); the
   * appraisal reads every document at once and then asks for the open points (`Appraiser.Timeout`
   * each); the gathering asks for queries (`Gatherer.Timeout`) and then searches, each search with
   * its retries. A step that fails or outlives its time anyway fails over to `abandon`, which
   * leaves what it was doing settled and goes on, so the cohort's round always ends.
   */
  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(StepTimeout)
      .stepTimeout(QuestionForecast.appraising, AppraiseTimeout)
      .stepTimeout(QuestionForecast.gathering, GatherTimeout)
      .defaultRecovery(RecoverStrategy.maxRetries(0).failoverTo(QuestionForecast.abandoning))
      .stepRecovery(QuestionForecast.abandoning, RecoverStrategy.maxRetries(2))
      .stepRecovery(QuestionForecast.finish, RecoverStrategy.maxRetries(2))
      .build

  def start(plan: QuestionPlan): Effect[Done] =
    if currentState.plan.isDefined then effects.reply(Done)
    else
      val order = Arms.all.map(_.name)
      val runs  = plan.runs.sortBy(r => (order.indexOf(r.arm), r.repetition))
      effects
        .updateState(currentState.copy(plan = Some(plan), pending = runs))
        .transitionTo(
          if plan.gather then QuestionForecast.gathering.ref else QuestionForecast.refresh.ref
        )
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

  /**
   * Why a run of a prospective question is no longer made: it closed, or has an outcome. Asked
   * before the model is called, so nothing is spent on a run the run entity would refuse.
   */
  private def tooLate(q: Question): Option[String] =
    if q.standing != Standing.Prospective then None
    else if !now.isBefore(q.closesAt) then Some(Run.Closed)
    else if q.current.isDefined then Some(Run.HasOutcome)
    else None

  private def notMade(run: RunPlan, why: String): StepEffect =
    cohort
      .call(CohortEntity.notMade)
      .invoke(NotMaking(RunKey(plan.question, run.arm, run.repetition), why, now)): Unit
    next(currentState.copy(pending = currentState.pending.filterNot(_ == run)))

  def venuePriceStep: StepEffect =
    val run = currentState.pending.head
    tooLate(question).fold(venuePrice(run))(notMade(run, _))

  private def venuePrice(run: RunPlan): StepEffect =
    val said = currentState.venue.getOrElse(VenueSaid(None, unreadable = true))
    val ending = said.reading match
      case Some(r)                 => Ending.Forecast(r.probability)
      case None if said.unreadable => Ending.Failed(VenueUnreadable)
      case None                    => Ending.Abstained(NoVenueProbability)
    val recorded = blank(run).copy(ending = ending, venue = said.reading)
    record(recorded)
    next(made(run, recorded.id))

  /**
   * A model arm's run. An arm that takes evidence waits for its question's pack (FR-018): it pauses
   * until the cohort notes the pack, reads it once it is gathered or empty, and is not made when
   * the pack failed. The graph and prose arms then wait the same way for the appraisal, and read
   * its content back from the graph before they are given it (research R8).
   */
  def modelStep: StepEffect =
    val run = currentState.pending.head
    val q   = question
    val arm = Arms.named(run.arm).get
    tooLate(q) match
      case Some(why)                  => notMade(run, why)
      case None if !arm.takesEvidence => modelRun(run, q, None, None)
      case None =>
        val note = cohort.call(CohortEntity.get).invoke().cohort.flatMap(_.packs.get(plan.question))
        note match
          case None => waitFor(run, NoPackInTime)
          case Some(n) if n.failed =>
            notMade(run, s"the pack was not gathered: ${n.reason.getOrElse("no reason was given")}")
          case Some(_) =>
            val pack = heldPack
            if !Appraised.contains(run.arm) || pack.forall(_.empty) then
              modelRun(run, q, pack, None)
            else
              val appraisal = heldAppraisal
              appraisal.made match
                case Some(m) =>
                  readContent(m, pack.get) match
                    case Right(content) => modelRun(run, q, pack, Some(m -> content))
                    case Left(why)      => failedRun(run, why, pack)
                case None if appraisal.failed.isDefined => modelRun(run, q, pack, None)
                case None                               => waitFor(run, NoAppraisalInTime)

  /** Pauses the run in hand to look again; past the bound, it is not made, saying why. */
  private def waitFor(run: RunPlan, why: String): StepEffect =
    if currentState.waited >= MaxWaits then notMade(run, why)
    else
      stepEffects
        .updateState(currentState.copy(waited = currentState.waited + 1))
        .thenPause(PackWait, stepFor(run.arm))

  private def reasoning = forecastbench.reasoning.Reasoning(context.services("reasoning"))

  /**
   * The appraisal's content as the graph holds it, once the graph has caught up with it. Each claim
   * is waited for: the graph topic has several partitions, so the last claim written can be applied
   * before the ones before it. The cases read must hold the appraisal's claims exactly. A read the
   * graph has not caught up with, inside the read's one wait limit, is made again after a pause, up
   * to the attempts the settings name (contracts/reasoning.md).
   */
  private def readContent(m: AppraisalMade, pack: Pack): Either[String, AppraisalContent] =
    val r        = reasoning
    val how      = Bench.edges.settings.graphRead
    val expected = m.claims.map(c => Reasoning.short(c.id)).toSet
    def once(): Either[String, AppraisalContent] =
      val deadline = System.nanoTime() + how.waitLimit.toNanos
      def left     = ((deadline - System.nanoTime()) / 1000000L).toInt
      for
        _ <- m.claims.foldLeft[Either[String, Unit]](Right(()))((done, c) =>
          done.flatMap(_ => if left <= 0 then Left(Reasoning.NotCaughtUp) else r.await(c.id, left))
        )
        content <- r.content(m.reasoningQuestion, pack, m.openPoints)
        _ <- Either.cond(content.claims.map(_.id).toSet == expected, (), Reasoning.NotCaughtUp)
      yield content
    def attempt(n: Int): Either[String, AppraisalContent] =
      once() match
        case Left(Reasoning.NotCaughtUp) if n < how.attempts =>
          Thread.sleep(how.pause.toMillis)
          attempt(n + 1)
        case other => other
    attempt(1)

  /** A run that failed before its model was called: the reasoning service could not be read. */
  private def failedRun(run: RunPlan, why: String, pack: Option[Pack]): StepEffect =
    val recorded = blank(run).copy(
      ending = Ending.Failed(why),
      model = Some(plan.model),
      pack = pack.map(_ => packId),
      appraisal = Some(appraisalId)
    )
    record(recorded)
    next(made(run, recorded.id))

  private def modelRun(
      run: RunPlan,
      q: Question,
      pack: Option[Pack],
      appraisal: Option[(AppraisalMade, AppraisalContent)]
  ): StepEffect =
    val arm = Arms.named(run.arm).get
    val asked =
      QuestionGiven(q.statement, q.criteria, q.closesAt, plan.pretendedTime.getOrElse(now))
    val packRef = pack.map(_ => packId)
    val appraisalRef =
      Option.when(Appraised.contains(run.arm) && pack.exists(!_.empty))(appraisalId)
    // The appraisal's cost is counted in the cost of each run that reads it (FR-027).
    val share = appraisal.fold(BigDecimal(0))(_._1.cost)
    arm.`given`(asked, pack, appraisal.map(_._2)) match
      case Left(Abstain(reason)) =>
        val recorded = blank(run).copy(
          ending = Ending.Abstained(reason),
          model = Some(plan.model),
          pack = packRef,
          appraisal = appraisalRef
        )
        record(recorded)
        next(made(run, recorded.id))
      case Right(userGiven) if appraisal.exists((_, c) => !sameContent(run, userGiven.user, c)) =>
        failedRun(run, ShapesDiffer, pack)
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
              Estimate.of(models, plan.model, input.text) + share,
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
            pack = packRef,
            appraisal = appraisalRef,
            `given` = Some(givenRef),
            answered = answerRef,
            reasons = forecast.reasons,
            usage = Some(forecast.usage),
            cost = forecast.cost + share,
            appraisalShare = share,
            latencyMs = forecast.latencyMs
          )
          val ours = record(recorded)
          cohort.call(CohortEntity.settle).invoke(Settle(runId, forecast.cost + share, now)): Unit
          for
            (m, content) <- appraisal
            p            <- forecast.ending.forecast
            if ours && run.arm == Arms.graph.name
          do believe(runId, run, p, forecast.reasons, m, content)
          next(made(run, runId))

  /**
   * The equality of contracts/arm.md: what this run is to be given holds the appraisal's content,
   * and so does what the other arm of the pair was given for the same repetition, when it has a run
   * (FR-025). Checked before the model is called, so a run that fails it costs nothing.
   */
  private def sameContent(run: RunPlan, text: String, content: AppraisalContent): Boolean =
    val expected = Shape.of(content)
    val other    = if run.arm == Arms.graph.name then Arms.prose.name else Arms.graph.name
    val partner = (1 to MaxAttempts).reverse.iterator
      .flatMap(a =>
        client
          .forEventSourcedEntity(
            EntityId(Ids.run(plan.cohort, plan.question, other, run.repetition, a))
          )
          .call(RunEntity.get)
          .invoke()
          .run
          .flatMap(_.`given`)
      )
      .nextOption()
      .map(ref =>
        GivenBucket
          .read(context.storage, ref)
          .flatMap(k => Given.parse(k.text))
          .flatMap(g => Shape.contentOf(g.user))
      )
    Shape.contentOf(text).contains(expected) && partner.forall(_.contains(expected))

  /**
   * The graph arm's forecast held as a belief in the yes hypothesis, resting on the reasons it
   * named that are claims of this appraisal; others are dropped (research R8). A belief that cannot
   * be written leaves the run as it is: it is tried once more and, failing that, noted on the
   * cohort.
   */
  private def believe(
      runId: String,
      run: RunPlan,
      p: Double,
      reasons: List[String],
      m: AppraisalMade,
      content: AppraisalContent
  ): Unit =
    val claims  = content.claims.map(_.id).toSet
    val restsOn = reasons.distinct.filter(claims).map(r => s"${m.reasoningQuestion}.$r")
    val dropped = reasons.distinct.count(r => !claims(r))
    val r       = reasoning
    def write() = r.belief(
      forecastbench.reasoning.Reasoning.beliefId(m.reasoningQuestion, run.repetition),
      forecastbench.reasoning.Reasoning.graphHolder(plan.cohort, run.repetition),
      forecastbench.reasoning.Reasoning.hypothesis(m.reasoningQuestion),
      p,
      restsOn
    )
    write().orElse(write()).left.foreach { why =>
      cohort
        .call(CohortEntity.note)
        .invoke(Note(s"the belief of run $runId was not written: $why", now)): Unit
    }
    if dropped > 0 then
      cohort
        .call(CohortEntity.note)
        .invoke(
          Note(s"run $runId named $dropped reasons that are not claims of its appraisal", now)
        ): Unit

  private def packId = Ids.pack(plan.cohort, plan.question)

  private def heldPack: Option[Pack] =
    client.forEventSourcedEntity(EntityId(packId)).call(PackEntity.get).invoke().pack

  /**
   * The question's pack (research R12), gathered once: a pack already held is left as it is. The
   * cohort notes it as gathered, empty, or failed with the reason (FR-016, FR-018).
   */
  def gatherStep: StepEffect =
    if heldPack.isEmpty then
      val q     = question
      val edges = Bench.edges
      val asked =
        QuestionGiven(q.statement, q.criteria, q.closesAt, plan.pretendedTime.getOrElse(now))
      val instructions = plan.instructions
        .get("gatherer-queries")
        .flatMap(r => Instructions.at("gatherer-queries", r.version))
        .orElse(Instructions.latest("gatherer-queries"))
        .get
      val gathered = forecastbench.gather.Gatherer.gather(
        forecastbench.gather
          .Gather(
            plan.cohort,
            plan.question,
            asked,
            plan.model,
            plan.pretendedTime,
            now,
            cohort.call(CohortEntity.get).invoke().cohort.flatMap(_.gathererVersion).getOrElse(1)
          ),
        edges.models,
        edges.search,
        instructions,
        edges.settings.limits
      )
      val note = gathered match
        case Right(pack) =>
          try
            client
              .forEventSourcedEntity(EntityId(packId))
              .call(PackEntity.gather)
              .invoke(pack): Unit
          catch case e: CommandError if e.code == ErrorCode.Conflict => ()
          if pack.empty then PackNote.Empty else PackNote.Gathered
        case Left(why) => PackNote.Failed(why)
      cohort.call(CohortEntity.notePack).invoke(NotePack(plan.question, note, now)): Unit
    stepEffects.updateState(currentState).thenTransitionTo(QuestionForecast.appraising.ref)

  private def appraisalId = Ids.appraisal(plan.cohort, plan.question)

  private def heldAppraisal: AppraisalState =
    client.forEventSourcedEntity(EntityId(appraisalId)).call(AppraisalEntity.get).invoke()

  /**
   * The question's appraisal (research R7), made once, for a cohort that names the graph or the
   * prose arm and a pack with documents. Made or failed, it is held; an arm reading it waits for
   * it.
   */
  def appraiseStep: StepEffect =
    val held   = cohort.call(CohortEntity.get).invoke().cohort.get
    val wanted = held.arms.exists(Appraised.contains)
    val pack   = heldPack.filterNot(_.empty)
    if wanted && pack.isDefined && !heldAppraisal.held then
      val q = question
      val asked =
        QuestionGiven(q.statement, q.criteria, q.closesAt, plan.pretendedTime.getOrElse(now))
      def instruction(name: String) = plan.instructions
        .get(name)
        .flatMap(r => Instructions.at(name, r.version))
        .orElse(Instructions.latest(name))
        .get
      val result = forecastbench.reasoning.Appraiser.appraise(
        forecastbench.reasoning.Appraise(
          plan.cohort,
          plan.question,
          asked,
          pack.get,
          plan.model,
          held.repetitions,
          now
        ),
        Bench.edges.models,
        forecastbench.reasoning.Reasoning(context.services("reasoning")),
        instruction("appraisal-reader"),
        instruction("appraisal-open-points"),
        reply => GivenBucket.keep(context.storage, reply)
      )
      val entity = client.forEventSourcedEntity(EntityId(appraisalId))
      try
        result.fold(
          f => entity.call(AppraisalEntity.fail).invoke(f),
          m => entity.call(AppraisalEntity.make).invoke(m)
        ): Unit
      catch case e: CommandError if e.code == ErrorCode.Conflict => ()
    stepEffects.updateState(currentState).thenTransitionTo(QuestionForecast.finish.ref)

  /**
   * The failover of every step. Round 0: a pack not yet noted is noted as failed, an appraisal not
   * yet held is held as failed. A round of runs: a venue not yet read is unreadable; the run in
   * hand is recorded as failed, its reservation settled at what was reserved, when its model may
   * have been called, and is noted as not made when nothing was reserved. Then on to the next run.
   */
  def abandonStep: StepEffect =
    val held = cohort.call(CohortEntity.get).invoke().cohort.get
    if plan.gather then
      if !held.packs.contains(plan.question) then
        cohort
          .call(CohortEntity.notePack)
          .invoke(NotePack(plan.question, PackNote.Failed(StepFailed), now)): Unit
      else if held.arms.exists(Appraised.contains) && heldPack.exists(
          !_.empty
        ) && !heldAppraisal.held
      then
        try
          client
            .forEventSourcedEntity(EntityId(appraisalId))
            .call(AppraisalEntity.fail)
            .invoke(AppraisalFailed(StepFailed, None, Usage.zero, BigDecimal(0), now)): Unit
        catch case e: CommandError if e.code == ErrorCode.Conflict => ()
      stepEffects.updateState(currentState).thenTransitionTo(QuestionForecast.finish.ref)
    else if currentState.venue.isEmpty then
      next(currentState.copy(venue = Some(VenueSaid(None, unreadable = true))))
    else
      currentState.pending.headOption match
        case None =>
          stepEffects.updateState(currentState).thenTransitionTo(QuestionForecast.finish.ref)
        case Some(run) =>
          val runId = Ids.run(plan.cohort, plan.question, run.arm, run.repetition, run.attempt)
          val recorded = client
            .forEventSourcedEntity(EntityId(runId))
            .call(RunEntity.get)
            .invoke()
            .run
            .isDefined
          held.reserved.get(runId) match
            // Recorded before the step failed: the run stands as recorded; settle what is reserved.
            case Some(amount) if recorded =>
              cohort.call(CohortEntity.settle).invoke(Settle(runId, amount, now)): Unit
              next(made(run, runId))
            case None if recorded => next(made(run, runId))
            case Some(amount) =>
              record(
                blank(run)
                  .copy(ending = Ending.Failed(StepFailed), model = Some(plan.model), cost = amount)
              )
              cohort.call(CohortEntity.settle).invoke(Settle(runId, amount, now)): Unit
              next(made(run, runId))
            case None => notMade(run, StepFailed)

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
    val to = state.pending.headOption.fold(QuestionForecast.finish.ref)(r => stepFor(r.arm))
    stepEffects.updateState(state.copy(waited = 0)).thenTransitionTo(to)

  /** Each arm's runs are made by a step named for it. */
  private def stepFor(arm: String): com.thinkmorestupidless.ankka.core.effect.StepRef = arm match
    case "venue-price" => QuestionForecast.venuePrice.ref
    case "evidence"    => QuestionForecast.evidence.ref
    case "graph"       => QuestionForecast.graph.ref
    case "prose"       => QuestionForecast.prose.ref
    case _             => QuestionForecast.model.ref

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

  /**
   * The run, recorded once; a run refused as already recorded or too late is left as it is. True
   * when this call recorded it: a step that ran on past its timeout finds its run already recorded
   * as failed by `abandon`, and writes nothing of its own after it.
   */
  private def record(run: RunRecorded): Boolean =
    val q = question
    try
      client
        .forEventSourcedEntity(EntityId(run.id))
        .call(RunEntity.record)
        .invoke(Record(run, QuestionNow(q.standing, q.closesAt, q.current.isDefined))): Unit
      true
    catch case e: CommandError if e.code == ErrorCode.Conflict => false

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

  val gathering  = step("gather")(_.gatherStep)
  val appraising = step("appraise")(_.appraiseStep)

  /** The arms that read the appraisal. */
  val Appraised: Set[String] = Set("graph", "prose")
  val refresh                = step("refresh")(_.refreshStep)
  val venuePrice             = step("venue-price")(_.venuePriceStep)
  val model                  = step("model-alone")(_.modelStep)
  val evidence               = step("evidence")(_.modelStep)
  val graph                  = step("graph")(_.modelStep)
  val prose                  = step("prose")(_.modelStep)

  /** How long an arm that takes evidence waits before it looks for its pack again. */
  val PackWait: FiniteDuration = 2.seconds

  /** How many times a run waits for its pack or its appraisal: ten minutes at `PackWait`. */
  val MaxWaits = 300

  val NoPackInTime      = "the pack was not gathered in time"
  val NoAppraisalInTime = "the appraisal was not made in time"
  val ShapesDiffer      = "the two shapes did not hold the same content"

  /** The attempts of a run looked through for the other arm of a pair. */
  val MaxAttempts = 5

  val finish = step("finish")(_.finishStep)

  /** Where every step fails over to. */
  val abandoning = step("abandon")(_.abandonStep)

  /** Why a run, a pack or an appraisal was left by a step that failed or outlived its time. */
  val StepFailed = "the step failed or did not finish in time"

  /**
   * A model step: the graph read, at most 30 + 10 + 30 + 10 + 30 seconds, and the model call, at
   * most four minutes, with a minute for the calls around them.
   */
  val StepTimeout: FiniteDuration = 7.minutes

  /** The appraisal: every document read at once, then the open points, three minutes each. */
  val AppraiseTimeout: FiniteDuration = 8.minutes

  /**
   * The gathering: the queries, at most two minutes, then three searches one after another, each at
   * most five attempts of 30 seconds and the pauses between them (`Exa.MaxPauseMs` at most each),
   * about eleven and a half minutes; and the wait for one of Exa's bench-wide slots, which six
   * questions gathering at once can make as long again as a search.
   */
  val GatherTimeout: FiniteDuration = 20.minutes

  val start = command("start")(_.start)
  val get   = query("get")(_.get)
