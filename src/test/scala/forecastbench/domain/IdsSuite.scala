package forecastbench.domain

class IdsSuite extends munit.FunSuite:

  test("a cohort id is lower case letters, digits and hyphens, starting with a letter"):
    assertEquals(Ids.cohort("november"), Right("november"))
    assertEquals(Ids.cohort("nov-2026-a"), Right("nov-2026-a"))
    assert(Ids.cohort("2026").isLeft)
    assert(Ids.cohort("November").isLeft)
    assert(Ids.cohort("").isLeft)
    assert(Ids.cohort("a" * 25).isLeft)

  test("a question id is the venue and the venue's id made safe"):
    assertEquals(Ids.question("kalshi", "KXFED-26DEC-T2.75"), "kalshi.kxfed-26dec-t2.75")
    assertEquals(Ids.question("polymarket", "0xA4_67"), "polymarket.0xa4-67")

  test("a qkey is twelve hex characters and the same for the same question"):
    val key = Ids.qkey("kalshi.kxfed-26dec-t2.75")
    assert(key.matches("[0-9a-f]{12}"), key)
    assertEquals(Ids.qkey("kalshi.kxfed-26dec-t2.75"), key)
    assertNotEquals(Ids.qkey("kalshi.kxfed-26dec-t3.00"), key)

  test("a run id names its cohort, question, arm, repetition and attempt within 64 characters"):
    val id = Ids.run("a" * 24, "kalshi.kxfed-26dec-t2.75", "venue-price", 12, 3)
    assert(id.endsWith(".venue-price.12.3"), id)
    assert(id.length <= 64, id)

  test("sha256 is the known digest of the empty text"):
    assertEquals(
      Ids.sha256(""),
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    )
