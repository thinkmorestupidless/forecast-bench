package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

/**
 * Codecs for what the bench sends to another service: a field with no value is left out, never sent
 * as `null` (ankka's `Codecs.make` writes `null`, which the reasoning service and Exa do not
 * document accepting). An empty list is still sent. A field the reply has and the type does not is
 * skipped.
 */
object Wire:
  inline def make[A]: JsonValueCodec[A] =
    JsonCodecMaker.make[A](
      CodecMakerConfig.withTransientNone(true).withTransientEmpty(false).withTransientDefault(false)
    )
