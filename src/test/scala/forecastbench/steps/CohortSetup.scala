package forecastbench.steps

import com.thinkmorestupidless.ankka.core.EntityId
import forecastbench.application.{CohortForecast, RunEntity}
import forecastbench.domain.{Ids, OutcomeValue, RunRecorded}
import forecastbench.support.ScriptedVenue

import java.time.{Duration, Instant}
import scala.concurrent.duration.DurationInt

/** How the suites make the questions and cohorts their scenarios start from, through the routes. */
trait CohortSetup extends FeatureSuite:

  /** A question listed at Kalshi and imported, closing `closesIn` from now. Its id. */
  protected def importedQuestion(
      name: String,
      closesIn: Duration = Duration.ofDays(28),
      resolved: Option[OutcomeValue] = None,
      eventGroup: Option[String] = None
  ): String =
    val venueId = world.venueId(name)
    val closes  = fixture.clock.now.plus(closesIn)
    val listed =
      ScriptedVenue.question(venueId, statement(name), closes, eventGroup = eventGroup)
    fixture.kalshi.list(listed)
    resolved.foreach(o => fixture.kalshi.resolve(venueId, o, closes.minus(Duration.ofDays(1))))
    fixture.kalshi.only = Some(venueId)
    val standing = if resolved.isDefined then "development" else "prospective"
    ok(post("/venues/kalshi/import", s"""{"standing":"$standing","onePerEvent":false}"""))
    fixture.kalshi.only = None
    val id = Ids.question("kalshi", venueId)
    world.held(s"question:$name") = id
    id

  /**
   * A question's statement: its name and the scenario's number, which the scripted model keys on.
   */
  protected def statement(name: String): String = s"Will $name happen [#${world.n}]?"

  protected def questionId(name: String): String =
    world.held.getOrElse(s"question:$name", fail(s"the scenario has no question '$name'"))

  protected def draft(
      name: String,
      model: String = "model-a",
      standing: String = "prospective",
      pretended: Option[Instant] = None
  ): String =
    val id = world.cohort(name)
    val p  = pretended.fold("")(t => s""","pretendedTime":"$t"""")
    ok(post("/cohorts", s"""{"id":"$id","standing":"$standing","model":"$model"$p}"""))
    id

  protected def window(from: Instant, to: Instant): String =
    s"""{"window":{"from":"$from","to":"$to"}}"""

  /** Questions, arms, a window of two days from now, a budget and repetitions. */
  protected def settle(
      cohort: String,
      questions: List[String],
      arms: List[String],
      repetitions: Int = 1,
      budget: String = "100.00",
      windowDays: Int = 2
  ): Unit =
    if questions.nonEmpty then
      ok(
        post(s"/cohorts/$cohort/questions", ujson.write(ujson.Obj("questions" -> questions)))
      ): Unit
    if arms.nonEmpty then
      ok(post(s"/cohorts/$cohort/arms", ujson.write(ujson.Obj("arms" -> arms)))): Unit
    val now = fixture.clock.now
    ok(put(s"/cohorts/$cohort", window(now, now.plus(Duration.ofDays(windowDays.toLong))))): Unit
    ok(put(s"/cohorts/$cohort", s"""{"repetitions":$repetitions,"budget":$budget}""")): Unit

  protected def freeze(cohort: String): String = ok(post(s"/cohorts/$cohort/freeze", "{}"))

  protected def readCohort(cohort: String): ujson.Value = json(ok(get(s"/cohorts/$cohort")))

  /** Every run of the cohort recorded so far, read from the runs themselves, any attempt. */
  protected def recorded(cohort: String): List[RunRecorded] =
    val c = readCohort(cohort)
    for
      q       <- c("questions").arr.map(_.str).toList
      a       <- c("arms").arr.map(_.str).toList
      r       <- (1 to c("repetitions").num.toInt).toList
      attempt <- (1 to 3).toList
      run <- fixture.kit.componentClient
        .forEventSourcedEntity(EntityId(Ids.run(cohort, q, a, r, attempt)))
        .call(RunEntity.get)
        .invoke()
        .run
    yield run

  /** Asks for the cohort's forecast and waits for every question of its round to end. */
  protected def forecastAndWait(cohort: String, round: Int = 1): (Int, String) =
    val answer = post(s"/cohorts/$cohort/forecast", "{}")
    if answer._1 == 202 then awaitRound(cohort, round)
    answer

  protected def awaitRound(cohort: String, round: Int): Unit =
    eventually(s"the round $round of $cohort to end", 60.seconds) {
      val state = fixture.kit.componentClient
        .forWorkflow(EntityId(CohortForecast.id(cohort, round)))
        .call(CohortForecast.get)
        .invoke()
      Option.when(state.ended)(())
    }

  /** The arm as the features name it ("model alone") and as the bench does ("model-alone"). */
  protected def arm(words: String): String = words.trim.toLowerCase.replace(' ', '-')
