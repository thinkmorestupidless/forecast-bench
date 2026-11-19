package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*

import scala.concurrent.duration.*

/** One round of a cohort's forecast: the question workflows still to start, and those running. */
final case class CohortForecastState(
    cohort: String,
    round: Int,
    queued: List[QuestionPlan],
    running: List[String],
    done: List[String],
    dispatching: Boolean,
    ended: Boolean
)

final case class Round(cohort: String, round: Int, plans: List[QuestionPlan])

/**
 * The forecast of a cohort (research R5): it starts question workflows, at most `bench.concurrency`
 * at a time, and pauses; each one tells it when it ends.
 *
 * A command made while a step runs would have its state overwritten by the step's, so
 * `questionEnded` changes nothing and only wakes the workflow when it is paused; the `dispatch`
 * step asks each running question workflow whether it has ended. A wake lost between that question
 * and the pause is caught by the pause's own deadline.
 */
final class CohortForecast(context: WorkflowContext) extends Workflow[CohortForecastState]:
  import CohortForecast.*

  private val client = context.componentClient

  def emptyState: CohortForecastState =
    CohortForecastState(context.workflowId.toString, 0, Nil, Nil, Nil, false, false)

  override def settings: WorkflowSettings =
    WorkflowSettings.builder
      .defaultStepTimeout(1.minute)
      .defaultRecovery(RecoverStrategy.maxRetries(3))
      .build

  def start(r: Round): Effect[Done] =
    if currentState.round != 0 then effects.reply(Done)
    else
      effects
        .updateState(
          CohortForecastState(
            r.cohort,
            r.round,
            r.plans,
            Nil,
            Nil,
            dispatching = true,
            ended = false
          )
        )
        .transitionTo(CohortForecast.dispatch)
        .thenReply(Done)

  def questionEnded(@scala.annotation.unused question: String): Effect[Done] =
    if currentState.dispatching || currentState.ended then effects.reply(Done)
    else
      effects
        .updateState(currentState.copy(dispatching = true))
        .transitionTo(CohortForecast.dispatch)
        .thenReply(Done)

  def get: ReadOnlyEffect[CohortForecastState] = effects.reply(currentState)

  def dispatchStep: StepEffect =
    val s = currentState
    val (finished, running) = s.running.partition { id =>
      client.forWorkflow(EntityId(id)).call(QuestionForecast.get).invoke().ended
    }
    val room          = Bench.edges.settings.concurrency - running.size
    val (now, queued) = s.queued.splitAt(math.max(room, 0))
    val started = now.map { plan =>
      val id = QuestionForecast.id(plan.cohort, plan.question, s.round)
      client
        .forWorkflow(EntityId(id))
        .call(QuestionForecast.start)
        .invoke(plan.copy(parent = Some(context.workflowId.toString))): Unit
      id
    }
    val next = s.copy(queued = queued, running = running ++ started, done = s.done ++ finished)
    if next.running.isEmpty && next.queued.isEmpty then
      stepEffects.updateState(next.copy(dispatching = false, ended = true)).thenEnd
    else
      stepEffects
        .updateState(next.copy(dispatching = false))
        .thenPause(Patience, CohortForecast.dispatch.ref)

object CohortForecast
    extends Workflow.Companion[CohortForecast, CohortForecastState](
      componentId = ComponentId("cohort-forecast"),
      stateSerializer = Codecs.serializer[CohortForecastState]("cohort-forecast")
    ):
  given Serializer[Round] = Codecs.serializer[Round]("cohort-forecast-round")

  /** How long it waits for a word from a question before it looks for itself. */
  val Patience: FiniteDuration = 20.seconds

  def id(cohort: String, round: Int): String = s"$cohort.$round"

  def create(context: WorkflowContext) = new CohortForecast(context)

  val dispatch = step("dispatch")(_.dispatchStep)

  val start         = command("start")(_.start)
  val questionEnded = command("question-ended")(_.questionEnded)
  val get           = query("get")(_.get)
