package forecastbench.domain

class AnswerSuite extends munit.FunSuite:

  private def ending(reply: String) = Answer.read(reply)._1

  test("a probability from nought to one is a forecast, with the reasons it names"):
    assertEquals(
      Answer.read(
        """{"probability": 0.62, "abstain": null, "reasons": ["c3","c5"], "rationale": "x"}"""
      ),
      (Ending.Forecast(0.62), List("c3", "c5"))
    )
    assertEquals(ending("""{"probability": 0, "abstain": null}"""), Ending.Forecast(0.0))
    assertEquals(ending("""{"probability": 1}"""), Ending.Forecast(1.0))

  test("an answer in a fenced block is read"):
    assertEquals(ending("```json\n{\"probability\": 0.3}\n```"), Ending.Forecast(0.3))

  test("an abstention is a reason"):
    assertEquals(
      ending("""{"probability": null, "abstain": "The question is ambiguous.", "reasons": []}"""),
      Ending.Abstained("The question is ambiguous.")
    )

  test("1.4, -0.2 and likely are not probabilities"):
    List(
      """{"probability": 1.4, "abstain": null}""",
      """{"probability": -0.2, "abstain": null}""",
      """{"probability": "likely", "abstain": null}""",
      "likely",
      "1.4",
      "-0.2"
    ).foreach(r => assertEquals(ending(r), Ending.Failed(Answer.NotAProbability), r))

  test("both or neither of probability and abstain is not a probability"):
    assertEquals(
      ending("""{"probability": 0.5, "abstain": "no"}"""),
      Ending.Failed(Answer.NotAProbability)
    )
    assertEquals(
      ending("""{"probability": null, "abstain": null}"""),
      Ending.Failed(Answer.NotAProbability)
    )
    assertEquals(ending("""{}"""), Ending.Failed(Answer.NotAProbability))
