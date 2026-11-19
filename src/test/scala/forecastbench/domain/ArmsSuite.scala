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
