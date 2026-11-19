package forecastbench.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import forecastbench.domain.OutcomeReading

import java.time.Instant

/** The questions whose outcomes the bench reads: every question of a frozen cohort. */
final case class Watched(questions: List[String])

/**
 * One entity, `outcomes`, naming them. A timed action has no view client, so the reader cannot ask
 * the cohorts view which questions are in frozen cohorts; the freeze route tells this entity.
 */
final class WatchedEntity extends KeyValueEntity[Watched]:
  def emptyState: Watched = Watched(Nil)

  def watch(questions: List[String]): Effect[Watched] =
    val more = questions.filterNot(currentState.questions.contains).distinct
    if more.isEmpty then effects.reply(currentState)
    else effects.updateState(Watched(currentState.questions ++ more)).thenReplyState

  def get: ReadOnlyEffect[Watched] = effects.reply(currentState)

object WatchedEntity
    extends KeyValueEntity.Companion[WatchedEntity, Watched](
      componentId = ComponentId("watched"),
      stateSerializer = Codecs.serializer[Watched]("watched")
    ):
  given Serializer[Watched]      = stateSerializer
  given Serializer[List[String]] = Codecs.serializer[List[String]]("watched-questions")

  val Id = "outcomes"

  def create(context: KeyValueEntityContext) = new WatchedEntity

  val watch = command("watch")(_.watch)
  val get   = query("get")(_.get)

/**
 * What one reading of the venues did: how many questions it read, changed, and the venues it could
 * not read.
 */
final case class OutcomesRead(read: Int, changed: Int, unreadable: List[String])

/**
 * The outcome reader: each watched question asked of its venue, and what the venue says sent to the
 * question, which records it only when it differs (a revision names the one it revises). A venue
 * that cannot be read is named and changes nothing (FR-031, FR-032).
 */
object OutcomeReader:

  def readAll(client: ComponentClient, now: Instant): OutcomesRead =
    val watched = client
      .forKeyValueEntity(EntityId(WatchedEntity.Id))
      .call(WatchedEntity.get)
      .invoke()
      .questions
    val venues = Bench.edges.venues
    val results = watched.flatMap { id =>
      client.forEventSourcedEntity(EntityId(id)).call(QuestionEntity.get).invoke().question.map { q =>
        venues.get(q.venue).map(_.one(q.venueId)) match
          case Some(Right(Some(vq))) =>
            val changed = vq.outcome.exists { value =>
              client
                .forEventSourcedEntity(EntityId(id))
                .call(QuestionEntity.readOutcome)
                .invoke(ReadOutcome(OutcomeReading(value, vq.disputed, vq.resolvedAt), now))
            }
            (if changed then 1 else 0, None)
          case Some(Right(None)) => (0, None)
          case _                 => (0, Some(q.venue))
      }
    }
    OutcomesRead(results.size, results.map(_._1).sum, results.flatMap(_._2).distinct.sorted)

/** Reads outcomes on the hour (`bench.outcomes-every`), scheduled when the service starts. */
final class ReadOutcomes(context: TimedActionContext) extends TimedAction:
  def read(@scala.annotation.unused what: String): Effect =
    OutcomeReader.readAll(context.componentClient, Bench.edges.clock.now): Unit
    effects.done()

object ReadOutcomes extends TimedAction.Companion[ReadOutcomes](ComponentId("read-outcomes")):
  def create(context: TimedActionContext) = new ReadOutcomes(context)

  val TimerName = "read-outcomes"

  val read = handler("read")(_.read)
