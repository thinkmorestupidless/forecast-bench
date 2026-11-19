package forecastbench.steps

import com.thinkmorestupidless.ankka.testkit.GherkinSuite

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.concurrent.duration.*

/**
 * One feature file run against the whole bench through its routes (research R14).
 *
 * `pending` names the scenarios of the file whose steps a later task binds: they are reported as
 * ignored, never passed, and the name must be a scenario of the file. The feature file itself is
 * never tagged.
 */
abstract class FeatureSuite(feature: String) extends GherkinSuite(feature):

  override val munitTimeout = 3.minutes

  /** Scenarios whose steps are bound by a later task, by name. */
  protected def pending: Set[String] = Set.empty

  override def munitTests(): Seq[Test] =
    val tests = super.munitTests()
    val stale = pending.filterNot(name => tests.exists(_.name.startsWith(name + " (")))
    val marked = tests.map { t =>
      if pending.exists(name => t.name.startsWith(name + " (")) then
        t.withName(s"${t.name} is bound by a later task").tag(munit.Ignore)
      else t
    }
    if stale.isEmpty then marked
    else
      val why = s"no scenario named ${stale.toVector.sorted.mkString("'", "', '", "'")}"
      marked :+ new Test(
        s"$feature holds every scenario its suite leaves pending",
        () => scala.concurrent.Future.failed(AssertionError(why)),
        Set.empty,
        munit.Location.empty
      )

  private var current: Option[(String, World)] = None

  /** The running scenario's world, made fresh when a scenario starts. */
  protected def world: World =
    current match
      case Some((id, w)) if id == scenarioId => w
      case _ =>
        val w = World.next()
        current = Some(scenarioId -> w)
        w

  protected def fixture: ServiceFixture.type = ServiceFixture

  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  /** A request to the bench: the status and the body. */
  protected def send(method: String, path: String, body: Option[String] = None): (Int, String) =
    val builder = HttpRequest
      .newBuilder(URI.create(fixture.baseUrl + path))
      .timeout(Duration.ofSeconds(60))
    body match
      case Some(json) =>
        builder.header("Content-Type", "application/json")
        builder.method(method, HttpRequest.BodyPublishers.ofString(json)): Unit
      case None => builder.method(method, HttpRequest.BodyPublishers.noBody()): Unit
    val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    (response.statusCode, response.body)

  protected def get(path: String): (Int, String) = send("GET", path)
  protected def post(path: String, json: String = "{}"): (Int, String) =
    send("POST", path, Some(json))
  protected def put(path: String, json: String): (Int, String) = send("PUT", path, Some(json))

  /** A request a step needs to succeed in setting a scenario up. */
  protected def ok(result: (Int, String)): String =
    assert(result._1 / 100 == 2, s"setting up failed with ${result._1}: ${result._2}")
    result._2

  /** Parses a JSON body. */
  protected def json(text: String): ujson.Value = ujson.read(text)

  /** Waits for a condition the bench reaches in its own time: a workflow, a view. */
  protected def eventually[A](what: String, within: FiniteDuration = 30.seconds)(
      check: => Option[A]
  ): A =
    val deadline = within.fromNow
    var seen     = check
    while seen.isEmpty && deadline.hasTimeLeft() do
      Thread.sleep(100)
      seen = check
    seen.getOrElse(fail(s"not seen within $within: $what"))
