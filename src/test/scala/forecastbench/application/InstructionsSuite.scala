package forecastbench.application

import forecastbench.domain.Ids

class InstructionsSuite extends munit.FunSuite:

  test("the latest version of an arm's instructions, with the digest of its text"):
    val i = Instructions.latest("model-alone").get
    assert(i.version >= 1)
    assertEquals(i.digest, Ids.sha256(i.text))
    assert(i.text.contains(""""probability""""), "the instructions end with the answer's form")

  test("a version named is that version; a version that does not exist is refused"):
    assertEquals(Instructions.fixed("model-alone", Some(1)).map(_.version), Right(1))
    assert(Instructions.fixed("model-alone", Some(999)).isLeft)
    assert(Instructions.fixed("no-such-arm", None).isLeft)
