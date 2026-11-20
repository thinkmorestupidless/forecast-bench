package forecastbench.steps

import com.thinkmorestupidless.ankka.agent.{ModelResponse, TokenUsage}
import forecastbench.domain.*
import forecastbench.reasoning.Reasoning
import forecastbench.support.ScriptedModel

class AppraisalFeatures extends FeatureSuite("features/arms/appraisal.feature") with AppraisalSteps:

  private def appraisalPath = s"/cohorts/$november/appraisals/${questionId("rates")}"

  When("the cohort is forecast") { () =>
    frozenWithArms(Nil)
    world.last = forecastAndWait(november)
    ok(world.last): Unit
  }

  Then("one appraisal is held for {string} in the cohort {string}") { (q: String, c: String) =>
    val read = json(ok(get(s"/cohorts/${world.cohort(c)}/appraisals/${questionId(q)}")))
    assert(!read("made").isNull, read.toString)
    // Read once: one reading of each document, though two arms read the appraisal.
    assertEquals(fixture.model.tasks(ScriptedModel.Task.Reading, key).size, documents)
    assertEquals(fixture.model.tasks(ScriptedModel.Task.OpenPoints, key).size, 1)
  }

  Then(
    "the reasoning service holds the question {string} with a yes hypothesis and a no hypothesis"
  ) { (_: String) =>
    val held = fixture.reasoning.get("question", rq).getOrElse(fail(s"no question $rq"))
    assertEquals(held("hypotheses").arr.map(_("id").str).toList, List("yes", "no"))
  }

  When("the experimenter reads the claims of the appraisal")(() => world.last = get(appraisalPath))
  When("the experimenter reads the open points of the appraisal")(() =>
    world.last = get(appraisalPath)
  )
  When("the experimenter reads the appraisal")(() => world.last = get(appraisalPath))

  private def read: ujson.Value = json(ok(world.last))("made")

  Then(
    "each claim is held in the reasoning service with its statement, the document it derives from and one stance on the yes hypothesis"
  ) { () =>
    val pack   = json(ok(get(s"/cohorts/$november/packs/${questionId("rates")}")))("documents").arr
    val claims = read("claims").arr
    assertEquals(claims.size, documents)
    claims.foreach { c =>
      val held = fixture.reasoning.get("claim", c("id").str).getOrElse(fail(s"no claim ${c("id")}"))
      assertEquals(held("statement").str, c("statement").str)
      assertEquals(held("derivesFrom").arr.map(_.str).toList, List(c("evidence").str))
      val evidence = fixture.reasoning.get("evidence", c("evidence").str).get
      val doc      = pack.find(_("n").num.toInt == c("document").num.toInt).get
      assertEquals(evidence("locator").str, doc("address").str)
      assertEquals(
        held("stances").arr.map(s => (s("hypothesis").str, s("stance").str)).toList,
        List((s"$rq/yes", c("stance").str))
      )
    }
  }

  Then(
    "the appraisal holds its open points, each a statement of something the evidence does not settle"
  ) { () =>
    val points = read("openPoints").arr.map(_.str).toList
    assertEquals(
      points,
      List("Whether what the documents report will hold until the question closes")
    )
  }

  Given("the appraisal states a claim that derives from no document of the pack") { () =>
    fixture.model.whenReading(key)(r =>
      if docOf(r) == 2 then
        Right(
          ModelResponse(
            ScriptedModel.claims(List(("Rates will fall", "supports", "words found nowhere")))
          )
        )
      else Right(ModelResponse(ScriptedModel.default(ScriptedModel.Task.Reading, r)))
    )
  }

  When("the appraisal of {string} is checked")((_: String) => appraised(): Unit)

  Then("the appraisal is failed, naming the claim") { () =>
    val failed = appraisalState.failed.getOrElse(fail("the appraisal was made"))
    assertEquals(failed.reason, Appraisal.NotInDocument)
    assertEquals(failed.naming, Some("claim 1 of document [2]: Rates will fall"))
    assertEquals(fixture.reasoning.all("claim").filter(_("id").str.startsWith(rq)), Nil)
  }

  Given("the appraisal of {string} is failed") { (_: String) =>
    fixture.model.whenReading(key)(_ =>
      Right(ModelResponse(ScriptedModel.claims(List(("Rates", "supports", "nowhere")))))
    )
  }

  Then("each run is recorded as abstained, saying the appraisal failed") { () =>
    val runs = recorded(november)
    assertEquals(runs.map(_.arm).sorted, List("graph", "prose"))
    runs.foreach(r => assertEquals(r.ending, Ending.Abstained("the appraisal failed")))
  }

  Given(
    "the appraisal of {string} holds {string} claims that support, {string} claims that contradict and {string} open point"
  ) { (_: String, yes: String, no: String, open: String) =>
    assertEquals((yes, no, open), ("3", "2", "1"))
    reads {
      case 1 => List("supports", "supports")
      case 2 => List("supports", "contradicts")
      case 3 => List("contradicts")
      case _ => Nil
    }
  }

  /** The graph text's claim lines under a heading, up to the next. */
  private def under(lines: List[String], heading: String, next: String) =
    lines.dropWhile(_ != heading).drop(1).takeWhile(_ != next).filter(_.startsWith("- "))

  Then(
    "the model is given the case for the yes hypothesis with {string} claims, the case against it with {string} claims and {string} open point"
  ) { (yes: String, no: String, open: String) =>
    val lines = userText(requestsOf("graph").last).split("\n").toList
    assertEquals(under(lines, Shape.CaseFor, Shape.CaseAgainst).size, yes.toInt)
    assertEquals(under(lines, Shape.CaseAgainst, Shape.OpenPoints).size, no.toInt)
    assertEquals(
      lines.dropWhile(_ != Shape.OpenPoints).drop(1).count(_.startsWith("- ")),
      open.toInt
    )
  }

  Then("each claim is given with the document it derives from") { () =>
    val claims = made.claims.map(c => Reasoning.short(c.id) -> c.document).toMap
    val pack   = json(ok(get(s"/cohorts/$november/packs/${questionId("rates")}")))("documents").arr
    val lines  = userText(requestsOf("graph").last).split("\n").toList
    val shown = lines.zip(lines.drop(1)).collect {
      case (c, f) if c.startsWith("- [") && f.startsWith("  from [") =>
        (c.substring(3, c.indexOf("]")), f.substring(8, f.indexOf("]")).toInt, f)
    }
    assertEquals(shown.map(g => g._1 -> g._2).toMap, claims)
    shown.foreach { (_, n, line) =>
      val doc = pack.find(_("n").num.toInt == n).get
      assert(line.endsWith(s"""\"${doc("excerpt").str}\""""), line)
    }
  }

  Then(
    "the model is given one passage that says each of the {string} claims with its stance and its document, and the {string} open point"
  ) { (claims: String, open: String) =>
    val text    = userText(requestsOf("prose").last)
    val content = Shape.contentOf(text).getOrElse(fail(s"not a prose passage: $text"))
    assertEquals(content.claims.size, claims.toInt)
    assertEquals(
      content.claims.map(c => (c._1, c._2, c._3)),
      made.claims.map(c => (Reasoning.short(c.id), c.stance, c.document)).toSet
    )
    assertEquals(content.openPoints.size, open.toInt)
    // The block's four lines, then the one paragraph: no heading, no list, no blank line.
    val paragraph = text.split("\n", -1).toList.drop(4)
    assertEquals(paragraph.size, 1, text)
    assert(!paragraph.head.startsWith("- ") && !paragraph.head.startsWith("#"), text)
  }

  Then("the passage follows the order of the documents in the pack") { () =>
    val docs = """According to \[(\d+)\]""".r
      .findAllMatchIn(userText(requestsOf("prose").last))
      .map(_.group(1).toInt)
      .toList
    assertEquals(docs, docs.sorted)
    assertEquals(docs.distinct, List(1, 2, 3))
  }

  Then("every claim, stance, document and open point given to one is given to the other") { () =>
    val g = Shape.contentOf(userText(requestsOf("graph").last))
    val p = Shape.contentOf(userText(requestsOf("prose").last))
    assert(g.isDefined && g.get.claims.nonEmpty)
    assertEquals(p, g)
  }

  Then("neither is given anything the other is not, apart from the shape") { () =>
    val g = userText(requestsOf("graph").last)
    val p = userText(requestsOf("prose").last)
    assertEquals(g.split("\n").take(4).toList, p.split("\n").take(4).toList)
    // Where each claim's document is said to come from and when: the same in both.
    val fromGraph = """  from \[(\d+)\] (.+?), (\d{4}-\d{2}-\d{2} \d{2}:\d{2} UTC|undated): """.r
      .findAllMatchIn(g)
      .map(m => (m.group(1), m.group(2), m.group(3)))
      .toSet
    val fromProse =
      """According to \[(\d+)\] (.+?) \((\d{4}-\d{2}-\d{2} \d{2}:\d{2} UTC|undated)\), """.r
        .findAllMatchIn(p)
        .map(m => (m.group(1), m.group(2), m.group(3)))
        .toSet
    assertEquals(fromProse, fromGraph)
    assertEquals(Shape.contentOf(g), Shape.contentOf(p))
  }

  When("the arm {string} forecasts {string} and the arm {string} forecasts {string}") {
    (a: String, q: String, b: String, _: String) =>
      questionId(q): Unit
      frozenWithArms(List(a, b))
      world.last = forecastAndWait(november)
      ok(world.last): Unit
  }

  Then(
    "each model is given the statement, the resolution criteria, the time the question closes and the time of the run"
  ) { () =>
    List("graph", "prose").foreach { a =>
      val text = userText(requestsOf(a).last)
      assert(text.startsWith(s"Question: ${statement("rates")}\n"), text)
      assert(
        text.contains(s"\nResolution criteria: Resolves yes if: ${statement("rates")}\n"),
        text
      )
      assert(text.contains("\nCloses: "), text)
      assert(text.contains("\nNow: "), text)
    }
  }

  Given("a document of the pack that no claim derives from") { () =>
    reads(n => if n == 4 then Nil else List("supports"))
  }

  Then("no model is given a document's excerpt except through a claim that derives from it") { () =>
    val pack = json(ok(get(s"/cohorts/$november/packs/${questionId("rates")}")))("documents").arr
    val left = pack.find(_("n").num.toInt == 4).get
    List("graph", "prose").foreach { a =>
      val text = userText(requestsOf(a).last)
      assert(!text.contains(left("excerpt").str), text)
      assert(!text.contains(left("address").str), text)
      assert(!text.contains("[4]"), text)
    }
  }

  Given(
    "the arm {string} forecasts {string} with the forecast {string} and names {string} claims as its reasons"
  ) { (words: String, _: String, p: String, n: String) =>
    assertEquals(arm(words), "graph")
    val reasons = (1 to n.toInt).toList.map(i => s"c$i")
    fixture.model.replies(key, ScriptedModel.forecast(p.toDouble, reasons))
    world.held("forecast") = p
  }

  When("the run is recorded") { () =>
    ok(forecastBy("graph", "rates")): Unit
    assertEquals(only.ending, Ending.Forecast(world.held("forecast").toDouble))
  }

  Then(
    "the reasoning service holds a belief in the yes hypothesis of {string} resting on those {string} claims"
  ) { (p: String, n: String) =>
    val belief = eventually("the belief to be held")(
      fixture.reasoning.get("revision", Reasoning.beliefId(rq, 1))
    )
    assertEquals(belief("probability").num, p.toDouble)
    assertEquals(belief("hypothesis").str, s"$rq/yes")
    assertEquals(belief("holder").str, Reasoning.graphHolder(november, 1))
    assertEquals(
      belief("restsOn").arr.map(_("claim").str).toList,
      (1 to n.toInt).toList.map(i => Reasoning.claimId(rq, i))
    )
  }

  Given("the reasoning service cannot be reached") { () =>
    names(List("graph"))
    made: Unit
    fixture.reasoning.unreachable(rq)
  }

  Then("the run is recorded as failed, saying the reasoning service could not be reached") { () =>
    try assertEquals(only.ending, Ending.Failed(Reasoning.Unreachable))
    finally fixture.reasoning.reachable(rq)
  }

  Then("the appraisal names the version of its instructions and the cohort's model") { () =>
    assertEquals(read("readerVersion").num.toInt, 1)
    assertEquals(read("openPointsVersion").num.toInt, 1)
    assertEquals(read("model").str, "model-a")
  }

  Given("the appraisal of {string} cost {string}") { (_: String, cost: String) =>
    // Four readings and the open points, each 400 output tokens at 20 a million: 0.008 each.
    assertEquals(cost, "0.04")
    val usage = TokenUsage(0, 400)
    fixture.model.whenReading(key)(r =>
      Right(ModelResponse(ScriptedModel.default(ScriptedModel.Task.Reading, r), usage = usage))
    )
    fixture.model.whenOpenPoints(key)(r =>
      Right(ModelResponse(ScriptedModel.default(ScriptedModel.Task.OpenPoints, r), usage = usage))
    )
  }

  Then("the cost of each run includes {string} for the appraisal") { (cost: String) =>
    assertEquals(made.cost, BigDecimal(cost))
    val runs = recorded(november)
    assertEquals(runs.size, 2)
    runs.foreach { r =>
      assertEquals(r.appraisalShare, BigDecimal(cost))
      val own = forecastbench.application.Models
        .all(fixture.settings, fixture.model)
        .cost("model-a", r.usage.get)
      assertEquals(r.cost, own + BigDecimal(cost))
    }
  }
