package forecastbench.domain

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
