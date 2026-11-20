package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}

import scala.util.Try

/**
 * How a model's reply is read as JSON (contracts/arm.md): the reply itself, optionally in a fenced
 * block; else, when the model wrote something before or after it, the last JSON object in the
 * reply. Whichever is read must still be of the form asked for. The whole reply is kept as answered
 * either way.
 */
object Reply:

  def read[A: JsonValueCodec](reply: String): Option[A] =
    candidates(reply).iterator.flatMap(t => Try(readFromString[A](t)).toOption).nextOption()

  /** The reply without a fence, then its last JSON object, when that is something else. */
  def candidates(reply: String): List[String] =
    val whole = reply.trim.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim
    (whole :: lastObject(reply).toList).distinct

  /**
   * The last balanced top-level `{…}` of the text, braces inside a JSON string not counted, so a
   * reply's closing object is found whatever its reasoning before it held.
   */
  def lastObject(text: String): Option[String] =
    var found  = Option.empty[String]
    var depth  = 0
    var start  = -1
    var inside = false
    var i      = 0
    while i < text.length do
      val c = text(i)
      if inside then
        if c == '\\' then i += 1
        else if c == '"' then inside = false
      else if c == '"' && depth > 0 then inside = true
      else if c == '{' then
        if depth == 0 then start = i
        depth += 1
      else if c == '}' && depth > 0 then
        depth -= 1
        if depth == 0 then found = Some(text.substring(start, i + 1))
      i += 1
    found
