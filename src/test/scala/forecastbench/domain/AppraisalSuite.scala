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
