package forecastbench.steps

class OutcomeFeatures extends FeatureSuite("features/outcomes/outcome.feature") with OutcomeSteps:

  Given("a prospective question {string} in a frozen cohort {string} with runs") {
    (question: String, cohort: String) =>
      val id = draft(cohort)
      settle(id, List(importedQuestion(question)), List("model-alone"))
      freeze(id): Unit
      ok(forecastAndWait(id)): Unit
  }

  Given("the venue resolves {string} as {string}") { (name: String, result: String) =>
    resolve(name, result)
  }

  When("the bench reads outcomes") { () =>
    acted()
    readOutcomes(): Unit
  }

  Then("the outcome names its venue and the time it resolved") { () =>
    val q = json(ok(get(s"/questions/${questionId("rates")}")))
    assertEquals(q("venue").str, "kalshi")
    assert(!outcomeOf("rates")("resolvedAt").isNull)
  }

  Given("the venue cancels {string}")((name: String) => resolve(name, "void"))

  Given("the venue has not resolved {string}")((_: String) => ())

  Then("{string} has no outcome") { (name: String) =>
    assertEquals(get(s"/questions/${questionId(name)}/outcome")._2.trim, "null")
  }

  Then("{string} is waiting") { (name: String) =>
    eventually(s"${questionId(name)} to be waiting") {
      val rows = json(ok(get("/questions/?waiting=true&venue=kalshi"))).arr.map(_("id").str)
      Option.when(rows.contains(questionId(name)))(())
    }
  }

  Given("the venue resolves {string} as {string} and marks it disputed") {
    (name: String, result: String) =>
      resolve(name, result, disputed = true)
  }

  When("the venue settles the dispute as {string}") { (result: String) =>
    acted()
    resolve("rates", result)
    readOutcomes()
  }

  Then("the earlier outcome is still held") { () =>
    assertEquals(outcomes("rates").size, 2)
  }

  When("the venue changes its result for {string} to {string}") { (name: String, result: String) =>
    acted()
    resolve(name, result)
    readOutcomes()
  }

  Then("the earlier outcome is still held, revised by the later one") { () =>
    val all = outcomes("rates")
    assertEquals(all.size, 2)
    assertEquals(all(1)("revises").num.toInt, all(0)("seq").num.toInt)
    assertEquals(all(0)("value").str, "yes")
  }

  When("the experimenter tries to state an outcome for {string}") { (name: String) =>
    acted()
    world.last = post(s"/questions/${questionId(name)}/outcome", """{"value":"yes"}""")
  }

  Then("the outcome is refused") { () =>
    assertEquals(world.last._1, 405, world.last._2)
    assertEquals(get(s"/questions/${questionId("rates")}/outcome")._2.trim, "null")
  }

  When("the bench reads outcomes and the venue cannot be read") { () =>
    acted()
    resolve("rates", "no")
    fixture.kalshi.cannotBeRead(true)
    try
      val read = json(readOutcomes())
      assert(read("unreadable").arr.map(_.str).contains("kalshi"), read.toString)
    finally fixture.kalshi.cannotBeRead(false)
  }

  private def outcomes(name: String): List[ujson.Value] =
    json(ok(get(s"/questions/${questionId(name)}")))("outcomes").arr.toList
