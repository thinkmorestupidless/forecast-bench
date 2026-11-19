package forecastbench.report

import forecastbench.domain.*

import java.time.{Duration, Instant}

/** A mean with its interval. */
final case class Figure(mean: Double, interval: Option[Interval])

final case class RunCounts(forecast: Int, abstained: Int, failed: Int)

/** One arm across the report's questions. Cost, tokens and latency are for model arms only. */
final case class ArmFigures(
    arm: String,
    questions: Int,
    brier: Option[Figure],
    log: Option[Figure],
    runs: RunCounts,
    costPerForecast: Option[BigDecimal],
    meanTokens: Option[Double],
    meanLatencyMs: Option[Double]
)

/** An arm's scores for one domain or one horizon. */
final case class Breakdown(
    arm: String,
    key: String,
    questions: Int,
    brier: Option[Double],
    log: Option[Double]
)

/** A horizon and its bounds in days: more than `after`, up to `upTo`. */
final case class Horizon(name: String, after: Int, upTo: Option[Int])

final case class QuestionCounts(questions: Int, scored: Int, waiting: Int, void: Int, disputed: Int)

final case class LeftOut(cohort: String, reason: String)

final case class ArmCalibration(arm: String, bands: List[Band])

/** An arm's forecast for one question, the mean of its repetitions, and its scores. */
final case class QuestionFigure(
    question: String,
    arm: String,
    forecast: Double,
    spread: Double,
    repetitions: Int,
    outcome: Option[OutcomeValue],
    brier: Option[Double],
    log: Option[Double]
)

/** What a cohort, or several, have shown so far (data-model.md, Report); computed, never held. */
final case class Report(
    cohorts: List[String],
    madeAt: Instant,
    scoringMethod: Int,
    models: List[String],
    instructions: Map[String, InstructionRef],
    development: Boolean,
    marking: Option[String],
    questions: QuestionCounts,
    nothingKnown: Option[String],
    arms: List[ArmFigures],
    comparisons: List[Comparison],
    calibration: List[ArmCalibration],
    byDomain: List[Breakdown],
    byHorizon: List[Breakdown],
    horizons: List[Horizon],
    leftOut: List[LeftOut],
    perQuestion: List[QuestionFigure]
):
  /** Everything but the time it was made: two reports of the same records are equal in this. */
  def figures: Report = copy(madeAt = Instant.EPOCH)

