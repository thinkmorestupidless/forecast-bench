package forecastbench.steps

import forecastbench.domain.OutcomeValue
import forecastbench.domain.Ids
import forecastbench.support.ScriptedVenue
import forecastbench.venues.VenueQuestion

import java.time.Duration

class ImportFeatures extends FeatureSuite("features/questions/import.feature") with OutcomeSteps:

  private def venueName(v: String) = v.toLowerCase

  /** A question this scenario lists at a venue, and only this scenario's questions are read. */
  private def listing(venue: String, name: String)(change: VenueQuestion => VenueQuestion): String =
    val v  = fixture.venue(venue)
    val id = world.venueId(name)
    v.only = Some(s"s${world.n}-")
    v.list(change(ScriptedVenue.question(id, name, fixture.clock.now.plus(Duration.ofDays(28)))))
    world.held("question") = Ids.question(venueName(venue), id)
    id

  private def importFrom(venue: String): (Int, String) =
    fixture.venue(venue).only = Some(s"s${world.n}-")
    post(s"/venues/${venueName(venue)}/import", """{"standing":"prospective"}""")

  private def question: ujson.Value =
    json(ok(get(s"/questions/${world.held("question")}")))

  private def heldFrom(venue: String): Int =
    val rows = json(ok(get(s"/questions?venue=${venueName(venue)}"))).arr
    rows.count(_("id").str.startsWith(s"${venueName(venue)}.s${world.n}-"))

  Given("the venue {string} lists an open question {string}") { (venue: String, statement: String) =>
    listing(venue, statement)(identity): Unit
  }

  When("the experimenter imports questions from {string}") { (venue: String) =>
    world.last = importFrom(venue)
  }

  When("the experimenter imports questions from {string} again") { (venue: String) =>
    world.last = importFrom(venue)
  }

  Then(
    "the question is held with its statement, its resolution criteria, the time it closes and its venue"
  ) { () =>
    ok(world.last): Unit
    val q      = question
    val listed = fixture.venue(q("venue").str).held(q("venueId").str).get
    assertEquals(q("statement").str, listed.statement)
    assertEquals(q("criteria").str, listed.criteria.get)
    assertEquals(q("closesAt").str, listed.closesAt.toString)
    assertEquals(q("venue").str, "polymarket")
  }

  Then("the question is prospective") { () =>
    assertEquals(question("standing").str, "prospective")
  }

  Given("the venue {string} lists an open question with {string} outcomes") {
    (venue: String, n: String) =>
      listing(venue, s"a question of $n outcomes")(
        _.copy(outcomes = (1 to n.toInt).map(i => s"outcome $i").toList)
      ): Unit
  }

  Given("the venue {string} lists an open question with no resolution criteria") { (venue: String) =>
    listing(venue, "a question with no criteria")(_.copy(criteria = None)): Unit
  }

  Then("the question is not held") { () =>
    assertEquals(get(s"/questions/${world.held("question")}")._1, 404)
  }

  private def leftOutFor(reason: String): Unit =
    val body    = json(ok(world.last))
    val venueId = world.held("question").dropWhile(_ != '.').drop(1)
    val out     = body("leftOut").arr.find(_("venueId").str == venueId)
    assertEquals(out.map(_("reason").str), Some(reason), body.toString)

  Then("the import says it was left out for having more than two outcomes") { () =>
    leftOutFor("more than two outcomes")
  }

  Then("the import says it was left out for having no resolution criteria") { () =>
    leftOutFor("no resolution criteria")
  }

  Given("a prospective question imported from {string}") { (venue: String) =>
    listing(venue, "a question imported twice")(identity): Unit
    ok(importFrom(venue)): Unit
  }

  Then("the question is held once") { () =>
    assertEquals(json(ok(world.last))("already").num.toInt, 1)
    eventually("the question in the view")(Option.when(heldFrom("polymarket") >= 1)(()))
    assertEquals(heldFrom("polymarket"), 1)
  }

  Given("the experimenter has named the category {string} of {string} as the domain {string}") {
    (category: String, venue: String, domain: String) =>
      val named = json(ok(get(s"/venues/${venueName(venue)}/domains"))).obj
      named(category) = ujson.Str(domain)
      ok(put(s"/venues/${venueName(venue)}/domains", ujson.write(named))): Unit
  }

  Given("the experimenter has named no domain for the category {string} of {string}") {
    (category: String, venue: String) =>
      val named = json(ok(get(s"/venues/${venueName(venue)}/domains"))).obj
      named.remove(category): Unit
      ok(put(s"/venues/${venueName(venue)}/domains", ujson.write(named))): Unit
  }

  When("a question in the category {string} is imported from {string}") {
    (category: String, venue: String) =>
      listing(venue, s"a question about $category")(_.copy(category = Some(category))): Unit
      ok(importFrom(venue)): Unit
  }

  Then("the question's domain is {string}") { (domain: String) =>
    assertEquals(question("domain").str, domain)
  }

  Given("{string} questions held from {string}") { (n: String, venue: String) =>
    (1 to n.toInt).foreach(i => listing(venue, s"held question $i")(identity): Unit)
    ok(importFrom(venue)): Unit
    eventually("the questions in the view")(Option.when(heldFrom(venue) == n.toInt)(()))
  }

  When("the experimenter imports questions from {string} and the venue cannot be read") {
    (venue: String) =>
      fixture.venue(venue).cannotBeRead(true)
      try world.last = importFrom(venue)
      finally fixture.venue(venue).cannotBeRead(false)
  }

  Then("the import says the venue could not be read") { () =>
    assertEquals(world.last._1, 503, world.last._2)
    assert(world.last._2.contains("the venue could not be read"), world.last._2)
  }

  Then("{string} questions are held from {string}") { (n: String, venue: String) =>
    assertEquals(heldFrom(venue), n.toInt)
  }

  Given("a prospective question in a frozen cohort") { () =>
    val id = draft("november")
    settle(id, List(importedQuestion("rates")), List("model-alone"))
    freeze(id): Unit
  }

  When("its venue resolves it") { () =>
    resolve("rates", "yes")
    readOutcomes(): Unit
    // And an import that lists it again, as a later import of resolved questions would.
    fixture.kalshi.only = Some(world.venueId("rates"))
    post("/venues/kalshi/import", """{"standing":"development","onePerEvent":false}"""): Unit
    fixture.kalshi.only = None
  }

  Then("the question is still prospective") { () =>
    val q = json(ok(get(s"/questions/${questionId("rates")}")))
    assertEquals(q("standing").str, "prospective")
    assertEquals(q("outcomes").arr.head("value").str, "yes")
  }

  Given("the venue {string} lists a resolved question {string}") {
    (venue: String, statement: String) =>
      val id = listing(venue, statement)(identity)
      fixture
        .venue(venue)
        .resolve(id, OutcomeValue.No, fixture.clock.now.minus(Duration.ofDays(30)))
  }

  When("the experimenter imports resolved questions from {string}") { (venue: String) =>
    fixture.venue(venue).only = Some(s"s${world.n}-")
    world.last = post(s"/venues/${venueName(venue)}/import", """{"standing":"development"}""")
  }

  Then("the question is a development question") { () =>
    ok(world.last): Unit
    assertEquals(question("standing").str, "development")
  }

  Then("its outcome is held with it") { () =>
    val outcomes = question("outcomes").arr
    assertEquals(outcomes.map(_("value").str).toList, List("no"))
  }

  Given("a development question") { () =>
    val id = listing("Kalshi", "rehearsed")(identity)
    fixture.kalshi.resolve(id, OutcomeValue.Yes, fixture.clock.now.minus(Duration.ofDays(30)))
    fixture.kalshi.only = Some(s"s${world.n}-")
    ok(post("/venues/kalshi/import", """{"standing":"development"}""")): Unit
    assertEquals(question("standing").str, "development")
  }

  When("the experimenter imports questions from its venue again") { () =>
    world.last = importFrom("Kalshi")
  }

  Then("the question is still a development question") { () =>
    assertEquals(question("standing").str, "development")
    assertEquals(question("outcomes").arr.map(_("value").str).toList, List("yes"))
  }
