package forecastbench.gather

import java.time.Instant

class ExaSuite extends munit.FunSuite:

  private def reply(name: String) =
    scala.io.Source.fromResource(s"exa/$name").mkString

  test("a reply's results are read in order, the source from the address"):
    val results = Exa.decode(reply("search.json")).toOption.get
    assertEquals(
      results.map(_.address),
      List("https://www.news.example/economy/rates-hold", "https://blog.example/markets")
    )
    assertEquals(results.map(_.source), List("news.example", "blog.example"))
    assertEquals(results.head.publishedAt, Some(Instant.parse("2026-11-12T00:00:00Z")))
    assertEquals(results.head.title, "Central bank holds rates steady")
    assertEquals(results.head.text, "The central bank held its rate at its November meeting.")

  test("a result with no published time is undated"):
    assertEquals(Exa.decode(reply("search.json")).toOption.get(1).publishedAt, None)

  test("a reply with no results finds nothing"):
    assertEquals(Exa.decode(reply("empty.json")), Right(Nil))

  test("a reply that is not Exa's says so"):
    assert(Exa.decode("<html>").left.exists(_.startsWith("Exa's reply could not be read")))

  test("with no key every search fails, saying so, and nothing is sent"):
    assertEquals(
      Exa(None, 2000).search("q", None, 10),
      Left(SearchFailed("exa", "EXA_API_KEY is not set"))
    )

  test("a search with no pretended time sends no end date, and one with it sends it"):
    import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
    val none = writeToString(Exa.Request("q", 10, "auto", None, Exa.Contents(Exa.Text(2000))))
    assertEquals(
      none,
      """{"query":"q","numResults":10,"type":"auto","contents":{"text":{"maxCharacters":2000}}}"""
    )
    val some = writeToString(
      Exa.Request("q", 10, "auto", Some("2025-05-01T00:00:00Z"), Exa.Contents(Exa.Text(2000)))
    )
    assert(some.contains(""""endPublishedDate":"2025-05-01T00:00:00Z""""), some)
