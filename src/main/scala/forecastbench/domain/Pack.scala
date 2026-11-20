package forecastbench.domain

import java.time.Instant

/** One document of a pack: its place in the pack from 1, where it came from, and an excerpt. */
final case class Document(
    n: Int,
    source: String,
    address: String,
    publishedAt: Option[Instant],
    excerpt: String,
    title: String
)

/** One search the gatherer made: the query, who wrote it, and the addresses it found in order. */
final case class SearchMade(query: String, writtenBy: String, results: List[String])

/** A question's evidence for one cohort, gathered once and never changed (FR-016, FR-017). */
final case class Pack(
    cohort: String,
    question: String,
    gathererVersion: Int,
    gatheredAt: Instant,
    searches: List[SearchMade],
    documents: List[Document],
    usage: Usage,
    cost: BigDecimal
):
  def empty: Boolean = documents.isEmpty

/** The one event of a pack. */
final case class PackGathered(pack: Pack)

/** A search result as the merge reads it: what the port gave, without the port's type. */
final case class Found(
    address: String,
    title: String,
    source: String,
    publishedAt: Option[Instant],
    text: String
)

object Pack:

  /** The host suffixes of the venues' own pages, which show the venue's probability. */
  val VenueHosts: Set[String] = Set("kalshi.com", "polymarket.com")

  private def host(address: String): String =
    scala.util
      .Try(java.net.URI.create(address).getHost)
      .toOption
      .flatMap(Option(_))
      .getOrElse("")
      .toLowerCase

  def isVenuePage(address: String): Boolean =
    val h = host(address)
    VenueHosts.exists(v => h == v || h.endsWith("." + v))

  /** The gatherer's version a cohort frozen now gathers by (research R12). */
  val CurrentVersion = 2

  /** A text that names a venue, whatever its case. */
  def namesVenue(text: String): Boolean =
    val t = text.toLowerCase
    Arm.VenueNames.exists(n => t.contains(n.toLowerCase)) || VenueHosts.exists(t.contains)

  /**
   * The merge (research R12): the searches' results interleaved by rank, one address once, the
   * venues' own pages left out, and for a pretended time only documents published before it; cut to
   * `documents`, each excerpt cut to `excerpt` characters. From gatherer version 2, a document
   * whose title, address, source or excerpt names a venue is left out as well, since an arm is
   * given all four: the press quotes a venue's odds, and no model arm may be given them (the second
   * rule). A frozen cohort gathers by its own version.
   */
  def merge(
      searches: List[List[Found]],
      documents: Int,
      excerpt: Int,
      pretended: Option[Instant],
      version: Int = CurrentVersion
  ): List[Document] =
    val deepest = searches.map(_.size).maxOption.getOrElse(0)
    val ranked  = (0 until deepest).toList.flatMap(rank => searches.flatMap(_.lift(rank)))
    val kept = ranked
      .filterNot(f => isVenuePage(f.address))
      .filterNot(f =>
        version >= 2 && namesVenue(
          List(f.title, f.address, f.source, f.text.take(excerpt)).mkString(" ")
        )
      )
      .filter(f => pretended.forall(t => f.publishedAt.exists(_.isBefore(t))))
    val once = kept.foldLeft(List.empty[Found])((seen, f) =>
      if seen.exists(_.address == f.address) then seen else seen :+ f
    )
    once.take(documents).zipWithIndex.map { (f, i) =>
      Document(i + 1, f.source, f.address, f.publishedAt, f.text.take(excerpt), f.title)
    }
