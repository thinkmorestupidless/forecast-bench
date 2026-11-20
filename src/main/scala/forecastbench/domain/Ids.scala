package forecastbench.domain

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** How everything the bench holds is named (specs/001-forecast-evaluation/data-model.md). */
object Ids:

  /** A cohort's id: 1 to 24 of lower case letters, digits and hyphens, starting with a letter. */
  def cohort(id: String): Either[String, String] =
    if id.matches("[a-z][a-z0-9-]{0,23}") then Right(id)
    else
      Left(
        s"'$id' is not a cohort id: 1 to 24 lower case letters, digits and hyphens, starting with a letter"
      )

  /** `<venue>.<the venue's id, lower case, anything outside [a-z0-9.-] as a hyphen>`. */
  def question(venue: String, venueId: String): String =
    s"$venue.${venueId.toLowerCase.replaceAll("[^a-z0-9.-]", "-")}"

  /** The first 12 hex of the SHA-256 of a question's id: short enough to sit inside other ids. */
  def qkey(questionId: String): String = sha256(questionId).take(12)

  def pack(cohort: String, questionId: String): String      = s"$cohort.${qkey(questionId)}"
  def appraisal(cohort: String, questionId: String): String = pack(cohort, questionId)

  def run(cohort: String, questionId: String, arm: String, repetition: Int, attempt: Int): String =
    s"$cohort.${qkey(questionId)}.$arm.$repetition.$attempt"

  def sha256(text: String): String = sha256(text.getBytes(UTF_8))

  def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
