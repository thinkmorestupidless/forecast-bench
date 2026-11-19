package forecastbench.gather

import java.time.Instant

/** One result of a search, in the search's own order (contracts/ports.md). */
final case class SearchResult(
    address: String,
    title: String,
    source: String,
    publishedAt: Option[Instant],
    text: String
)

final case class SearchFailed(search: String, why: String)

/** The port to a search: one real implementation (Exa) and one scripted for the suites. */
trait Search:
  def name: String

  /** Up to `limit` results for the query, published before the time when one is given. */
  def search(
      query: String,
      publishedBefore: Option[Instant],
      limit: Int
  ): Either[SearchFailed, List[SearchResult]]

object Search:
  /** A result's source: the site's name when the search gives one, else the address's host. */
  def host(address: String): String =
    scala.util
      .Try(java.net.URI.create(address).getHost)
      .toOption
      .flatMap(Option(_))
      .map(_.stripPrefix("www."))
      .getOrElse(address)
