package forecastbench.application

import com.thinkmorestupidless.ankka.sdk.ObjectStorage
import forecastbench.domain.{Ids, ObjectRef}

import java.nio.charset.StandardCharsets.UTF_8

/** A text read back from the bucket, and whether it still matches the digest it was named by. */
final case class Kept(text: String, intact: Boolean)

/**
 * The bucket `given` (research R3): what a model was given and what it answered, each an object
 * named by its SHA-256, written before the run is recorded and never deleted. A changed object no
 * longer matches the run that names it.
 */
object GivenBucket:

  val Name        = "given"
  val ContentType = "text/plain; charset=utf-8"

  def keep(storage: ObjectStorage, text: String): ObjectRef =
    val bytes = text.getBytes(UTF_8)
    val name  = Ids.sha256(bytes)
    storage.bucket(Name).putBytes(name, bytes, ContentType)
    ObjectRef(name, bytes.length.toLong)

  def read(storage: ObjectStorage, ref: ObjectRef): Option[Kept] =
    storage.bucket(Name).getBytes(ref.sha256).map { (_, bytes) =>
      Kept(
        String(bytes, UTF_8),
        Ids.sha256(bytes) == ref.sha256 && bytes.length.toLong == ref.bytes
      )
    }
