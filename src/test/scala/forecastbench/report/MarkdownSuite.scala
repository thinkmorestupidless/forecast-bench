package forecastbench.report

import forecastbench.domain.InstructionRef

import java.time.Instant

class MarkdownSuite extends munit.FunSuite:

  private val empty = Report(
    List("november"),
    Instant.parse("2027-01-04T09:00:00Z"),
    1,
    List("model-a"),
    Map("model-alone" -> InstructionRef(1, "abc")),
    false,
    None,
    QuestionCounts(40, 35, 5, 0, 0),
    None,
    Nil,
    Nil,
    Nil,
    Nil,
    Nil,
    Report.Horizons,
    Nil,
    Nil
  )

  test("a comparison with too few questions prints no difference"):
    val c =
      Comparison("graph", "prose", 5, "too few", Some(-0.5), Some(Interval(-0.9, -0.1)), "x", "y")
    val page = Markdown.render(empty.copy(comparisons = List(c)))
    val row  = page.linesIterator.find(_.startsWith("| graph − prose")).get
    assert(!row.contains("-0.5000") && !row.contains("-0.9000"), row)
    assert(row.contains("| 5 | too few |"), row)
    assert(page.contains("graph against prose: 5 questions, too few to compare"), page)

  test("every comparison has its count, standing and interval"):
    val c = Comparison(
      "graph",
      "prose",
      120,
      "counted",
      Some(-0.02),
      Some(Interval(-0.031, -0.004)),
      "graph has the lower Brier score",
      "y"
    )
    val row = Markdown
      .render(empty.copy(comparisons = List(c)))
      .linesIterator
      .find(_.startsWith("| graph − prose"))
      .get
    assert(
      row.contains(
        "| 120 | counted | -0.0200 | -0.0310 to -0.0040 | graph has the lower Brier score |"
      ),
      row
    )

  test("the page names what is not yet known, the method, the model and the instructions"):
    val page = Markdown.render(empty.copy(nothingKnown = Some(Report.NothingKnown)))
    assert(page.contains("## Not yet known"))
    assert(page.contains("Nothing is known yet: 5 questions are waiting"), page)
    assert(page.contains("Scoring method 1. Model model-a."), page)
    assert(page.contains("model-alone version 1"), page)

  test("a development report says so on its face"):
    val page = Markdown.render(empty.copy(development = true, marking = Some(Report.Development)))
    assert(page.linesIterator.drop(2).next().contains(Report.Development), page)
