package forecastbench.venues

import forecastbench.domain.{Arm, Pack, QuestionGiven}

import java.time.Instant

/** What a model is given of a venue's questions, checked for the venue (the second rule). */
object Blinded:

  private val now = Instant.parse("2026-11-19T19:00:00Z")

  /** Every name and host a model must not be given, lower case. */
  val Words: List[String] = Arm.VenueNames.map(_.toLowerCase) ++ Pack.VenueHosts.toList

  def named(text: String): List[String] = Words.filter(text.toLowerCase.contains)

  /** The questions' criteria name a venue, and nothing the block built from them does. */
  def check(questions: List[VenueQuestion]): Unit =
    val raw = questions.map(q => q.statement + " " + q.criteria.getOrElse(""))
    assert(raw.exists(named(_).nonEmpty), "the recorded replies name no venue: nothing is checked")
    questions.foreach { q =>
      val block = Arm.block(QuestionGiven(q.statement, q.criteria.getOrElse(""), q.closesAt, now))
      assert(named(block).isEmpty, s"${q.venueId} gives a model ${named(block)}: $block")
    }
