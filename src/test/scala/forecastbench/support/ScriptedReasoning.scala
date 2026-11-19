package forecastbench.support

import com.thinkmorestupidless.ankka.sdk.ServiceResponse

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/**
 * The reasoning service as the suites play it (contracts/reasoning.md): it holds what it is sent,
 * answering a new record `201` and the same record again `200`, and answers `/graph/wait` and
 * `/answers/case` from what it holds, as ankka-reasoning 0.1.1 documents them. It can be made
 * unreachable (`503 graph.unavailable`) and made to refuse a route with a rule.
 *
 * `respond` is the whole service, so it serves a `ScriptedService` on loopback for the features and
 * a `ScriptedServices` for a unit suite alike.
 */
final class ScriptedReasoning:

  /** Every record held, by kind and id, as it was sent. */
  private val held    = ConcurrentHashMap[(String, String), ujson.Value]()
  private val order   = java.util.concurrent.atomic.AtomicLong(0)
  private val when    = ConcurrentHashMap[(String, String), Long]()
  private val refusal = ConcurrentHashMap[String, String]()
  private val down    = AtomicBoolean(false)
  private val sent    = java.util.concurrent.ConcurrentLinkedQueue[(String, String, String)]()

  private val downFor = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  def unreachable(): Unit = down.set(true)
  def reachable(): Unit   = down.set(false)

  /** Unreachable for every request whose path or body holds the word: one question's records. */
  def unreachable(word: String): Unit = downFor.add(word): Unit
  def reachable(word: String): Unit   = downFor.remove(word): Unit

  /** The graph loses a record: it is no longer held or answered. */
  def forget(kind: String, id: String): Unit =
    held.remove(kind -> id)
    when.remove(kind -> id): Unit

  /** The next writes to the route (`/claims`) are refused with the rule, until `admit`. */
  def refuse(route: String, rule: String): Unit = refusal.put(route, rule): Unit
  def admit(route: String): Unit                = refusal.remove(route): Unit

  def get(kind: String, id: String): Option[ujson.Value] = Option(held.get(kind -> id))

  def all(kind: String): List[ujson.Value] =
    held.asScala.toList
      .collect { case ((k, id), v) if k == kind => (when.get(k -> id), v) }
      .sortBy(_._1)
      .map(_._2)

  /** Every request, as `(method, path, body)`, in order. */
  def requests: List[(String, String, String)] = sent.asScala.toList

  private def json(status: Int, body: ujson.Value) =
    ServiceResponse(
      status,
      "application/json",
      ujson.write(body).getBytes(StandardCharsets.UTF_8),
      Vector.empty
    )

  private def refused(status: Int, rule: String) =
    json(
      status,
      ujson.Obj("rule" -> rule, "status" -> status, "error" -> rule, "names" -> ujson.Obj())
    )

  /** The record held under the id, or this one, held now: `201` when new, `200` when the same. */
  private def write(kind: String, id: String, record: ujson.Value): ServiceResponse =
    Option(held.get(kind -> id)) match
      case Some(was) if was == record => json(200, was)
      case Some(_)                    => refused(409, s"$kind.id.taken")
      case None =>
        held.put(kind -> id, record)
        when.put(kind -> id, order.incrementAndGet())
        json(201, record)

  private def query(path: String): Map[String, String] =
    path
      .dropWhile(_ != '?')
      .drop(1)
      .split('&')
      .filter(_.contains('='))
      .map { kv =>
        val (k, v) = kv.splitAt(kv.indexOf('='))
        k -> java.net.URLDecoder.decode(v.drop(1), StandardCharsets.UTF_8)
      }
      .toMap

  private def hex(s: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(s.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString

  /**
   * A claim as an answer gives it: a `support`, with its evidence and the evidence's source inside.
   */
  private def support(claim: ujson.Value): ujson.Value =
    val evidence = claim("derivesFrom").arr.toList.flatMap(e => get("evidence", e.str)).map { e =>
      val source = get("source", e("source").str).getOrElse(ujson.Obj("id" -> e("source")))
      val out = ujson.Obj(
        "id"      -> e("id"),
        "source"  -> source,
        "locator" -> e("locator"),
        "excerpt" -> e("excerpt")
      )
      e.obj.get("publishedAt").foreach(p => out("publishedAt") = p)
      out
    }
    ujson.Obj(
      "claim" -> ujson.Obj(
        "id"        -> claim("id"),
        "holder"    -> claim("holder"),
        "statement" -> claim("statement"),
        "withdrawn" -> false
      ),
      "evidence"  -> ujson.Arr.from(evidence),
      "revisedBy" -> ujson.Arr(),
      "heldBy"    -> ujson.Arr()
    )

  def respond(method: String, path: String, body: Array[Byte]): ServiceResponse =
    val text = String(body, StandardCharsets.UTF_8)
    sent.add((method, path, text))
    val route = path.takeWhile(_ != '?')
    if down.get || downFor.asScala.exists(w => path.contains(w) || text.contains(w)) then
      refused(503, "graph.unavailable")
    else
      Option(refusal.get(route)) match
        case Some(rule) => refused(422, rule)
        case None =>
          (method, route) match
            case ("POST", "/holders") => val b = ujson.read(text); write("holder", b("id").str, b)
            case ("POST", "/questions") =>
              val b = ujson.read(text); write("question", b("id").str, b)
            case ("POST", "/sources") => val b = ujson.read(text); write("source", b("id").str, b)
            case ("POST", "/evidence") =>
              val b = ujson.read(text)
              val id = hex(
                b("source").str + "\u0000" + b("locator").str + "\u0000" + b("excerpt").str
              )
              b("id") = id
              write("evidence", id, b)
            case ("POST", "/claims") =>
              val b = ujson.read(text)
              if b("derivesFrom").arr.exists(e => get("evidence", e.str).isEmpty) then
                refused(422, "claim.evidence.unknown")
              else write("claim", b("id").str, b)
            case ("POST", "/beliefs/revisions") =>
              val b = ujson.read(text); write("revision", b("id").str, b)
            case ("POST", "/graph/wait") =>
              val b = ujson.read(text)
              if get(b("kind").str, b("id").str).isEmpty then refused(404, "graph.wait.not-held")
              else
                json(200, ujson.Obj("caughtUp" -> true, "waitedMs" -> 0, "missing" -> ujson.Arr()))
            case ("GET", "/answers/case") =>
              val q = query(path)
              val claims = all("claim").filter(
                _("stances").arr.exists(s =>
                  s("hypothesis").str == q("hypothesis") && s("stance").str == q("stance")
                )
              )
              json(
                200,
                ujson.Obj(
                  "claims"        -> ujson.Arr.from(claims.map(support)),
                  "revisedClaims" -> ujson.Arr()
                )
              )
            case _ => refused(404, "route.unknown")
