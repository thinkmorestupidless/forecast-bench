package forecastbench.report

import forecastbench.domain.*

import java.time.{Duration, Instant}

/**
 * The report against a fixture worked by hand (SC-004). Fifty questions with an outcome, forty
 * yes and ten no; the model-alone arm says 0.8 to every one and the venue-price arm 0.6. Six more
 * questions: three waiting, two void, one disputed. By hand, to four places:
 *
 *   model-alone  Brier (40 x 0.04 + 10 x 0.64) / 50                     = 0.1600
 *                log   (40 x -ln 0.8 + 10 x -ln 0.2) / 50
 *                      = (40 x 0.223144 + 10 x 1.609438) / 50            = 0.5004
 *   venue-price  Brier (40 x 0.16 + 10 x 0.36) / 50                     = 0.2000
 *                log   (40 x 0.510826 + 10 x 0.916291) / 50              = 0.5919
 *   difference   venue-price - model-alone (the bench's order): yes 0.12, no -0.28,
 *                (40 x 0.12 - 10 x 0.28) / 50                           = 0.0400 on 50 questions
 *
 * The questions in the domain "economy" are the first twenty-five (twenty yes, five no), the rest
 * "politics"; so model-alone on economy is (20 x 0.04 + 5 x 0.64) / 25 = 0.1600 as well, and on
 * the questions closing within a week of the window (the first ten, all yes) 0.0400.
 */
