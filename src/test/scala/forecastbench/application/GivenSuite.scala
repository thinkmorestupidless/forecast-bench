package forecastbench.application

import com.thinkmorestupidless.ankka.sdk.BucketExposure
import com.thinkmorestupidless.ankka.runtime.storage.MemoryObjectStorage

import java.nio.charset.StandardCharsets.UTF_8

class GivenSuite extends munit.FunSuite:

  private def store = MemoryObjectStorage(Map(GivenBucket.Name -> BucketExposure.Private))

  test("a text is kept under its digest and read back intact"):
    val s   = store
    val ref = GivenBucket.keep(s, "Question: will it?\nNow: 2027-01-04")
    assertEquals(GivenBucket.read(s, ref), Some(Kept("Question: will it?\nNow: 2027-01-04", true)))

  test("an object changed after it was kept is read as not intact"):
    val s   = store
    val ref = GivenBucket.keep(s, "the original")
    s.bucket(GivenBucket.Name)
      .putBytes(ref.sha256, "a changed text".getBytes(UTF_8), GivenBucket.ContentType)
    assertEquals(GivenBucket.read(s, ref).map(_.intact), Some(false))
