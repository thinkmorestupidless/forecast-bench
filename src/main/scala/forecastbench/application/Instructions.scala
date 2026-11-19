package forecastbench.application

import forecastbench.domain.{Ids, InstructionRef}

import java.nio.charset.StandardCharsets.UTF_8

/** One version of one set of instructions: its number, its text and the SHA-256 of the text. */
final case class Instruction(name: String, version: Int, text: String):
  def digest: String      = Ids.sha256(text)
  def ref: InstructionRef = InstructionRef(version, digest)

/**
 * Instructions are files, `instructions/<name>/<n>.md` on the classpath (research R6): a version is
 * the file's number, and a change is a new file, so a run can name the text it was given.
 */
object Instructions:

  /** Every set of instructions the bench has, by directory. */
  val names: List[String] = List(
    "model-alone",
    "evidence",
    "graph",
    "prose",
    "appraisal-reader",
    "appraisal-open-points",
    "gatherer-queries"
  )

  def at(name: String, version: Int): Option[Instruction] =
    Option(getClass.getClassLoader.getResourceAsStream(s"instructions/$name/$version.md")).map {
      in =>
        try Instruction(name, version, String(in.readAllBytes(), UTF_8))
        finally in.close()
    }

  /** The highest numbered version, counting up from 1 until one is missing. */
  def latest(name: String): Option[Instruction] =
    Iterator.from(1).map(at(name, _)).takeWhile(_.isDefined).flatten.toList.lastOption

  /** The version a cohort fixes: the one named, else the latest. */
  def fixed(name: String, named: Option[Int]): Either[String, Instruction] =
    named match
      case Some(v) => at(name, v).toRight(s"there is no version $v of the instructions '$name'")
      case None    => latest(name).toRight(s"there are no instructions '$name'")
