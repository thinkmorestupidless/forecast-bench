package forecastbench.steps

import java.time.Duration

class CohortFeatures extends FeatureSuite("features/cohorts/cohort.feature") with CohortSetup:

  override protected def pending = Set(
    // T030: the forecast.
    "no arm forecasts a cohort that is not frozen",
    "each arm forecasts each question once for each repetition",
    "a cohort stops making runs when its budget is spent",
    "a run not made within the forecast window is never made",
    // T069: development cohorts.
    "a prospective cohort refuses a development question",
    "a development cohort refuses a prospective question"
  )

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
    assertEquals(readCohort(november), json(world.held("frozen")))
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
