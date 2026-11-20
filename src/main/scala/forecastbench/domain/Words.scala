package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}

/**
 * A codec that writes a fieldless enum as its word (`"yes"`), not as `{"type":"Yes"}`: the words
 * are what the routes answer and what the journal holds, so they are fixed here once.
 */
object Words:

  def codec[E](all: Seq[E], word: E => String): JsonValueCodec[E] =
    val byWord = all.map(e => word(e) -> e).toMap
    new JsonValueCodec[E]:
      def decodeValue(in: JsonReader, default: E): E =
        val w = in.readString(null)
        byWord.getOrElse(w, in.decodeError(s"'$w' is not one of ${byWord.keys.mkString(", ")}"))
      def encodeValue(x: E, out: JsonWriter): Unit = out.writeVal(word(x))
      def nullValue: E                             = null.asInstanceOf[E]

  def parse[E](all: Seq[E], word: E => String, text: String): Option[E] =
    all.find(e => word(e) == text)
