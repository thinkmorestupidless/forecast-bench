package forecastbench.gather

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString, writeToString}
import com.thinkmorestupidless.ankka.core.Codecs

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import scala.util.Try

/**
 * The `Search` on Exa (research R12), written from Exa's documentation of `POST /search` as read on
 * 2026-11-19: the key in `x-api-key`; `query`, `numResults`, `type`, `endPublishedDate` and
 * `contents.text.maxCharacters` in the body; each result's `url`, `title`, `publishedDate` and
 * `text`. Exa names no source for a result, so the source is the address's host.
 */
final class Exa(key: Option[String], excerpt: Int, http: HttpClient = Exa.client) extends Search:
  val name = "exa"

  def search(
      query: String,
      publishedBefore: Option[Instant],
      limit: Int
  ): Either[SearchFailed, List[SearchResult]] =
    key.filter(_.nonEmpty) match
      case None => Left(SearchFailed(name, "EXA_API_KEY is not set"))
      case Some(k) =>
        val body = writeToString(
          Exa.Request(
            query,
            limit,
            "auto",
            publishedBefore.map(_.toString),
            Exa.Contents(Exa.Text(excerpt))
          )
        )
        val request = HttpRequest
          .newBuilder(URI.create(Exa.Endpoint))
          .timeout(Duration.ofSeconds(30))
          .header("x-api-key", k)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build()
        Try(http.send(request, HttpResponse.BodyHandlers.ofString())).toEither.left
          .map(e => SearchFailed(name, s"Exa could not be reached: ${e.getMessage}"))
          .flatMap { r =>
            if r.statusCode / 100 == 2 then Exa.decode(r.body).left.map(SearchFailed(name, _))
            else Left(SearchFailed(name, s"Exa answered ${r.statusCode}"))
          }

object Exa:
  val Endpoint = "https://api.exa.ai/search"

  lazy val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  def fromEnv(excerpt: Int): Exa = Exa(sys.env.get("EXA_API_KEY"), excerpt)

  final case class Text(maxCharacters: Int)
  final case class Contents(text: Text)
  final case class Request(
      query: String,
      numResults: Int,
      `type`: String,
      endPublishedDate: Option[String],
      contents: Contents
  )

  final case class Result(
      url: String,
      title: Option[String] = None,
      publishedDate: Option[String] = None,
      text: Option[String] = None
  )
  final case class Reply(results: List[Result] = Nil)

  given JsonValueCodec[Request] = forecastbench.domain.Wire.make
  given JsonValueCodec[Reply]   = Codecs.make

  /** A reply's results in Exa's order; a date Exa gives that cannot be read is left undated. */
  def decode(body: String): Either[String, List[SearchResult]] =
    Try(readFromString[Reply](body)).toEither.left
      .map(e => s"Exa's reply could not be read: ${e.getMessage}")
      .map(_.results.map { r =>
        SearchResult(
          r.url,
          r.title.getOrElse(""),
          Search.host(r.url),
          r.publishedDate.flatMap(d => Try(Instant.parse(d)).toOption),
          r.text.getOrElse("")
        )
      })
