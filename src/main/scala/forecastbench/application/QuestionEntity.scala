package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.*

final case class QuestionState(question: Option[Question])

/** What an import did: held for the first time, or already held. */
final case class Imported(held: Boolean, question: Question)

/** An outcome as the reader read it, and when. */
final case class ReadOutcome(reading: OutcomeReading, now: java.time.Instant)

/**
 * A question, its standing for ever, and every outcome its venue has given it (data-model.md). No
 * command states an outcome: `readOutcome` is called by the outcome reader alone, and no route
 * reaches it (FR-032).
 */
final class QuestionEntity extends EventSourcedEntity[QuestionState, QuestionEvent]:

  def emptyState: QuestionState = QuestionState(None)

  def applyEvent(event: QuestionEvent): QuestionState =
    QuestionState(Question(currentState.question, event))

  def importQuestion(q: QuestionImport): Effect[Imported] =
    Question.importing(currentState.question, q) match
      case Left(why) => effects.error(why, ErrorCode.BadRequest)
      case Right(events) =>
        val first = currentState.question.isEmpty
        effects.persistAll(events).thenReply(s => Imported(first, s.question.get))

  def readOutcome(r: ReadOutcome): Effect[Boolean] =
    currentState.question match
      case None => effects.error("no such question", ErrorCode.NotFound)
      case Some(_) =>
        Question.reading(currentState.question, r.reading, r.now) match
          case Nil    => effects.reply(false)
          case events => effects.persistAll(events).thenReply(_ => true)

  def get: ReadOnlyEffect[QuestionState] = effects.reply(currentState)

object QuestionEntity
    extends EventSourcedEntity.Companion[QuestionEntity, QuestionState, QuestionEvent](
      componentId = ComponentId("question"),
      stateSerializer = Codecs.serializer[QuestionState]("question"),
      eventSerializer = Codecs.serializer[QuestionEvent]("question-event")
    ):
  given Serializer[QuestionImport] = Codecs.serializer[QuestionImport]("question-import")
  given Serializer[Imported]       = Codecs.serializer[Imported]("question-imported")
  given Serializer[ReadOutcome]    = Codecs.serializer[ReadOutcome]("question-read-outcome")
  given Serializer[QuestionState]  = stateSerializer

  def create(context: EventSourcedEntityContext) = new QuestionEntity

  val importQuestion = command("import")(_.importQuestion)
  val readOutcome    = command("read-outcome")(_.readOutcome)
  val get            = query("get")(_.get)
