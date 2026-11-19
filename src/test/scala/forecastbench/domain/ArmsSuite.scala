package forecastbench.domain

import forecastbench.application.Instructions
import java.time.Instant

class ArmsSuite extends munit.FunSuite:

  private val q = QuestionGiven(
    "Will the Bank cut?",
    "Resolves yes if the Bank cuts.",
    Instant.parse("2027-02-01T00:00:00Z"),
    Instant.parse("2027-01-04T09:00:00Z")
  )

  test("the model-alone arm is given the block and nothing else"):
    val g = Arms.modelAlone.`given`(q, None, None).toOption.get
    assertEquals(
      g.user,
      """Question: Will the Bank cut?
        |Resolution criteria: Resolves yes if the Bank cuts.
        |Closes: 2027-02-01 00:00 UTC
        |Now: 2027-01-04 09:00 UTC""".stripMargin
    )

  test("what a model was given is kept as one text and read back the same"):
    val g = Given("instructions", "user text\nwith lines")
    assertEquals(Given.parse(g.text), Some(g))

  test("arms are known by name, and the features' words are their names"):
    assertEquals(Arms.named(Arms.byWords("venue price")).map(_.name), Some("venue-price"))
    assertEquals(Arms.named("crystal-ball"), None)

  test("the evidence arm is given what the model-alone arm is, then each document in order"):
    val now = java.time.Instant.parse("2027-01-04T09:00:00Z")
    val q =
      QuestionGiven("Will rates fall?", "Resolves yes if they fall.", now.plusSeconds(86400), now)
    val docs = List(
      Document(1, "news.example", "https://news.example/1", Some(now), "First excerpt.", "t"),
      Document(2, "blog.example", "https://blog.example/2", None, "Second excerpt.", "t")
    )
    val pack = Pack("c", "q", 1, now, Nil, docs, Usage.zero, BigDecimal(0))
    val text = Arms.evidence.`given`(q, Some(pack), None).toOption.get.user
    assertEquals(
      text,
      Arm.block(q) + "\nDocuments:\n" +
        "[1] news.example, 2027-01-04 09:00 UTC, https://news.example/1\nFirst excerpt.\n" +
        "[2] blog.example, undated, https://blog.example/2\nSecond excerpt."
    )
    assertEquals(
      Arms.evidence.`given`(q, Some(pack.copy(documents = Nil)), None),
      Left(Abstain("there was no evidence"))
    )
    assertEquals(Arms.evidence.`given`(q, None, None), Left(Abstain("there was no evidence")))

  test("the graph and prose arms are given one content in two shapes, and abstain without it"):
    val now = java.time.Instant.parse("2027-01-04T09:00:00Z")
    val q =
      QuestionGiven("Will rates fall?", "Resolves yes if they fall.", now.plusSeconds(86400), now)
    val doc  = Document(1, "news.example", "https://news.example/1", Some(now), "Rates held.", "t")
    val pack = Pack("c", "q", 1, now, Nil, List(doc), Usage.zero, BigDecimal(0))
    val c = AppraisalContent(
      List(
        ClaimGiven(
          "c1",
          "Rates held",
          Stance.Contradicts,
          1,
          "news.example",
          Some(now),
          "Rates held."
        )
      ),
      List("When")
    )
    val g = Arms.graph.`given`(q, Some(pack), Some(c)).toOption.get.user
    val p = Arms.prose.`given`(q, Some(pack), Some(c)).toOption.get.user
    assertEquals(g, Shape.graph(q, c))
    assertEquals(p, Shape.prose(q, c))
    assertEquals(Shape.contentOf(g), Shape.contentOf(p))
    assertEquals(Arms.graph.`given`(q, Some(pack), None), Left(Abstain("the appraisal failed")))
    assertEquals(
      Arms.prose.`given`(q, Some(pack.copy(documents = Nil)), Some(c)),
      Left(Abstain("there was no evidence"))
    )

  test(
    "the graph and prose instructions differ in the one sentence that says how the material is laid out"
  ):
    val g = Instructions.latest("graph").get.text.linesIterator.toList
    val p = Instructions.latest("prose").get.text.linesIterator.toList
    assertEquals(g.size, p.size)
    assertEquals(g.zip(p).count(_ != _), 1)
