package forecastbench.steps

import forecastbench.application.Instructions
import forecastbench.domain.{Arm, Ending, Ids}
import forecastbench.support.ScriptedModel
import forecastbench.venues.VenueProbability

class ArmsFeatures extends FeatureSuite("features/arms/arms.feature") with PackSteps:

  override protected def pending = Set(
    // T066: the graph and prose arms.
    "no model arm is given the venue's probability",
    "every model arm of a cohort uses the cohort's model"
  )

  Then("the model is given what the model-alone arm is given and every document of the pack") {
    () =>
      val request = fixture.model.requests(key).last
      val user    = lastUserText
      val pack    = json(ok(get(packPath)))
      val docs    = pack("documents").arr.toList
      assert(docs.nonEmpty)
      // The model-alone arm's four lines, then the documents, in the pack's order.
      val asked = user.linesIterator.take(4).toList
      assert(asked.head.startsWith(s"Question: ${statement("rates")}"), user)
      assert(asked(3).startsWith("Now: "), user)
      val after = user.linesIterator.drop(4).toList
      assertEquals(after.head, "Documents:")
      val places = docs.map(d => user.indexOf(d("address").str))
      assert(places.forall(_ > 0) && places == places.sorted, user)
      docs.foreach(d => assert(user.contains(d("excerpt").str), user))
      assertEquals(request.systemMessage, Instructions.at("evidence", 1).map(_.text))
  }

  Then("the run names the version of the arm's instructions") { () =>
    assertEquals(only.instructions, Instructions.at("evidence", 1).map(_.ref))
    val read = json(ok(get(s"/runs/${only.id}")))("run")
    assertEquals(read("instructions")("version").num.toInt, 1)
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

  Then("a run is recorded with the forecast {string}") { (p: String) =>
    assertEquals(only.ending, Ending.Forecast(p.toDouble))
  }

  Given("the venue's probability for {string} is {string} when observed in the forecast window") {
    (name: String, p: String) =>
      val price = p.toDouble
      fixture.kalshi.update(world.venueId(name))(
        _.copy(probability =
          VenueProbability.read(
            Some(price - 0.01),
            Some(price + 0.01),
            Some(price),
            fixture.clock.now
          )
        )
      )
  }

  Then("the run names no model")(() => assertEquals(only.model, None))

  Given("the venue gives no probability for {string} in the forecast window") { (name: String) =>
    fixture.kalshi.update(world.venueId(name))(_.copy(probability = None))
  }

  Then("the run is recorded as abstained, saying the venue gave no probability") { () =>
    assertEquals(only.ending, Ending.Abstained("the venue gave no probability"))
  }

  Then(
    "the model is given the statement, the resolution criteria, the time the question closes and the time of the run"
  ) { () =>
    val user = lastUserText
    assert(user.startsWith(s"Question: ${statement("rates")}\n"), user)
    assert(user.contains(s"\nResolution criteria: Resolves yes if: ${statement("rates")}\n"), user)
    assert(user.contains("\nCloses: "), user)
    assert(user.contains(s"\nNow: ${Arm.when(only.recordedAt)}"), user)
  }

  Then("the model is given nothing else") { () =>
    val request = fixture.model.requests(key).last
    assertEquals(lastUserText.linesIterator.size, 4, lastUserText)
    assertEquals(request.systemMessage, Instructions.at("model-alone", 1).map(_.text))
    assert(!lastUserText.contains("kalshi") && !lastUserText.contains(world.venueId("rates")))
  }

  Given("the model answers that it cannot give a probability") { () =>
    fixture.model.replies(
      key,
      """{"probability": null, "abstain": "The criteria name no date.", "reasons": []}"""
    )
  }

  Then("the run is recorded as abstained with the reason the model gave") { () =>
    assertEquals(only.ending, Ending.Abstained("The criteria name no date."))
  }

  Then("the run is recorded as failed, saying the answer was not a probability") { () =>
    assertEquals(only.ending, Ending.Failed("the answer was not a probability"))
  }

  Then("the run is recorded as failed, saying the model could not be reached") { () =>
    assertEquals(only.ending, Ending.Failed("the model could not be reached"))
  }

  Given("a failed run of the arm {string} for {string}") { (words: String, name: String) =>
    fixture.model.unreachable(key)
    forecastBy(words, name)
  }

  When(
    "the experimenter asks for the failed runs of the cohort to be made again within the forecast window"
  ) { () =>
    fixture.model.replies(key, ScriptedModel.forecast(0.55))
    retryAndWait()
  }

  Then("a second run is recorded for the arm {string} and {string}") {
    (words: String, name: String) =>
      val runs =
        recorded(november).filter(r => r.arm == arm(words) && r.question == questionId(name))
      assertEquals(runs.map(_.attempt).sorted, List(1, 2))
      assertEquals(runs.find(_.attempt == 2).map(_.ending), Some(Ending.Forecast(0.55)))
  }

  Then("the failed run is still held") { () =>
    val first = Ids.run(november, questionId("rates"), "model-alone", 1, 1)
    assertEquals(json(ok(get(s"/runs/$first")))("run")("ending")("type").str, "Failed")
  }

  Given("a run of the arm {string} for {string} with a forecast") { (words: String, name: String) =>
    forecastBy(words, name)
  }

  When("the experimenter asks for the failed runs of the cohort to be made again") { () =>
    retryAndWait()
  }

  Then("no further run is recorded for the arm {string} and {string}") {
    (words: String, name: String) =>
      assertEquals(
        recorded(november).count(r => r.arm == arm(words) && r.question == questionId(name)),
        1
      )
  }
