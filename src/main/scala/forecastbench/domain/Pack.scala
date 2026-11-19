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