class ReportSuite extends munit.FunSuite:

  private val start  = Instant.parse("2027-01-04T09:00:00Z")
  private val window = Window(start, start.plus(Duration.ofDays(2)))

  private def r4(x: Double) = BigDecimal(x).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

  private def question(i: Int, outcome: Option[Outcome]) = Question(
    s"kalshi.q$i",
    "kalshi",
    s"q$i",
    s"group-${i / 2}",
    s"Will $i happen?",
    "criteria",
    window.to.plus(Duration.ofDays(if i <= 10 then 5 else 40)),
    None,
    if i <= 25 then "economy" else "politics",
    Standing.Prospective,
    start,
    outcome.toList
  )

  private def outcome(v: OutcomeValue, disputed: Boolean = false) =
    Some(Outcome(1, v, disputed, Some(start), start, None))

  private val scoredQs = (1 to 50).map { i =>
    val yes = if i <= 25 then i <= 20 else i <= 45
    question(i, outcome(if yes then OutcomeValue.Yes else OutcomeValue.No))
  }
  private val otherQs = Seq(
    question(51, None),
    question(52, None),
    question(53, None),
    question(54, outcome(OutcomeValue.Void)),
    question(55, outcome(OutcomeValue.Void)),
    question(56, outcome(OutcomeValue.Yes, disputed = true))
  )
  private val questions = (scoredQs ++ otherQs).map(q => q.id -> q).toMap

  private val cohort = Cohort(
    "november",
    Standing.Prospective,
    "model-a",
    None,
    start,
    questions.keys.toList.sorted,
    List("venue-price", "model-alone"),
    Some(window),
    1,
    Some(BigDecimal(100)),
    CohortStatus.Frozen,
    Some(start),
    Map("model-alone" -> InstructionRef(1, "abc")),
    Some(1),
    Some(1),
    BigDecimal(0),
    Map.empty,
    Nil,
    Map.empty,
    Some(start),
    Nil
  )

  private def run(q: String, arm: String, p: Double) = RunRecorded(
    Ids.run("november", q, arm, 1, 1),
    "november",
    q,
    arm,
    1,
    1,
    Ending.Forecast(p),
    start,
    Option.when(arm != "venue-price")("model-a"),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Nil,
    Option.when(arm != "venue-price")(Usage(1000, 200)),
    if arm == "venue-price" then BigDecimal(0) else BigDecimal("0.01"),
    BigDecimal(0),
    if arm == "venue-price" then 0 else 1500,
    None
  )

  private val runs = questions.keys.toList.sorted.flatMap(q =>
    List(run(q, "model-alone", 0.8), run(q, "venue-price", 0.6))
  )

  private def report = Report.build(List(cohort), questions, runs, Scoring.One, start)

  test("each arm's mean scores and the questions behind them, worked by hand"):
    val arms = report.arms.map(a => a.arm -> a).toMap
    assertEquals(report.arms.map(_.arm), List("venue-price", "model-alone"))
    assertEquals(arms("model-alone").questions, 50)
    assertEquals(r4(arms("model-alone").brier.get.mean), 0.16)
    assertEquals(r4(arms("model-alone").log.get.mean), 0.5004)
    assertEquals(r4(arms("venue-price").brier.get.mean), 0.2)
    assertEquals(r4(arms("venue-price").log.get.mean), 0.5919)

  test("the comparison on the questions both scored"):
    val c = report.comparisons.head
    assertEquals((c.a, c.b, c.count, c.standing), ("venue-price", "model-alone", 50, "preliminary"))
    assertEquals(r4(c.meanDifference.get), 0.04)
    assert(c.interval.get.low <= 0.04 && 0.04 <= c.interval.get.high)

  test("what is left out is counted"):
    assertEquals(report.questions, QuestionCounts(56, 50, 3, 2, 1))
    assertEquals(report.nothingKnown, None)

  test("cost, tokens and latency for the model arm only"):
    val arms = report.arms.map(a => a.arm -> a).toMap
    // 56 runs at 0.01 over 56 forecasts.
    assertEquals(arms("model-alone").costPerForecast, Some(BigDecimal("0.010000")))
    assertEquals(arms("model-alone").meanTokens, Some(1200.0))
    assertEquals(arms("model-alone").meanLatencyMs, Some(1500.0))
    assertEquals(arms("venue-price").costPerForecast, None)
    assertEquals(arms("model-alone").runs, RunCounts(56, 0, 0))

  test("a report scores by its cohorts' one method, and refuses cohorts frozen under two"):
    assertEquals(Report.methodOf(List(cohort), 9), Right(1))
    assertEquals(Report.methodOf(List(cohort.copy(scoringMethod = None)), 9), Right(9))
    assert(
      Report
        .methodOf(List(cohort, cohort.copy(id = "december", scoringMethod = Some(2))), 1)
        .left
        .exists(_.contains("different methods (1, 2)"))
    )

  test("a graph run's cost, which holds its share of the appraisal, is counted once"):
    // Each graph run cost 0.01 for its own call and 0.04 for its share of the appraisal, recorded
    // together as 0.05 (FR-027): 56 runs at 0.05 over 56 forecasts is 0.05 a forecast, not 0.09.
    val graph = questions.keys.toList.sorted.map(q =>
      run(q, "graph", 0.8).copy(cost = BigDecimal("0.05"), appraisalShare = BigDecimal("0.04"))
    )
    val withGraph = cohort.copy(arms = List("model-alone", "graph"))
    val r         = Report.build(List(withGraph), questions, graph, Scoring.One, start)
    assertEquals(
      r.arms.find(_.arm == "graph").flatMap(_.costPerForecast),
      Some(BigDecimal("0.050000"))
    )

  test("calibration: every forecast of the model-alone arm in the band from 0.8"):
    val bands = report.calibration.find(_.arm == "model-alone").get.bands
    assertEquals(bands(8).count, 50)
    assertEquals(r4(bands(8).shareYes.get), 0.8)
    assertEquals(bands.map(_.count).sum, 50)

  test("by domain and by horizon, each with its count"):
    val domain = report.byDomain
      .filter(_.arm == "model-alone")
      .map(b => b.key -> (b.questions, r4(b.brier.get)))
      .toMap
    assertEquals(domain, Map("economy" -> (25, 0.16), "politics" -> (25, 0.16)))
    val horizon = report.byHorizon
      .filter(_.arm == "model-alone")
      .map(b => b.key -> (b.questions, r4(b.brier.get)))
      .toMap
    assertEquals(horizon("days"), (10, 0.04))
    assertEquals(horizon("months")._1, 40)

  test("the same input gives the same figures"):
    assertEquals(
      report.figures,
      Report.build(List(cohort), questions, runs, Scoring.One, start.plusSeconds(60)).figures
    )

  test("the horizons: up to a week is days, up to four weeks weeks, beyond that months"):
    List(3 -> "days", 7 -> "days", 8 -> "weeks", 28 -> "weeks", 29 -> "months", 200 -> "months")
      .foreach { (d, h) =>
        assertEquals(Report.horizon(Duration.ofDays(d.toLong)), h, s"$d days")
      }

  test("no scored question: nothing is known yet"):
    val none =
      Report.build(List(cohort), otherQs.map(q => q.id -> q).toMap, Nil, Scoring.One, start)
    assertEquals(none.nothingKnown, Some(Report.NothingKnown))

  test("graph with prose leads the comparisons"):
    assertEquals(
      Report.pairs(List("venue-price", "model-alone", "evidence", "graph", "prose")).take(2),
      List(("graph", "prose"), ("venue-price", "model-alone"))
    )

  test("over several cohorts a development cohort is left out"):
    val dev = cohort.copy(id = "rehearsal", standing = Standing.Development)
    val r   = Report.build(List(cohort, dev), questions, runs, Scoring.One, start)
    assertEquals(r.cohorts, List("november"))
    assertEquals(r.leftOut, List(LeftOut("rehearsal", Report.Development)))
    val alone = Report.build(List(dev), questions, Nil, Scoring.One, start)
    assertEquals(alone.marking, Some(Report.Development))
