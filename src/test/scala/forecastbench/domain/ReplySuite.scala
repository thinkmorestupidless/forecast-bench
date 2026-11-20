package forecastbench.domain

class ReplySuite extends munit.FunSuite:

  test("the last top-level object, with nested objects and braces in strings"):
    assertEquals(
      Reply.lastObject("""first {"a": 1} then {"b": {"c": "}{"}} done"""),
      Some("""{"b": {"c": "}{"}}""")
    )

  test("no object, or one never closed, is none"):
    assertEquals(Reply.lastObject("no json here"), None)
    assertEquals(Reply.lastObject("""{"a": 1"""), None)

  test("a reply that is itself the object is read once"):
    assertEquals(
      Reply.candidates("""```json
{"a": 1}
```"""),
      List("""{"a": 1}""")
    )
