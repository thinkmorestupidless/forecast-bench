package forecastbench.report

/**
 * The report as a page. Every figure has its count beside it, every comparison its interval and
 * standing; a comparison with too few questions prints no difference; what is not yet known is a
 * section of its own.
 */
object Markdown:

  private def n4(x: Double): String = f"$x%.4f"

  private def figure(f: Option[Figure]): String =
    f.fold("—")(x => n4(x.mean) + x.interval.fold("")(i => s" (${n4(i.low)} to ${n4(i.high)})"))

  private def opt[A](a: Option[A])(show: A => String): String = a.fold("—")(show)

  def render(r: Report): String =
    val b                          = StringBuilder()
    def line(s: String = ""): Unit = b.append(s).append('\n'): Unit

    line(s"# Report: ${r.cohorts.mkString(", ")}")
    line()
    r.marking.foreach(m => line(s"**$m**\n"))
    line(
      s"Made at ${r.madeAt}. Scoring method ${r.scoringMethod}. Model ${r.models.mkString(", ")}."
    )
    if r.instructions.nonEmpty then
      line(
        "Instructions: " + r.instructions.toList
          .sortBy(_._1)
          .map((k, v) => s"$k version ${v.version}")
          .mkString(", ") + "."
      )
    line()

    val q = r.questions
    line("## Questions")
    line()
    line(
      s"${q.questions} questions: ${q.scored} scored, ${q.waiting} waiting, ${q.void} void, ${q.disputed} disputed."
    )
    line()

    line("## Not yet known")
    line()
    r.nothingKnown.foreach(n =>
      line(s"${n.capitalize}: ${q.waiting} questions are waiting for their outcome.")
    )
    if r.nothingKnown.isEmpty && q.waiting > 0 then
      line(s"${q.waiting} questions are waiting for their outcome.")
    r.comparisons.filter(_.standing == Comparison.TooFew).foreach { c =>
      line(s"- ${c.a} against ${c.b}: ${c.count} questions, too few to compare.")
    }
    if r.nothingKnown.isEmpty && q.waiting == 0 && !r.comparisons.exists(
        _.standing == Comparison.TooFew
      )
    then line("Nothing the report counts is waiting.")
    line()

    line("## Arms")
    line()
    line(
      "| Arm | Questions | Brier | Log | Forecast / abstained / failed | Cost per forecast | Tokens | Latency (ms) |"
    )
    line("|---|---|---|---|---|---|---|---|")
    r.arms.foreach { a =>
      line(
        s"| ${a.arm} | ${a.questions} | ${figure(a.brier)} | ${figure(a.log)} | " +
          s"${a.runs.forecast} / ${a.runs.abstained} / ${a.runs.failed} | ${opt(a.costPerForecast)(_.toString)} | " +
          s"${opt(a.meanTokens)(t => f"$t%.0f")} | ${opt(a.meanLatencyMs)(t => f"$t%.0f")} |"
      )
    }
    line()

    line("## Comparisons")
    line()
    line(
      "Each difference is the first arm's mean Brier score less the second's, on the questions both scored."
    )
    line()
    line("| Arms | Questions | Standing | Difference | Interval | Says | Measures |")
    line("|---|---|---|---|---|---|---|")
    r.comparisons.foreach { c =>
      val diff = if c.standing == Comparison.TooFew then "—" else opt(c.meanDifference)(n4)
      val interval =
        if c.standing == Comparison.TooFew then "—"
        else opt(c.interval)(i => s"${n4(i.low)} to ${n4(i.high)}")
      line(
        s"| ${c.a} − ${c.b} | ${c.count} | ${c.standing} | $diff | $interval | ${c.says} | ${c.measures} |"
      )
    }
    line()

    line("## Calibration")
    r.calibration.foreach { cal =>
      line()
      line(s"### ${cal.arm}")
      line()
      line("| Band | Forecasts | Mean forecast | Share yes | Interval | |")
      line("|---|---|---|---|---|---|")
      cal.bands.foreach { band =>
        line(
          f"| ${band.from}%.1f to ${band.to}%.1f | ${band.count} | ${opt(band.meanForecast)(n4)} | " +
            s"${opt(band.shareYes)(n4)} | ${opt(band.interval)(i => s"${n4(i.low)} to ${n4(i.high)}")} | " +
            s"${if band.tooFew then "too few" else ""} |"
        )
      }
    }
    line()

    def breakdown(title: String, rows: List[Breakdown]): Unit =
      line(s"## $title")
      line()
      line("| Arm | | Questions | Brier | Log |")
      line("|---|---|---|---|---|")
      rows.foreach(x =>
        line(s"| ${x.arm} | ${x.key} | ${x.questions} | ${opt(x.brier)(n4)} | ${opt(x.log)(n4)} |")
      )
      line()

    breakdown("By domain", r.byDomain)
    breakdown("By horizon", r.byHorizon)
    line(
      "Horizons, from the end of the forecast window to the time a question closes: " +
        r.horizons.map(h => s"${h.name} ${h.upTo.fold(s"more than ${h.after} days")(u => s"more than ${h.after} and up to $u days")}").mkString("; ") + "."
    )

    if r.leftOut.nonEmpty then
      line()
      line("## Left out")
      line()
      r.leftOut.foreach(l => line(s"- ${l.cohort}: ${l.reason}"))
    b.toString
