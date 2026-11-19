# Contract: the two ports to the outside

Both are traits with one real implementation each family and one scripted one for the suites.
Neither is called from an entity.

## Venue

```scala
trait Venue:
  def name: String                                              // "kalshi"
  def open(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]]
  def resolved(filter: VenueFilter): Either[VenueUnreadable, List[VenueQuestion]]
  def one(venueId: String): Either[VenueUnreadable, Option[VenueQuestion]]

final case class VenueQuestion(
  venueId: String, eventGroup: String, statement: String, criteria: Option[String],
  closesAt: Instant, category: Option[String], outcomes: List[String],
  probability: Option[VenueReading], result: Option[String],   // the venue's own word
  outcome: Option[OutcomeValue], disputed: Boolean, resolvedAt: Option[Instant], volume: Option[BigDecimal])
```

The mapping of each venue's fields is `research.md` R9. `outcome` is the bench's reading of
`result`: yes, no or void.

## Search

```scala
trait Search:
  def name: String                                              // "exa"
  def search(query: String, publishedBefore: Option[Instant], limit: Int): Either[SearchFailed, List[SearchResult]]

final case class SearchResult(address: String, title: String, source: String,
                              publishedAt: Option[Instant], text: String)
```

`source` is the result's site name where the search gives one, else the address's host.
