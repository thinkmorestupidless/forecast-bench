package forecastbench.steps

import java.time.Duration

class CohortFeatures extends FeatureSuite("features/cohorts/cohort.feature") with CohortSetup:

  private def november = world.cohort("november")

  Given("a drafted cohort {string} with the model {string}") { (name: String, model: String) =>
    draft(name, model): Unit
  }

  When("the experimenter reads the cohort {string}") { (name: String) =>
    world.last = get(s"/cohorts/${world.cohort(name)}")
  }

  Then("it names its questions, its arms, its model, its forecast window and its budget") { () =>
    val c = json(ok(world.last)).obj
    List("questions", "arms", "model", "window", "budget").foreach(k =>
      assert(c.contains(k), s"the cohort does not name its $k: ${world.last._2}")
    )
    assertEquals(c("model").str, "model-a")
  }

  When("the experimenter adds a prospective question and the arm {string} to the cohort") {
    (words: String) =>
      val q = importedQuestion("added")
      ok(post(s"/cohorts/$november/questions", s"""{"questions":["$q"]}""")): Unit
      ok(post(s"/cohorts/$november/arms", s"""{"arms":["${arm(words)}"]}""")): Unit
  }

  Then("the cohort names that question and that arm") { () =>
    val c = readCohort(november)
    assertEquals(c("questions").arr.map(_.str).toList, List(questionId("added")))
    assertEquals(c("arms").arr.map(_.str).toList, List("model-alone"))
  }

  Given("the cohort {string} is frozen") { (name: String) =>
    val id = world.cohort(name)
    settle(id, List(importedQuestion("frozen")), List("model-alone"))
    world.held("frozen") = freeze(id)
  }

  When("the experimenter tries to change its {}") { (part: String) =>
    world.last = part match
      case "questions" =>
        post(s"/cohorts/$november/questions", s"""{"questions":["${importedQuestion("late")}"]}""")
      case "arms"  => post(s"/cohorts/$november/arms", """{"arms":["venue-price"]}""")
      case "model" => put(s"/cohorts/$november", """{"model":"model-b"}""")
      case "forecast window" =>
        val now = fixture.clock.now
        put(s"/cohorts/$november", window(now, now.plus(Duration.ofDays(5))))
      case "repetitions" => put(s"/cohorts/$november", """{"repetitions":5}""")
      case other         => fail(s"no part '$other'")
  }

  Then("the change is refused") { () =>
    assertEquals(world.last._1, 409, world.last._2)
    assert(world.last._2.contains("the cohort is frozen"), world.last._2)
  }

  Then("the cohort is as it was frozen") { () =>
    // What freezing fixes; the packs it then gathers are noted on the cohort as they come.
    val fixed = List(
      "questions",
      "arms",
      "model",
      "window",
      "repetitions",
      "budget",
      "status",
      "instructions",
      "frozenAt"
    )
    val held = readCohort(november)
    val was  = json(world.held("frozen"))
    fixed.foreach(k => assertEquals(held(k), was(k), k))
  }

  Given("the cohort names a question that closes before the forecast window ends") { () =>
    settle(november, List(importedQuestion("early", Duration.ofDays(1))), List("model-alone"))
  }

  When("the experimenter freezes the cohort") { () =>
    world.last = post(s"/cohorts/$november/freeze", "{}")
  }

  Then("the experimenter is refused, naming the question") { () =>
    assertEquals(world.last._1, 422, world.last._2)
    assert(world.last._2.contains(questionId("early")), world.last._2)
  }

  Given("the cohort names no questions") { () =>
    settle(november, Nil, List("model-alone"))
  }

  Then("the experimenter is refused") { () =>
    assertEquals(world.last._1, 422, world.last._2)
    assert(world.last._2.contains("the cohort names no questions"), world.last._2)
    assertEquals(readCohort(november)("status").str, "drafted")
  }

  Given("a prospective question in the frozen cohort {string}") { (name: String) =>
    val id = world.cohort(name)
    val q  = importedQuestion("shared")
    settle(id, List(q), List("model-alone"))
    freeze(id): Unit
  }

  When("the experimenter adds that question to a drafted cohort {string}") { (name: String) =>
    val id = draft(name)
    world.last = post(s"/cohorts/$id/questions", s"""{"questions":["${questionId("shared")}"]}""")
  }

  Then("the cohort {string} names that question") { (name: String) =>
    ok(world.last): Unit
    assert(
      readCohort(world.cohort(name))("questions").arr.map(_.str).contains(questionId("shared"))
    )
  }

  When("the experimenter asks for the cohort {string} to be forecast") { (name: String) =>
    world.last = forecastAndWait(world.cohort(name))
  }

  When("the experimenter asks for the cohort to be forecast") { () =>
    world.last = forecastAndWait(november)
  }

  Then("nothing is forecast") { () =>
    assertEquals(world.last._1, 409, world.last._2)
    Thread.sleep(300)
    assertEquals(recorded(november), Nil)
  }

  Then("the experimenter is told the cohort is not frozen") { () =>
    assert(world.last._2.contains("the cohort is not frozen"), world.last._2)
  }

  Given("the cohort names {string} questions, {string} arms and {string} repetitions") {
    (questions: String, arms: String, repetitions: String) =>
      val qs = (1 to questions.toInt).map(i => importedQuestion(s"question $i")).toList
      settle(november, qs, List("venue-price", "model-alone").take(arms.toInt), repetitions.toInt)
  }

  Given("the cohort is frozen")(() => freeze(november): Unit)

  When("the cohort is forecast")(() => ok(forecastAndWait(november)): Unit)

  Then("{string} runs are recorded") { (n: String) =>
    val runs = recorded(november)
    assertEquals(runs.size, n.toInt, runs.map(_.id).mkString("\n"))
    assertEquals(runs.map(r => (r.question, r.arm, r.repetition)).distinct.size, n.toInt)
  }

  Given("the cohort is frozen with a budget of {string}") { (budget: String) =>
    // One question, so its runs are made one after another and each is settled before the next
    // is reserved: what the scenario observes is the budget, not two questions racing for it.
    settle(
      november,
      List(importedQuestion("first")),
      List("model-alone"),
      repetitions = 3,
      budget = budget
    )
    freeze(november): Unit
  }

  When("the cost of its runs reaches {string}") { (cost: String) =>
    // Each run costs half the budget: no input, and output tokens at the model's price.
    val price = fixture.settings.models("model-a").prices.outputPerMillion
    val out   = (BigDecimal(cost) / 2 / price * 1000000).toLong
    fixture.model.when(s"[#${world.n}]")(_ =>
      Right(
        com.thinkmorestupidless.ankka.agent.ModelResponse(
          forecastbench.support.ScriptedModel.forecast(0.4),
          usage = com.thinkmorestupidless.ankka.agent.TokenUsage(0, out.toInt)
        )
      )
    )
    ok(forecastAndWait(november)): Unit
    assertEquals(BigDecimal(readCohort(november)("spent").num), BigDecimal(cost))
  }

  Then("no further run is made") { () =>
    assertEquals(recorded(november).size, 2)
  }

  Then("the cohort says which runs were not made") { () =>
    val notMade = readCohort(november)("notMade").arr
    assert(notMade.nonEmpty, "the cohort names no run not made")
    notMade.foreach(n => assert(n("reason").str.nonEmpty))
  }

  Given("the cohort is frozen and its forecast window has ended") { () =>
    settle(november, List(importedQuestion("late")), List("model-alone"), windowDays = 1)
    freeze(november): Unit
    fixture.clock.advance(Duration.ofDays(2))
  }

  Given("the cohort {string} is a prospective cohort") { (name: String) =>
    assertEquals(readCohort(world.cohort(name))("standing").str, "prospective")
  }

  When("the experimenter adds a development question to the cohort") { () =>
    val q = importedQuestion("rehearsed", resolved = Some(forecastbench.domain.OutcomeValue.Yes))
    world.last = post(s"/cohorts/$november/questions", s"""{"questions":["$q"]}""")
  }

  Given("a drafted development cohort {string}") { (name: String) =>
    draft(
      name,
      standing = "development",
      pretended = Some(fixture.clock.now.minus(java.time.Duration.ofDays(60)))
    ): Unit
  }

  When("the experimenter adds a prospective question to the cohort {string}") { (name: String) =>
    val q = importedQuestion("open")
    world.held("refusing") = world.cohort(name)
    world.last = post(s"/cohorts/${world.cohort(name)}/questions", s"""{"questions":["$q"]}""")
  }

  Then("the question is refused") { () =>
    assertEquals(world.last._1, 422, world.last._2)
    val cohort = world.held.getOrElse("refusing", november)
    assertEquals(readCohort(cohort)("questions").arr.size, 0)
  }
