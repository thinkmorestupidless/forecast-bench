package forecastbench.steps

class ArmsFeatures extends FeatureSuite("features/arms/arms.feature") with CohortSetup:

  override protected def pending = Set(
    // T031, T055, T066: the forecast by each arm.
    "an arm answers a question with a probability",
    "the venue-price arm's forecast is the venue's probability observed in the forecast window",
    "the venue-price arm abstains when the venue gives no probability",
    "the model-alone arm is given the statement, the resolution criteria and the time the question closes",
    "no model arm is given the venue's probability",
    "the evidence arm is given what the model-alone arm is given and the evidence pack",
    "every model arm of a cohort uses the cohort's model",
    "a model arm's instructions are named by version in each run",
    "an arm may abstain and say why",
    "an answer that is not a probability is a failed run",
    "a model that cannot be reached is a failed run",
    "a failed run is made again only when the experimenter asks, and both runs are kept",
    "a run with a forecast is never made again"
  )

  /**
   * The background's cohort is drafted with its question here and frozen by the first step that
   * forecasts, with the arms the scenario names: a cohort is frozen before anything is observed of
   * it, and a frozen cohort could not be given the arms a scenario adds.
   */
  Given("a frozen cohort {string} with the model {string} and a prospective question {string}") {
    (name: String, model: String, question: String) =>
      val id = draft(name, model)
      settle(id, List(importedQuestion(question)), Nil)
  }

  Given("a drafted cohort {string}")((name: String) => draft(name): Unit)

  When("the experimenter adds the arm {string} to the cohort {string}") {
    (words: String, name: String) =>
      world.last = post(s"/cohorts/${world.cohort(name)}/arms", s"""{"arms":["${arm(words)}"]}""")
  }

  Then("the arm is refused") { () =>
    assertEquals(world.last._1, 422, world.last._2)
    assert(world.last._2.contains("the bench knows no arm 'crystal-ball'"), world.last._2)
  }
