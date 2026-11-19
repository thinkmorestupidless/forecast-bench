package forecastbench.api

import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}
import com.thinkmorestupidless.ankka.http.HttpProblem

/** How an entity's refusal reaches the experimenter (contracts/http.md). */
object Refusals:

  /** An entity's `BadRequest` is the contract's `422`; every other code keeps ankka's mapping. */
  def invalidAs422[A](call: => A): A =
    try call
    catch
      case e: CommandError if e.code == ErrorCode.BadRequest => throw HttpProblem(422, e.message)

  def notFound(what: String): Nothing = throw HttpProblem.notFound(what)