object Report:

  val Development  = "development: the model may know these outcomes"
  val NothingKnown = "nothing is known yet"

  val Horizons: List[Horizon] =
    List(Horizon("days", 0, Some(7)), Horizon("weeks", 7, Some(28)), Horizon("months", 28, None))

  /** The horizon of a question that closes this long after the forecast window ends. */
  def horizon(after: Duration): String =
    val days = after.toMinutes.toDouble / (24 * 60)
    Horizons.find(h => h.upTo.forall(days <= _)).map(_.name).getOrElse("months")

  /** The arms in the bench's order, then any other in the order the cohorts name them. */
  private def ordered(names: Seq[String]): List[String] =
    val known = Arms.all.map(_.name)
    (known.filter(names.contains) ++ names.filterNot(known.contains)).distinct

  /** The pairs to compare: graph with prose first when both are named, then every other pair. */
  def pairs(arms: List[String]): List[(String, String)] =
    val all = for i <- arms.indices.toList; j <- arms.indices.toList if i < j
    yield (arms(i), arms(j))
    val first = all.filter(_ == ("graph", "prose"))
    first ++ all.filterNot(first.contains)

  private def mean(xs: Seq[Double]): Option[Double] = Option.when(xs.nonEmpty)(xs.sum / xs.size)

  def build(
      cohorts: List[Cohort],
      questions: Map[String, Question],
      runs: List[RunRecorded],
      method: Scoring.Method,
      madeAt: Instant
  ): Report =
    val included =
      if cohorts.size <= 1 then cohorts else cohorts.filter(_.standing == Standing.Prospective)
    val leftOut = cohorts.filterNot(included.contains).map(c => LeftOut(c.id, Development))
    val ids     = included.map(_.id).sorted
    val inRuns  = runs.filter(r => ids.contains(r.cohort))
    val arms    = ordered(included.flatMap(_.arms))
    val qs      = included.flatMap(_.questions).distinct.flatMap(questions.get)

    val counts = QuestionCounts(
      qs.size,
      qs.count(_.current.exists(_.settled)),
      qs.count(_.current.isEmpty),
      qs.count(_.current.exists(o => !o.disputed && o.value == OutcomeValue.Void)),
      qs.count(_.current.exists(_.disputed))
    )

    // Each cohort's forecast of each question by each arm, the mean of its repetitions.
    final case class Made(cohort: Cohort, q: Question, arm: String, f: Scoring.ArmForecast)
    val made = for
      c   <- included
      q   <- c.questions.flatMap(questions.get)
      arm <- c.arms
      f <- Scoring.armForecast(
        inRuns
          .filter(r => r.cohort == c.id && r.question == q.id && r.arm == arm)
          .flatMap(_.ending.forecast)
      )
    yield Made(c, q, arm, f)

    def scored(m: Made): Option[(Double, Double)] =
      m.q.current.filter(_.settled).flatMap(o => Scoring.score(method, m.f.mean, o.value))

    // One figure for each question and arm: over several cohorts, the mean of theirs.
    val perQuestion = made
      .groupBy(m => (m.q.id, m.arm))
      .toList
      .sortBy(_._1)
      .map { case ((q, arm), ms) =>
        val scores = ms.flatMap(scored)
        QuestionFigure(
          q,
          arm,
          ms.map(_.f.mean).sum / ms.size,
          ms.map(_.f.spread).sum / ms.size,
          ms.map(_.f.repetitions).sum,
          ms.head.q.current.filter(_.settled).map(_.value),
          mean(scores.map(_._1)),
          mean(scores.map(_._2))
        )
      }
    val scoredFigures = perQuestion.filter(_.brier.isDefined)

    def seed(parts: String*) = Bootstrap.seed((method.version.toString +: ids) ++ parts*)
    def group(q: String)     = questions.get(q).map(_.eventGroup).getOrElse(q)

    val armFigures = arms.map { arm =>
      val fs        = scoredFigures.filter(_.arm == arm)
      val armRuns   = inRuns.filter(_.arm == arm)
      val usesModel = Arms.named(arm).forall(_.usesModel)
      val forecasts = armRuns.count(_.ending.forecast.isDefined)
      def figure(of: QuestionFigure => Double, what: String) =
        mean(fs.map(of)).map(m =>
          Figure(
            m,
            Bootstrap.mean(
              fs.map(f => (group(f.question), of(f))),
              seed(arm, what),
              method.resamples,
              method.confidence
            )
          )
        )
      ArmFigures(
        arm,
        fs.size,
        figure(_.brier.get, "brier"),
        figure(_.log.get, "log"),
        RunCounts(
          forecasts,
          armRuns.count(_.ending.isInstanceOf[Ending.Abstained]),
          armRuns.count(_.ending.isInstanceOf[Ending.Failed])
        ),
        Option.when(usesModel && forecasts > 0)(
          (armRuns.map(r => r.cost + r.appraisalShare).sum / forecasts)
            .setScale(6, BigDecimal.RoundingMode.HALF_UP)
        ),
        Option.when(usesModel)(mean(armRuns.flatMap(_.usage).map(_.total.toDouble))).flatten,
        Option
          .when(usesModel)(mean(armRuns.filter(_.model.isDefined).map(_.latencyMs.toDouble)))
          .flatten
      )
    }

    val comparisons = pairs(arms).map { (a, b) =>
      val paired = for
        c  <- included
        q  <- c.questions
        ma <- made.find(m => m.cohort.id == c.id && m.q.id == q && m.arm == a)
        mb <- made.find(m => m.cohort.id == c.id && m.q.id == q && m.arm == b)
        sa <- scored(ma)
        sb <- scored(mb)
      yield Paired(q, group(q), sa._1 - sb._1)
      Comparison.of(a, b, paired, seed(a, b), method)
    }

    val calibration = arms.map { arm =>
      ArmCalibration(
        arm,
        Calibration.table(
          scoredFigures
            .filter(_.arm == arm)
            .map(f => (f.forecast, f.outcome.contains(OutcomeValue.Yes)))
        )
      )
    }

    def breakdown(key: QuestionFigure => String): List[Breakdown] =
      scoredFigures
        .groupBy(f => (f.arm, key(f)))
        .toList
        .sortBy((k, _) => (arms.indexOf(k._1), k._2))
        .map { case ((arm, k), fs) =>
          Breakdown(arm, k, fs.size, mean(fs.flatMap(_.brier)), mean(fs.flatMap(_.log)))
        }

    def horizonOf(q: String): String =
      val c = included.find(_.questions.contains(q))
      (for
        cohort <- c
        w      <- cohort.window
        held   <- questions.get(q)
      yield horizon(Duration.between(w.to, held.closesAt))).getOrElse("months")

    val development = included.nonEmpty && included.forall(_.standing == Standing.Development)

    Report(
      cohorts = ids,
      madeAt = madeAt,
      scoringMethod = method.version,
      models = included.map(_.model).distinct.sorted,
      instructions = included.flatMap(_.instructions).toMap,
      development = development,
      marking = Option.when(development)(Development),
      questions = counts,
      nothingKnown = Option.when(counts.scored == 0)(NothingKnown),
      arms = armFigures,
      comparisons = comparisons,
      calibration = calibration,
      byDomain = breakdown(f => questions.get(f.question).map(_.domain).getOrElse("other")),
      byHorizon = breakdown(f => horizonOf(f.question)),
      horizons = Horizons,
      leftOut = leftOut,
      perQuestion = perQuestion
    )
