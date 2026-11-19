package forecastbench.steps

import com.thinkmorestupidless.ankka.core.{CommandError, EntityId, ErrorCode}
import forecastbench.application.PackEntity
import forecastbench.domain.{Ending, Ids, OutcomeValue, Pack}
import forecastbench.support.ScriptedSearch

class PackFeatures extends FeatureSuite("features/evidence/pack.feature") with PackSteps:

  /**
   * Drafted with its question and the evidence arm; frozen, which gathers, by the first step that
   * needs the pack, so a scenario can first say what the search finds.
   */
  Given("a frozen cohort {string} with a prospective question {string}") {
    (name: String, question: String) =>
      settle(draft(name), List(importedQuestion(question)), List("evidence"))
  }

  When("the gatherer has finished")(() => gathered(): Unit)

  Then("the question {string} has one evidence pack in the cohort {string}") {
    (q: String, c: String) =>
      val pack = json(ok(get(s"/cohorts/${world.cohort(c)}/packs/${questionId(q)}")))
      assertEquals(pack("question").str, questionId(q))
      assertEquals(readCohort(november)("packs").obj.keys.toList, List(questionId(q)))
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
    frozenWithArms(Nil)
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

  Then("each run of those arms for {string} names the same evidence pack") { (name: String) =>
    awaitRound(november, 1)
    val runs = recorded(november).filter(_.question == questionId(name))
    assertEquals(runs.map(_.arm).toSet, Set("evidence", "graph", "prose"))
    assertEquals(
      runs.map(_.pack).toSet,
      Set[Option[String]](Some(Ids.pack(november, questionId(name))))
    )
  }

  private def day(d: String): java.time.Instant = java.time.Instant.parse(s"${d}T00:00:00Z")

  /**
   * Drafted with a development question and the evidence arm; frozen once the search is scripted.
   */
  Given("a frozen development cohort {string} with the pretended time {string}") {
    (name: String, pretended: String) =>
      val id = draft(name, standing = "development", pretended = Some(day(pretended)))
      settle(
        id,
        List(importedQuestion("rehearsed", resolved = Some(OutcomeValue.Yes))),
        List("evidence")
      )
      world.held("dev") = id
      world.held("pretended") = pretended
  }

  /**
   * The pack of the development cohort, gathered with what the search finds and one earlier
   * document.
   */
  private def devPack(late: forecastbench.gather.SearchResult): ujson.Value =
    val early = ScriptedSearch.result(
      s"https://early.example/${world.n}",
      Some(day(world.held("pretended")).minus(java.time.Duration.ofDays(10)))
    )
    fixture.search.finds(key, List(late, early))
    world.held("late") = late.address
    val dev = world.held("dev")
    freeze(dev): Unit
    eventually("the pack to be noted")(readCohort(dev)("packs").obj.get(questionId("rehearsed")))
    // Every search was asked for what was published before the pretended time.
    assert(fixture.search.asked(key).nonEmpty)
    assert(fixture.search.asked(key).forall(_._2.contains(day(world.held("pretended")))))
    json(ok(get(s"/cohorts/$dev/packs/${questionId("rehearsed")}")))

  When("the search finds a document published on {string}") { (d: String) =>
    world.held("pack") =
      ujson.write(devPack(ScriptedSearch.result(s"https://late.example/${world.n}", Some(day(d)))))
  }

  When("the search finds a document with no time it was published") { () =>
    world.held("pack") =
      ujson.write(devPack(ScriptedSearch.result(s"https://undated.example/${world.n}", None)))
  }

  Then("that document is not in the pack") { () =>
    val addresses = json(world.held("pack"))("documents").arr.map(_("address").str).toList
    assertEquals(addresses, List(s"https://early.example/${world.n}"))
    assert(!addresses.contains(world.held("late")))
  }
