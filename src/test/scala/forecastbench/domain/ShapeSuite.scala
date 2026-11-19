package forecastbench.domain

import java.time.Instant
import scala.util.Random

class ShapeSuite extends munit.FunSuite:

  private val now = Instant.parse("2027-01-04T09:00:00Z")
  private val q =
    QuestionGiven("Will rates fall?", "Resolves yes if:\nthey fall.", now.plusSeconds(86400), now)

  /**
   * Text with what breaks a careless parser: quotes, brackets, semicolons, backslashes and line
   * breaks.
   */
  private val awkward = "abc xyz \"[]();:,.\\\n\r— é".toVector

  private def text(r: Random, min: Int, max: Int): String =
    Vector.fill(min + r.nextInt(max - min + 1))(awkward(r.nextInt(awkward.size))).mkString

  private def content(r: Random): AppraisalContent =
    val claims = (1 to r.nextInt(7)).toList.map { i =>
      val doc = 1 + r.nextInt(12)
      ClaimGiven(
        s"c$i",
        text(r, 1, 60),
        if r.nextBoolean() then Stance.Supports else Stance.Contradicts,
        doc,
        s"source$doc.example",
        Option.when(r.nextBoolean())(now.minusSeconds(r.nextInt(1000000).toLong)),
        text(r, 0, 80)
      )
    }
    AppraisalContent(claims, List.fill(r.nextInt(4))(text(r, 1, 40)))

  private val contents = (1 to 500).map(seed => content(Random(seed)))

  test("contentOf each rendering is the content, for generated contents"):
    contents.foreach { c =>
      assertEquals(Shape.contentOf(Shape.graph(q, c)), Some(Shape.of(c)), Shape.graph(q, c))
      assertEquals(Shape.contentOf(Shape.prose(q, c)), Some(Shape.of(c)), Shape.prose(q, c))
    }

  test("the prose is the block and one paragraph, with no heading, list or blank line"):
    contents.foreach { c =>
      val p = Shape.prose(q, c)
      assert(p.startsWith(Arm.block(q) + "\n"))
      val paragraph = p.stripPrefix(Arm.block(q) + "\n")
      assert(!paragraph.contains('\n') && !paragraph.contains('\r'), paragraph)
      assert(!paragraph.startsWith("#") && !paragraph.startsWith("- "), paragraph)
    }

  test("the prose follows the order of the documents in the pack"):
    contents.foreach { c =>
      val docs = """According to \[(\d+)\]""".r
        .findAllMatchIn(Shape.prose(q, c))
        .map(_.group(1).toInt)
        .toList
      assertEquals(docs, docs.sorted)
    }

  test("the graph groups the claims by stance"):
    contents.foreach { c =>
      val g       = Shape.graph(q, c).split("\n").toList
      val forAt   = g.indexOf(Shape.CaseFor)
      val against = g.indexOf(Shape.CaseAgainst)
      val open    = g.indexOf(Shape.OpenPoints)
      def ids(from: Int, to: Int) =
        g.slice(from + 1, to).filter(_.startsWith("- [")).map(l => l.substring(3, l.indexOf("] ")))
      assertEquals(ids(forAt, against), c.claims.filter(_.stance == Stance.Supports).map(_.id))
      assertEquals(ids(against, open), c.claims.filter(_.stance == Stance.Contradicts).map(_.id))
    }

  test("a claim is written as the contract shows it"):
    val k = ClaimGiven(
      "c1",
      "Rates fell",
      Stance.Supports,
      2,
      "news.example",
      Some(now),
      "They \"fell\"."
    )
    val c = AppraisalContent(List(k), List("When"))
    assert(
      Shape
        .graph(q, c)
        .endsWith(
          "Case for yes:\n- [c1] Rates fell\n  from [2] news.example, 2027-01-04 09:00 UTC: \"They \\\"fell\\\".\"\nCase against yes:\nOpen points:\n- When"
        )
    )
    assert(
      Shape
        .prose(q, c)
        .endsWith(
          "According to [2] news.example (2027-01-04 09:00 UTC), Rates fell; this supports yes [c1], on the words \"They \\\"fell\\\".\". What the evidence does not settle: \"When\"."
        )
    )

  test("a text that is neither shape has no content"):
    assertEquals(Shape.contentOf(Arm.block(q) + "\nCase for yes:\nnonsense"), None)
