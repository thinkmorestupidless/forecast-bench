package forecastbench.steps

import com.thinkmorestupidless.ankka.agent.ModelResponse
import com.thinkmorestupidless.ankka.core.EntityId
import forecastbench.application.CohortForecast
import forecastbench.domain.*
import forecastbench.support.{ScriptedModel, ScriptedVenue}

import java.time.Duration
import scala.concurrent.duration.*

class ReportFeatures
    extends FeatureSuite("features/reports/report.feature")
    with ModelSteps
    with OutcomeSteps:

  override val munitTimeout = 8.minutes

  override protected def pending = Set(
    // T072: development cohorts.
    "a report of a development cohort says on its face that it shows nothing",
    "a report over several cohorts leaves out development cohorts"
  )

  /** What an arm answers for a question of the scenario. */
  private enum Answer:
    case P(p: Double)
    case Abstains
    case Fails

  private val Arms5 = List("venue-price", "model-alone", "evidence", "graph", "prose")

  /**
   * The background's cohort, drafted with its arms and frozen by the first step that seeds its
   * questions: a frozen cohort takes no question, and each scenario brings its own.
   */
  Given(
    "a frozen prospective cohort {string} with the arms {string}, {string}, {string}, {string} and {string}"
  ) { (name: String, a: String, b: String, c: String, d: String, e: String) =>
    val id = draft(name)
    settle(id, Nil, List(a, b, c, d, e).map(arm), budget = "10000.00")
  }

  private def armOf(r: com.thinkmorestupidless.ankka.agent.ModelRequest): String =
    List("model-alone", "evidence", "graph", "prose")
      .find(a => r.systemMessage == forecastbench.application.Instructions.latest(a).map(_.text))
      .getOrElse("model-alone")

  private val Named = """Question: Will (.+) happen \[#\d+\]\?""".r.unanchored

  /**
   * Each model arm's answer for each question, by the question's name; a forecast of a half else.
   */
  private def answering(answer: (String, String) => Answer): Unit =
    fixture.model.when(key) { r =>
      val name = ScriptedModel.text(r) match
        case Named(n) => n
        case _        => ""
      answer(name, armOf(r)) match
        case Answer.P(p) =>
          Right(ModelResponse(ScriptedModel.forecast(p), usage = ScriptedModel.Usage))
        case Answer.Fails =>
          Right(ModelResponse("I would rather not say.", usage = ScriptedModel.Usage))
        case Answer.Abstains =>
          Right(
            ModelResponse(
              """{"probability": null, "abstain": "nothing can be known", "reasons": [], "rationale": "x"}""",
              usage = ScriptedModel.Usage
            )
          )
    }

  /** The scenario's questions, imported in one call and added to the cohort. */
  private def seed(
      names: List[String],
      closes: String => java.time.Instant = _ => fixture.clock.now.plus(Duration.ofDays(28)),
      category: String => String = _ => "Economics",
      cohort: String = november
  ): List[String] =
    names.foreach { n =>
      fixture.kalshi.list(
        ScriptedVenue.question(
          world.venueId(n),
          statement(n),
          closes(n),
          category = Some(category(n))
        )
      )
    }
    fixture.kalshi.only = Some(s"s${world.n}-")
    try
      ok(post("/venues/kalshi/import", """{"standing":"prospective","onePerEvent":false}""")): Unit
    finally fixture.kalshi.only = None
    val ids = names.map { n =>
      val id = Ids.question("kalshi", world.venueId(n))
      world.held(s"question:$n") = id
      id
    }
    ok(post(s"/cohorts/$cohort/questions", ujson.write(ujson.Obj("questions" -> ids)))): Unit
    ids

  /**
   * Freezes the cohort and forecasts it, waiting for every run to be in the view the report reads.
   */
  private def forecastAll(cohort: String = november): Unit =
    if readCohort(cohort)("status").str == "drafted" then freeze(cohort): Unit
    ok(post(s"/cohorts/$cohort/forecast", "{}")): Unit
    eventually(s"the cohort's forecast to end", 7.minutes) {
      val state = fixture.kit.componentClient
        .forWorkflow(EntityId(CohortForecast.id(cohort, 1)))
        .call(CohortForecast.get)
        .invoke()
      Option.when(state.ended)(())
    }
    val c        = readCohort(cohort)
    val expected = c("questions").arr.size * c("arms").arr.size * c("repetitions").num.toInt
    eventually("every run to be listed", 3.minutes)(
      Option.when(listed(cohort).size == expected)(())
    )

  /** The cohort's runs as the view the report reads holds them. */
  private def listed(cohort: String = november): List[ujson.Value] =
    json(ok(get(s"/runs?cohort=$cohort"))).arr.toList

  /**
   * The outcomes at the venue, read by the bench; then the report, once every run with a forecast
   * on a question with an outcome of yes or no, undisputed, has its score.
   */
  private def outcomes(
      of: Map[String, String],
      disputed: Set[String] = Set.empty,
      cohorts: List[String] = List(november)
  ): ujson.Value =
    of.foreach((n, v) => resolve(n, v, disputed(n)))
    readOutcomes(): Unit
    def forecast(r: ujson.Value) = r("run")("ending")("type").str == "Forecast"
    def scorable(r: ujson.Value) =
      of.exists((n, v) =>
        questionId(n) == r("run")("question").str && (v == "yes" || v == "no") && !disputed(n)
      )
    val scored = cohorts.flatMap(listed).count(r => forecast(r) && scorable(r))
    eventually("every run to be scored", 3.minutes) {
      Option.when(cohorts.flatMap(listed).count(_("scores").arr.nonEmpty) == scored)(())
    }
    report()

  private def report(): ujson.Value = json(ok(get(s"/cohorts/$november/report")))

  private def names(n: Int): List[String] = (1 to n).toList.map(i => s"q$i")

  /**
   * `n` questions, every arm forecasting each with the answer given, all scored by the outcome
   * given.
   */
  private def scoredQuestions(
      n: Int,
      answer: (String, String) => Answer,
      outcome: String => String
  ): ujson.Value =
    val qs = names(n)
    answering(answer)
    seed(qs)
    forecastAll()
    outcomes(qs.map(q => q -> outcome(q)).toMap)

  Given("{string} questions of the cohort have an outcome of yes or no") { (n: String) =>
    world.held("n") = n
    scoredQuestions(
      n.toInt,
      (q, _) => Answer.P(0.3 + (q.drop(1).toInt % 5) * 0.1),
      q => if q.drop(1).toInt % 3 == 0 then "no" else "yes"
    ): Unit
  }

  When("the experimenter asks for the report of the cohort") { () =>
    world.last = get(s"/cohorts/$november/report")
  }

  Then(
    "each arm is given its mean Brier score, its mean log score and the number of questions scored"
  ) { () =>
    val arms = json(ok(world.last))("arms").arr
    assertEquals(arms.map(_("arm").str).toList.sorted, Arms5.sorted)
    arms.foreach { a =>
      assertEquals(a("questions").num.toInt, world.held("n").toInt, a.toString)
      assert(!a("brier").isNull && !a("log").isNull, a.toString)
      assert(a("brier")("mean").num > 0 && a("log")("mean").num > 0, a.toString)
    }
  }

  private def figures: ujson.Value = json(ok(world.last))

  private def arm(r: ujson.Value, name: String): ujson.Value =
    r("arms").arr.find(_("arm").str == name).getOrElse(fail(s"no arm $name in ${r("arms")}"))

  private val ModelArms = List("model-alone", "evidence", "graph", "prose")

  private def yesOrNo(q: String): String = if q.drop(1).toInt % 3 == 0 then "no" else "yes"

  Given("each model arm has runs with a forecast") { () =>
    answering((_, _) => Answer.P(0.6))
    seed(names(6))
    forecastAll()
  }

  Then(
    "each model arm is given its mean cost for each forecast, its mean tokens and its mean latency"
  ) { () =>
    val r = figures
    ModelArms.foreach { a =>
      val f = arm(r, a)
      assert(f("costPerForecast").num > 0, f.toString)
      // A forecast's own tokens: 1,000 in and 200 out from the scripted model.
      assertEquals(f("meanTokens").num, 1200.0, f.toString)
      assert(f("meanLatencyMs").num >= 0, f.toString)
    }
    val venue = arm(r, "venue-price")
    assert(
      venue("costPerForecast").isNull && venue("meanTokens").isNull && venue(
        "meanLatencyMs"
      ).isNull,
      venue.toString
    )
  }

  // Graph and prose forecasts that differ by question, so their differences have a spread.
  private def pg(q: String): Double = 0.55 + (q.drop(1).toInt % 7) * 0.05
  private def pp(q: String): Double = 0.50 + (q.drop(1).toInt % 5) * 0.08

  Given(
    "the arm {string} has a score for {string} questions and the arm {string} has a score for {string} questions"
  ) { (a: String, na: String, b: String, nb: String) =>
    world.held("a") = s"$a:$na"
    world.held("b") = s"$b:$nb"
  }

  Given("{string} questions have a score for both") { (both: String) =>
    val Array(a, na)      = world.held("a").split(":")
    val Array(b, nb)      = world.held("b").split(":")
    val (n, onlyA, onlyB) = (both.toInt, na.toInt - both.toInt, nb.toInt - both.toInt)
    // q1… scored by both; then those only the first scores, then those only the second.
    answering { (q, arm) =>
      val i = q.drop(1).toInt
      if arm == b && i > n && i <= n + onlyA then Answer.Abstains
      else if arm == a && i > n + onlyA then Answer.Abstains
      else Answer.P(if arm == a then pg(q) else pp(q))
    }
    val qs = names(n + onlyA + onlyB)
    seed(qs)
    forecastAll()
    outcomes(qs.map(q => q -> yesOrNo(q)).toMap): Unit
  }

  private def both(n: Int, answer: (String, String) => Answer): Unit =
    answering(answer)
    val qs = names(n)
    seed(qs)
    forecastAll()
    outcomes(qs.map(q => q -> yesOrNo(q)).toMap): Unit

  Given("{string} questions have a score for both {string} and {string}") {
    (n: String, a: String, b: String) =>
      both(
        n.toInt,
        (q, arm) => Answer.P(if arm == a then pg(q) else if arm == b then pp(q) else 0.5)
      )
  }

  When("the experimenter asks for the comparison of {string} with {string}") {
    (a: String, b: String) =>
      world.last = get(s"/cohorts/$november/comparison?a=$a&b=$b")
  }

  Then("the comparison is made on {string} questions") { (n: String) =>
    assertEquals(figures("count").num.toInt, n.toInt)
  }

  Then(
    "the comparison gives the mean difference of their Brier scores, its interval and the count {string}"
  ) { (n: String) =>
    val c = figures
    assertEquals(c("count").num.toInt, n.toInt)
    val per = report()("perQuestion").arr
    def brier(q: String, a: String) =
      per.find(f => f("question").str == q && f("arm").str == a).get("brier").num
    val qs   = per.map(_("question").str).distinct.toList
    val mean = qs.map(q => brier(q, c("a").str) - brier(q, c("b").str)).sum / qs.size
    assertEqualsDouble(c("meanDifference").num, mean, 1e-12)
    val i = c("interval")
    assert(i("low").num <= mean && mean <= i("high").num, c.toString)
  }

  Then("the comparison is marked {string}") { (standing: String) =>
    assertEquals(figures("standing").str, standing)
  }

  Then("the comparison gives its count and no difference") { () =>
    val c = figures
    assertEquals(c("count").num.toInt, 5)
    assert(c("meanDifference").isNull && c("interval").isNull, c.toString)
  }

  /**
   * A comparison whose interval lies on the side of nought its bounds name. The bench draws the
   * interval from the scores, so the scenario's bounds are matched in sign, not to the digit.
   */
  Given("a comparison of {string} with {string} whose interval runs from {string} to {string}") {
    (a: String, b: String, low: String, high: String) =>
      val straddles = low.toDouble < 0 && high.toDouble > 0
      both(
        40,
        { (q, arm) =>
          val i = q.drop(1).toInt
          // Better for `a` on every question, or on every other one.
          val yes     = yesOrNo(q) == "yes"
          val better  = if yes then 0.8 else 0.2
          val worse   = if yes then 0.6 else 0.4
          val aBetter = !straddles || i % 2 == 0
          if arm == a then Answer.P(if aBetter then better else worse)
          else if arm == b then Answer.P(if aBetter then worse else better)
          else Answer.P(0.5)
        }
      )
      world.held("pair") = s"$a:$b"
  }

  When("the experimenter reads the comparison") { () =>
    val Array(a, b) = world.held("pair").split(":")
    world.last = get(s"/cohorts/$november/comparison?a=$a&b=$b")
  }

  Then("it says {string} has the lower Brier score") { (a: String) =>
    val c = figures
    assert(c("interval")("high").num < 0, c.toString)
    assertEquals(c("says").str, s"$a has the lower Brier score")
  }

  Then("it says no difference is shown") { () =>
    val c = figures
    assert(c("interval")("low").num < 0 && c("interval")("high").num > 0, c.toString)
    assertEquals(c("says").str, forecastbench.report.Comparison.NoDifference)
  }

  Given("every pair of arms has questions with a score for both") { () =>
    both(30, (q, arm) => Answer.P(0.3 + ((q.drop(1).toInt + arm.length) % 6) * 0.1))
  }

  Then("the first comparison is {string} with {string}") { (a: String, b: String) =>
    val first = figures("comparisons").arr.head
    assertEquals((first("a").str, first("b").str), (a, b))
  }

  Then("every other pair of arms is compared after it") { () =>
    val pairs = figures("comparisons").arr.toList.map(c => Set(c("a").str, c("b").str))
    assertEquals(pairs.size, 10)
    assertEquals(pairs.toSet, Arms5.combinations(2).map(_.toSet).toSet)
    assertEquals(pairs.head, Set("graph", "prose"))
  }

  private def band(p: Double): Int = (p * 10).toInt min 9

  Given("{string} forecasts of the arm {string} on questions with an outcome of yes or no") {
    (n: String, a: String) =>
      // Spread over the ten bands; yes on three of every four questions.
      answering((q, arm) =>
        if arm == a then Answer.P(((q.drop(1).toInt % 10) + 0.5) / 10) else Answer.P(0.5)
      )
      val qs = names(n.toInt)
      seed(qs)
      forecastAll()
      outcomes(qs.map(q => q -> (if q.drop(1).toInt % 4 == 0 then "no" else "yes")).toMap): Unit
      world.held("forecasts") = n
  }

  When("the experimenter asks for the calibration table of {string}") { (a: String) =>
    world.last = get(s"/cohorts/$november/calibration?arm=$a")
  }

  When("the experimenter reads the calibration table") { () =>
    world.last = get(s"/cohorts/$november/calibration?arm=graph")
  }

  private def bands: List[ujson.Value] = figures("bands").arr.toList

  Then(
    "each band gives its count of forecasts, their mean forecast, how often the outcome was yes and the interval of that"
  ) { () =>
    val per = report()("perQuestion").arr.filter(_("arm").str == "graph")
    assertEquals(bands.map(_("count").num.toInt).sum, world.held("forecasts").toInt)
    bands.zipWithIndex.foreach { (b, i) =>
      val in = per.filter(f => band(f("forecast").num) == i)
      assertEquals(b("count").num.toInt, in.size, b.toString)
      assertEqualsDouble(b("meanForecast").num, in.map(_("forecast").num).sum / in.size, 1e-9)
      assertEqualsDouble(
        b("shareYes").num,
        in.count(_("outcome").str == "yes").toDouble / in.size,
        1e-9
      )
      val w = b("interval")
      assert(w("low").num <= b("shareYes").num && b("shareYes").num <= w("high").num, b.toString)
    }
  }

  Then("the table has {string} bands of equal width from {string} to {string}") {
    (n: String, from: String, to: String) =>
      assertEquals(bands.size, n.toInt)
      val width = (to.toDouble - from.toDouble) / n.toInt
      bands.zipWithIndex.foreach { (b, i) =>
        assertEqualsDouble(b("from").num, from.toDouble + i * width, 1e-9)
        assertEqualsDouble(b("to").num, from.toDouble + (i + 1) * width, 1e-9)
      }
  }

  Given("a band of the calibration table of {string} with {string} forecasts") {
    (a: String, n: String) =>
      answering((_, arm) => if arm == a then Answer.P(0.35) else Answer.P(0.5))
      val qs = names(n.toInt)
      seed(qs)
      forecastAll()
      outcomes(qs.map(_ -> "yes").toMap): Unit
      world.held("band") = "3"
      world.held("forecasts") = n
  }

  Then("that band is marked {string}") { (marked: String) =>
    val b = bands(world.held("band").toInt)
    assertEquals(b("count").num.toInt, world.held("forecasts").toInt)
    assertEquals(b("tooFew").bool, marked == "too few")
  }

  Given("scored questions in the domains {string} and {string}") { (d1: String, d2: String) =>
    ok(
      put("/venues/kalshi/domains", ujson.write(ujson.Obj("Economics" -> d1, "Politics" -> d2)))
    ): Unit
    answering((q, _) => Answer.P(0.4 + (q.drop(1).toInt % 3) * 0.1))
    val qs = names(10)
    seed(qs, category = q => if q.drop(1).toInt % 2 == 0 then "Economics" else "Politics")
    forecastAll()
    outcomes(qs.map(_ -> "yes").toMap): Unit
    world.held("domains") = s"$d1:$d2"
  }

  Then(
    "each arm's scores are also given for each domain and for each horizon, each with its count"
  ) { () =>
    val r       = figures
    val domains = world.held("domains").split(":").toSet
    Arms5.foreach { a =>
      val byDomain = r("byDomain").arr.filter(_("arm").str == a)
      assertEquals(byDomain.map(_("key").str).toSet, domains)
      byDomain.foreach(d => assertEquals(d("questions").num.toInt, 5, d.toString))
      val byHorizon = r("byHorizon").arr.filter(_("arm").str == a)
      assert(byHorizon.nonEmpty)
      assertEquals(byHorizon.map(_("questions").num.toInt).sum, 10)
    }
  }

  Given("a question that closes {string} days after the forecast window ends") { (days: String) =>
    val end = java.time.Instant.parse(readCohort(november)("window")("to").str)
    answering((_, _) => Answer.P(0.6))
    seed(List("q1"), closes = _ => end.plus(Duration.ofDays(days.toLong)))
    forecastAll()
    outcomes(Map("q1" -> "yes")): Unit
  }

  Then("the question is counted in the horizon {string}") { (horizon: String) =>
    val r = figures("byHorizon").arr.filter(_("arm").str == "graph")
    assertEquals(r.map(h => h("key").str -> h("questions").num.toInt).toList, List(horizon -> 1))
  }

  Given(
    "the cohort has {string} questions of which {string} have an outcome of yes or no, {string} are waiting, {string} are void and {string} are disputed"
  ) { (all: String, scored: String, waiting: String, void: String, disputed: String) =>
    answering((_, _) => Answer.P(0.6))
    val qs = names(all.toInt)
    seed(qs)
    forecastAll()
    val (s, w, v) = (scored.toInt, waiting.toInt, void.toInt)
    val held = qs.zipWithIndex.collect {
      case (q, i) if i < s                       => q -> (if i % 2 == 0 then "yes" else "no")
      case (q, i) if i >= s + w && i < s + w + v => q -> "void"
      case (q, i) if i >= s + w + v              => q -> "yes"
    }
    outcomes(held.toMap, disputed = qs.drop(s + w + v).toSet): Unit
    assertEquals(qs.drop(s + w + v).size, disputed.toInt)
  }

  Then(
    "the report gives the counts {string} scored, {string} waiting, {string} void and {string} disputed"
  ) { (s: String, w: String, v: String, d: String) =>
    val q = figures("questions")
    assertEquals(
      (q("scored").num.toInt, q("waiting").num.toInt, q("void").num.toInt, q("disputed").num.toInt),
      (s.toInt, w.toInt, v.toInt, d.toInt)
    )
  }

  Given(
    "the arm {string} has {string} runs with a forecast, {string} runs abstained and {string} runs failed"
  ) { (a: String, f: String, ab: String, fl: String) =>
    val (nf, na) = (f.toInt, ab.toInt)
    answering { (q, arm) =>
      val i = q.drop(1).toInt
      if arm != a then Answer.P(0.5)
      else if i <= nf then Answer.P(0.6)
      else if i <= nf + na then Answer.Abstains
      else Answer.Fails
    }
    seed(names(nf + na + fl.toInt))
    forecastAll()
  }

  Then(
    "the arm {string} is given the counts {string} with a forecast, {string} abstained and {string} failed"
  ) { (a: String, f: String, ab: String, fl: String) =>
    val runs = arm(figures, a)("runs")
    assertEquals(
      (runs("forecast").num.toInt, runs("abstained").num.toInt, runs("failed").num.toInt),
      (f.toInt, ab.toInt, fl.toInt)
    )
  }

  Given("a question in the prospective cohorts {string} and {string}") { (a: String, b: String) =>
    assertEquals(world.cohort(a), november)
    val december = draft(b)
    settle(december, Nil, Arms5, budget = "10000.00")
    // Thirty questions in both cohorts, so the comparison has a difference to give; graph and
    // prose answer each question differently in the second cohort.
    val round = java.util.concurrent.ConcurrentHashMap[String, Int]()
    answering { (q, arm) =>
      val n = round.merge(s"$q.$arm", 1, _ + _)
      val i = q.drop(1).toInt
      if arm == "graph" then Answer.P(if n == 1 then pg(q) else 0.9 - (i % 4) * 0.05)
      else if arm == "prose" then Answer.P(if n == 1 then pp(q) else 0.45 + (i % 3) * 0.1)
      else Answer.P(0.5)
    }
    val qs  = names(30)
    val ids = seed(qs)
    ok(post(s"/cohorts/$december/questions", ujson.write(ujson.Obj("questions" -> ids)))): Unit
    forecastAll()
    forecastAll(december)
    outcomes(qs.map(q => q -> yesOrNo(q)).toMap, cohorts = List(november, december)): Unit
    world.held("cohorts") = s"$november,$december"
  }

  When("the experimenter asks for a report over both") { () =>
    world.last = get(s"/reports?cohorts=${world.held("cohorts")}")
  }

  Then("each comparison counts that question once, by the mean of its differences") { () =>
    val r  = figures
    val gp = r("comparisons").arr.find(c => c("a").str == "graph" && c("b").str == "prose").get
    def per(c: String) = json(ok(get(s"/cohorts/$c/report")))("perQuestion").arr
    val differences = world.held("cohorts").split(",").toList.map { c =>
      val f = per(c)
      def brier(q: String, a: String) =
        f.find(x => x("question").str == q && x("arm").str == a).get("brier").num
      f.map(_("question").str).distinct.map(q => q -> (brier(q, "graph") - brier(q, "prose"))).toMap
    }
    val qs   = differences.head.keys.toList
    val mean = qs.map(q => differences.map(_(q)).sum / differences.size).sum / qs.size
    assertEqualsDouble(gp("meanDifference").num, mean, 1e-12)
    r("comparisons").arr.foreach(c => assertEquals(c("count").num.toInt, 30, c.toString))
  }

  Given("no outcome and no run has been recorded since the last report") { () =>
    scoredQuestions(30, (q, _) => Answer.P(pg(q)), _ => "yes"): Unit
    world.held("before") = ok(get(s"/cohorts/$november/report"))
  }

  When("the experimenter asks for the report of the cohort again") { () =>
    world.last = get(s"/cohorts/$november/report")
  }

  Then("every figure is the same as before") { () =>
    def figuresOf(v: ujson.Value) =
      val c = ujson.copy(v)
      c.obj.remove("madeAt")
      c
    assertEquals(figuresOf(figures), figuresOf(json(world.held("before"))))
  }

  Given("the cohort is scored by the scoring method {string}") { (method: String) =>
    scoredQuestions(6, (_, _) => Answer.P(0.6), _ => "yes"): Unit
    ok(post(s"/cohorts/$november/score?method=$method", "{}")): Unit
    world.held("method") = method
  }

  Then(
    "the report names its scoring method, the cohort's model, each arm's instructions by version and the time it was made"
  ) { () =>
    val r = figures
    assertEquals(r("scoringMethod").num.toInt, world.held("method").toInt)
    assertEquals(r("models").arr.map(_.str).toList, List("model-a"))
    ModelArms.foreach(a => assertEquals(r("instructions")(a)("version").num.toInt, 1, a))
    assert(java.time.Instant.parse(r("madeAt").str).isAfter(java.time.Instant.EPOCH))
  }

  Given("no question of the cohort has an outcome") { () =>
    answering((_, _) => Answer.P(0.6))
    seed(names(5))
    forecastAll()
  }

  Then("the report says nothing is known yet and gives the count of questions waiting") { () =>
    val r = figures
    assertEquals(r("nothingKnown").str, "nothing is known yet")
    assertEquals(r("questions")("waiting").num.toInt, 5)
  }
