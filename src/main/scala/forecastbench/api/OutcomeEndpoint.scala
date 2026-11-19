package forecastbench.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.Codecs
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import forecastbench.application.{Bench, OutcomeReader, OutcomesRead}

/** Reads the outcomes now rather than on the hour; nothing else states an outcome. */
final class OutcomeEndpoint(client: ComponentClient) extends HttpEndpoint("/outcomes"):
  import OutcomeEndpoint.given

  val acl: Acl = Acl.allowCallers(Callers.self)

  post("/read")(() => OutcomeReader.readAll(client, Bench.edges.clock.now))

object OutcomeEndpoint:
  given JsonValueCodec[OutcomesRead] = Codecs.make
