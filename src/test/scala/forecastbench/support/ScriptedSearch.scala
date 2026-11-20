package forecastbench.support

import forecastbench.gather.{Search, SearchFailed, SearchResult}

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.jdk.CollectionConverters.*

/**
 * The search the suites gather with. A scenario says what a query holding a word of its own finds
 * (its question's statement, which every query the scripted model writes repeats), so scenarios
 * sharing the one bench never read each other's results. Anything unscripted finds three documents
 * of its own.
 */
final class ScriptedSearch extends Search:
  val name = "scripted"

  private val found   = ConcurrentHashMap[String, String => Either[String, List[SearchResult]]]()
  private val holds   = ConcurrentHashMap[String, CountDownLatch]()
  private val queries = ConcurrentLinkedQueue[(String, Option[Instant])]()

  def finds(word: String, results: List[SearchResult]): Unit =
    found.put(word, _ => Right(results)): Unit

  /** What each query holding the word finds, by the query. */
  def findsBy(word: String)(results: String => List[SearchResult]): Unit =
    found.put(word, q => Right(results(q))): Unit
  def findsNothing(word: String): Unit = finds(word, Nil)
  def fails(word: String): Unit = found.put(word, _ => Left("the scripted search is down")): Unit

  /**
   * The next search for the word throws, once, as a search with a fault in it would: the step
   * fails. Once, since scenario numbers repeat across suites and the search is shared.
   */
  def throws(word: String): Unit =
    found.put(
      word,
      _ =>
        found.remove(word)
        throw IllegalStateException("the scripted search threw")
    ): Unit

  /** Holds every search for the word until `release`: a pack not yet gathered. */
  def hold(word: String): Unit    = holds.put(word, CountDownLatch(1)): Unit
  def release(word: String): Unit = Option(holds.remove(word)).foreach(_.countDown())

  def asked(word: String): List[(String, Option[Instant])] =
    queries.asScala.toList.filter(_._1.contains(word))

  def search(
      query: String,
      publishedBefore: Option[Instant],
      limit: Int
  ): Either[SearchFailed, List[SearchResult]] =
    queries.add(query -> publishedBefore)
    holds.asScala
      .find((w, _) => query.contains(w))
      .foreach((_, latch) => latch.await(2, TimeUnit.MINUTES))
    found.asScala.find((w, _) => query.contains(w)).map(_._2(query)) match
      case Some(Left(why))      => Left(SearchFailed(name, why))
      case Some(Right(results)) => Right(results.take(limit))
      case None                 => Right(ScriptedSearch.default(query).take(limit))

object ScriptedSearch:
  val Published: Instant = Instant.parse("2026-12-20T10:00:00Z")

  def result(address: String, published: Option[Instant] = Some(Published), text: String = "") =
    SearchResult(
      address,
      s"Title of $address",
      Search.host(address),
      published,
      if text.isEmpty then s"The text at $address." else text
    )

  /** Three documents a query finds unless a scenario says otherwise, named by the query. */
  def default(query: String): List[SearchResult] =
    val slug = query.toLowerCase.replaceAll("[^a-z0-9]+", "-").take(40)
    (1 to 3).toList.map(i => result(s"https://news.example/$slug/$i"))
