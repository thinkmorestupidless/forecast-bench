package forecastbench.api

import forecastbench.steps.{CohortSetup, FeatureSuite}
import forecastbench.support.ScriptedVenue

import java.time.Duration

/** The import route's limit, counted after an event's markets are made one. */
class ImportRoutesSuite
    extends FeatureSuite("src/test/resources/features/questions/limit")
    with CohortSetup:

  Given("the venue lists an event of {string} markets and {string} events of one market each") {
    (ladder: String, single: String) =>
      val closes = fixture.clock.now.plus(Duration.ofDays(28))
      // The ladder's ids sort first, so a limit applied before one per event would take only it.
      val listed =
        (1 to ladder.toInt).map(i => (s"s${world.n}-a-$i", s"s${world.n}-ladder")) ++
          (1 to single.toInt).map(i => (s"s${world.n}-b-$i", s"s${world.n}-single-$i"))
      listed.foreach { (id, event) =>
        fixture.kalshi.list(
          ScriptedVenue.question(id, s"Will $id happen?", closes, eventGroup = Some(event))
        )
      }
  }

  When("the experimenter imports {string} questions, one per event") { (n: String) =>
    fixture.kalshi.only = Some(s"s${world.n}-")
    try
      world.last = post(
        "/venues/kalshi/import",
        s"""{"standing":"prospective","onePerEvent":true,"limit":$n}"""
      )
    finally fixture.kalshi.only = None
  }

  Then("{string} questions are held, one from each event") { (n: String) =>
    assertEquals(json(ok(world.last))("held").num.toInt, n.toInt)
    // The questions view, once it holds the three.
    val held = eventually("the questions view to hold them") {
      Some(
        json(ok(get("/questions?venue=kalshi"))).arr
          .map(_("id").str)
          .toList
          .filter(_.startsWith(s"kalshi.s${world.n}-"))
      ).filter(_.size == n.toInt)
    }
    assertEquals(held.count(_.startsWith(s"kalshi.s${world.n}-a-")), 1)
    assertEquals(held.count(_.startsWith(s"kalshi.s${world.n}-b-")), 2)
  }
