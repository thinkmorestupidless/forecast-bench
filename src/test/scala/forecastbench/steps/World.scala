package forecastbench.steps

import java.util.concurrent.atomic.AtomicInteger

/**
 * What one scenario holds while it runs: the names its steps use ("november", "rates") made into
 * ids no other scenario of the JVM uses, and what its last request answered.
 */
final class World(val n: Int):

  /** The answer to the last request a `When` step made: status and body. */
  var last: (Int, String) = (0, "")

  private val named = scala.collection.mutable.Map.empty[String, String]

  /** A cohort id for a name in the feature: the name and this scenario's number. */
  def cohort(name: String): String = named.getOrElseUpdate(s"cohort:$name", s"${slug(name)}-$n")

  /** A venue's id for a question the feature names. */
  def venueId(name: String): String = named.getOrElseUpdate(s"question:$name", s"s$n-${slug(name)}")

  /** Values a scenario's steps pass from Given to Then. */
  val held = scala.collection.mutable.Map.empty[String, String]

  private def slug(text: String): String =
    text.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-").take(12)

object World:
  private val counter = AtomicInteger(0)

  def next(): World = World(counter.incrementAndGet())
