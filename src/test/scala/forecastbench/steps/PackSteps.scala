package forecastbench.steps

/** The pack of the background's question, as the features that read one share it. */
trait PackSteps extends ModelSteps:

  protected def packPath = s"/cohorts/$november/packs/${questionId("rates")}"

  /** Freezes the cohort with the evidence arm, if a step has not, and waits for the pack's note. */
  protected def gathered(): ujson.Value =
    frozenWith("evidence")
    eventually("the pack to be noted") {
      readCohort(november)("packs").obj.get(questionId("rates"))
    }

  Given("the question {string} has an evidence pack") { (_: String) =>
    assertEquals(gathered()("state").str, "gathered")
  }
