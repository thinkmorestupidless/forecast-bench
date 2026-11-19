package forecastbench.steps

import com.thinkmorestupidless.ankka.core.{CommandError, EntityId, ErrorCode}
import forecastbench.application.PackEntity
import forecastbench.domain.{Ending, Ids, Pack}
import forecastbench.support.ScriptedSearch

class PackFeatures extends FeatureSuite("features/evidence/pack.feature") with ModelSteps:

  override protected def pending = Set(
    // T066: the graph and prose arms.
    "every arm that takes evidence is given the same pack",
    // T070: development cohorts.
    "a development cohort's pack holds only documents published before its pretended time",
    "a development cohort's pack holds no document without a time it was published"
  )

  /**
   * Drafted with its question and the evidence arm; frozen, which gathers, by the first step that
   * needs the pack, so a scenario can first say what the search finds.
   */
  Given("a frozen cohort {string} with a prospective question {string}") {
    (name: String, question: String) =>
      settle(draft(name), List(importedQuestion(question)), List("evidence"))
  }

  private def packPath = s"/cohorts/$november/packs/${questionId("rates")}"

  /** Freezes the cohort, if a step has not, and waits for the cohort to note the pack. */
  private def gathered(): ujson.Value =
    frozenWith("evidence")
    eventually("the pack to be noted") {
      readCohort(november)("packs").obj.get(questionId("rates"))
    }

  When("the gatherer has finished")(() => gathered(): Unit)

  Then("the question {string} has one evidence pack in the cohort {string}") {
    (q: String, c: String) =>
      val pack = json(ok(get(s"/cohorts/${world.cohort(c)}/packs/${questionId(q)}")))
      assertEquals(pack("question").str, questionId(q))
      assertEquals(readCohort(november)("packs").obj.keys.toList, List(questionId(q)))
  }

  Given("the question {string} has an evidence pack") { (_: String) =>
    assertEquals(gathered()("state").str, "gathered")
  }

  When("the experimenter reads the documents of the pack") { () => world.last = get(packPath) }

  When("the experimenter reads the pack") { () => world.last = get(packPath) }

  Then(
    "each document of the pack is held with its source, its address, the time it was published and an excerpt"
  ) { () =>
    val docs = json(ok(world.last))("documents").arr
    assert(docs.nonEmpty)
    docs.foreach { d =>
      assert(
        d("source").str.nonEmpty && d("address").str.startsWith("https://") && d(
          "excerpt"
        ).str.nonEmpty
      )
      assert(!d("publishedAt").isNull)
    }
  }

  Then("the pack names the gatherer's version, each search made and the time it was gathered") {
    () =>
      val p = json(ok(world.last))
      assertEquals(p("gathererVersion").num.toInt, 1)
      assertEquals(p("searches").arr.map(_("query").str).head, statement("rates"))
      assertEquals(p("searches").arr.size, 3)
      assert(p("searches").arr.forall(_("results").arr.nonEmpty))
      assert(!p("gatheredAt").isNull)
  }

  When("the gatherer is asked to gather for the cohort {string} again") { (name: String) =>
    world.held("before") = ok(get(packPath))
    assertEquals(post(s"/cohorts/${world.cohort(name)}/freeze", "{}")._1, 409)
    val again = Pack(
      november,
      questionId("rates"),
      1,
      fixture.clock.now,
      Nil,
      Nil,
      forecastbench.domain.Usage.zero,
      BigDecimal(0)
    )
    val refused =
      try
        fixture.kit.componentClient
          .forEventSourcedEntity(EntityId(Ids.pack(november, questionId("rates"))))
          .call(PackEntity.gather)
          .invoke(again)
        None
      catch case e: CommandError => Some(e.code)
    assertEquals(refused, Some(ErrorCode.Conflict))
  }

  Then("the pack is as it was") { () =>
    assertEquals(json(ok(get(packPath))), json(world.held("before")))
  }

  Given("the question {string} has no evidence pack yet") { (_: String) =>
    fixture.search.hold(key)
    frozenWith("evidence")
  }

  When("the cohort is forecast") { () =>
    world.last = post(s"/cohorts/$november/forecast", "{}")
    ok(world.last): Unit
  }

  Then("no run of an arm that takes evidence is recorded for {string}") { (name: String) =>
    Thread.sleep(1500)
    try
      assertEquals(
        recorded(november).filter(r => r.question == questionId(name) && r.arm == "evidence"),
        Nil
      )
    finally
      fixture.search.release(key)
      awaitRound(november, 1)
    // Once the pack is gathered the arm makes its run.
    assertEquals(recorded(november).count(_.arm == "evidence"), 1)
  }

  Given("the gatherer's limit is {string} documents") { (n: String) =>
    assertEquals(fixture.settings.limits.documents, n.toInt)
  }

  When("the search finds {string} documents") { (n: String) =>
    // Each of the three searches finds its own share of them.
    fixture.search.findsBy(key) { query =>
      val which = query.takeWhile(_ != ' ').toLowerCase
      (1 to n.toInt).toList.map(i =>
        ScriptedSearch.result(s"https://many.example/${world.n}/$which/$i")
      )
    }
    gathered(): Unit
  }

  Then("the pack holds {string} documents") { (n: String) =>
    assertEquals(json(ok(get(packPath)))("documents").arr.size, n.toInt)
  }

  When("the search finds no documents for {string}") { (_: String) =>
    fixture.search.findsNothing(key)
    gathered(): Unit
  }

  /** Written as a Given and as a Then: arranges an empty pack when the cohort is not yet frozen. */
  Given("the question {string} has an empty evidence pack") { (_: String) =>
    if readCohort(november)("status").str == "drafted" then fixture.search.findsNothing(key)
    assertEquals(gathered()("state").str, "empty")
    assertEquals(json(ok(get(packPath)))("documents").arr.size, 0)
  }

  Then("the run is recorded as abstained, saying there was no evidence") { () =>
    assertEquals(only.ending, Ending.Abstained("there was no evidence"))
    assertEquals(only.pack, Some(Ids.pack(november, questionId("rates"))))
  }

  When("the search fails for {string}") { (_: String) =>
    fixture.search.fails(key)
    gathered(): Unit
  }

  Then("the question {string} has no evidence pack") { (_: String) =>
    assertEquals(get(packPath)._1, 404)
  }

  Then("the cohort says the pack was not gathered and why") { () =>
    val note = gathered()
    assertEquals(note("state").str, "failed")
    assertEquals(note("reason").str, "the search failed: the scripted search is down")
  }
