package forecastbench.api

import forecastbench.steps.{CohortSetup, FeatureSuite}

/** The report's routes beside the cohort's, on a cohort forecast by the model-alone arm. */
class ReportRoutesSuite
    extends FeatureSuite("src/test/resources/features/reports/routes")
    with CohortSetup:

  Given("a forecast cohort {string}") { (name: String) =>
    val id = draft(name)
    settle(id, List(importedQuestion("rates")), List("venue-price", "model-alone"))
    freeze(id): Unit
    ok(forecastAndWait(id)): Unit
  }

  Then("its report, its page, a comparison and a calibration table are served") { () =>
    val id = world.cohort("november")
    val r  = json(ok(get(s"/cohorts/$id/report")))
    assertEquals(r("cohorts").arr.map(_.str).toList, List(id))
    assertEquals(r("nothingKnown").str, "nothing is known yet")
    val page = get(s"/cohorts/$id/report.md")
    assertEquals(page._1, 200)
    assert(page._2.startsWith(s"# Report: $id"), page._2)
    assertEquals(
      json(ok(get(s"/cohorts/$id/comparison?a=model-alone&b=venue-price")))("a").str,
      "model-alone"
    )
    assertEquals(json(ok(get(s"/cohorts/$id/calibration?arm=model-alone")))("bands").arr.size, 10)
    assertEquals(json(ok(get(s"/reports?cohorts=$id")))("cohorts").arr.size, 1)
    assert(ok(get(s"/reports.md?cohorts=$id")).startsWith(s"# Report: $id"))
    assertEquals(json(ok(get(s"/cohorts/$id")))("id").str, id)
  }
