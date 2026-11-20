package forecastbench.domain

class AppraisalSuite extends munit.FunSuite:

  private val doc = Document(
    3,
    "news.example",
    "https://news.example/3",
    None,
    "The bank\n  held   rates\tin November.",
    "t"
  )

  test("a claim whose quote is in its document holds, white space read as one space"):
    val checked = Appraisal.check(
      doc,
      List(ReaderClaim("Rates were held", "supports", "held rates in\nNovember"))
    )
    assertEquals(
      checked.map(_.map(c => (c.stance, c.statement, c.quote))),
      Right(List((Stance.Supports, "Rates were held", "held rates in November")))
    )

  test("a quote written with plain quotes, dashes and dots holds against a document's typography"):
    val typeset = doc.copy(excerpt =
      "\u201cI know people want it sooner,\u201d he said\u00a0\u2014 it\u2019s up to her\u2026"
    )
    val checked = Appraisal.check(
      typeset,
      List(
        ReaderClaim(
          "It is hers",
          "supports",
          "\"I know people want it sooner,\" he said - it's up to her..."
        )
      )
    )
    assert(checked.isRight, checked.toString)

  test("a claim whose quote is in no document of the pack fails, naming it"):
    val checked = Appraisal.check(
      doc,
      List(
        ReaderClaim("Rates were held", "supports", "held rates"),
        ReaderClaim("Rates were cut", "contradicts", "cut rates")
      )
    )
    assertEquals(
      checked,
      Left(ClaimRefused(Appraisal.NotInDocument, "claim 2 of document [3]: Rates were cut"))
    )

  test("a stance other than supports or contradicts fails"):
    assertEquals(
      Appraisal
        .check(doc, List(ReaderClaim("Rates were held", "neutral", "held")))
        .left
        .map(_.reason),
      Left(Appraisal.NoStance)
    )

  test("a statement that is empty or longer than 1,000 characters fails"):
    assertEquals(
      Appraisal.check(doc, List(ReaderClaim("  ", "supports", "held"))).left.map(_.reason),
      Left(Appraisal.BadStatement)
    )
    assertEquals(
      Appraisal.check(doc, List(ReaderClaim("x" * 1001, "supports", "held"))).left.map(_.reason),
      Left(Appraisal.BadStatement)
    )

  test("an empty quote derives from nothing"):
    assertEquals(
      Appraisal.check(doc, List(ReaderClaim("Rates", "supports", " "))).left.map(_.reason),
      Left(Appraisal.NotInDocument)
    )

  test("a document with no claim checks to none"):
    assertEquals(Appraisal.check(doc, Nil), Right(Nil))
