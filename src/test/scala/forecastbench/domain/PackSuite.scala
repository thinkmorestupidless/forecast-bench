package forecastbench.domain

import java.time.Instant

class PackSuite extends munit.FunSuite:

  private val t = Instant.parse("2025-05-01T00:00:00Z")

  private def f(
      address: String,
      published: Option[Instant] = Some(t.minusSeconds(86400)),
      text: String = "text"
  ) =
    Found(address, "title", "source", published, text)

  test("results are interleaved by rank and an address is kept once"):
    val a    = List(f("https://a/1"), f("https://a/2"), f("https://shared"))
    val b    = List(f("https://shared"), f("https://b/2"))
    val docs = Pack.merge(List(a, b), 12, 2000, None)
    assertEquals(
      docs.map(_.address),
      List("https://a/1", "https://shared", "https://a/2", "https://b/2")
    )
    assertEquals(docs.map(_.n), List(1, 2, 3, 4))

  test("the venues' own pages are left out"):
    val docs = Pack.merge(
      List(
        List(
          f("https://kalshi.com/markets/x"),
          f("https://polymarket.com/event/y"),
          f("https://news/z")
        )
      ),
      12,
      2000,
      None
    )
    assertEquals(docs.map(_.address), List("https://news/z"))

  test("cut to the limit, each excerpt to its limit"):
    val many = (1 to 40).toList.map(i => f(s"https://n/$i", text = "x" * 3000))
    val docs = Pack.merge(List(many), 12, 2000, None)
    assertEquals(docs.size, 12)
    assert(docs.forall(_.excerpt.length == 2000))

  test("for a pretended time, only documents published before it"):
    val docs = Pack.merge(
      List(
        List(
          f("https://before"),
          f("https://after", Some(Instant.parse("2025-05-20T00:00:00Z"))),
          f("https://undated", None)
        )
      ),
      12,
      2000,
      Some(t)
    )
    assertEquals(docs.map(_.address), List("https://before"))

  test("from version 2, a document that names a venue in its title or excerpt is left out"):
    val quoted = List(
      f("https://news/odds", text = "Traders on KALSHI think there's a 59% chance."),
      f("https://news/plain", text = "The company delayed its listing."),
      Found(
        "https://news/title",
        "Polymarket bettors shift",
        "news",
        Some(t.minusSeconds(86400)),
        "x"
      ),
      f("https://news/late", text = ("y" * 2000) + " polymarket.com")
    )
    assertEquals(
      Pack.merge(List(quoted), 12, 2000, None).map(_.address),
      List("https://news/plain", "https://news/late")
    )
    // A cohort frozen under version 1 gathers as version 1 did.
    assertEquals(Pack.merge(List(quoted), 12, 2000, None, version = 1).size, 4)
