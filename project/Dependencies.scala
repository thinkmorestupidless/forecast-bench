import sbt.*

/** Single source of truth for every external version. */
object Dependencies {

  object V {
    val scala = "3.9.0"
    // The same version is declared in service.json as `runtime`: change both together.
    val ankka = "0.18.0"
    val munit = "1.3.6"
    val ujson = "4.4.3"
  }

  private def ankka(module: String) = "com.thinkmorestupidless" %% s"ankka-$module" % V.ankka

  val ankkaSdk       = ankka("sdk")
  val ankkaRuntime   = ankka("runtime")
  val ankkaHttp      = ankka("http")
  val ankkaAgent     = ankka("agent")
  val ankkaTelemetry = ankka("telemetry-otlp")
  val ankkaTestkit   = ankka("testkit")

  val munit = "org.scalameta" %% "munit" % V.munit
  // For reading replies in tests: not the service's own JSON.
  val ujson = "com.lihaoyi" %% "ujson" % V.ujson
}
