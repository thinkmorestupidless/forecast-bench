package forecastbench.steps

import forecastbench.domain.{Ids, OutcomeValue}
import forecastbench.support.ScriptedVenue

import java.time.{Duration, Instant}

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
      ScriptedVenue.question(venueId, s"Will $name happen?", closes, eventGroup = eventGroup)
    fixture.kalshi.list(listed)
    resolved.foreach(o => fixture.kalshi.resolve(venueId, o, closes.minus(Duration.ofDays(1))))
    fixture.kalshi.only = Some(venueId)
    val standing = if resolved.isDefined then "development" else "prospective"
    ok(post("/venues/kalshi/import", s"""{"standing":"$standing","onePerEvent":false}"""))
    fixture.kalshi.only = None
    val id = Ids.question("kalshi", venueId)
    world.held(s"question:$name") = id
    id

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

  /** The arm as the features name it ("model alone") and as the bench does ("model-alone"). */
  protected def arm(words: String): String = words.trim.toLowerCase.replace(' ', '-')
