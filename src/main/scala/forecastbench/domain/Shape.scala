package forecastbench.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.time.Instant

/** A claim's one stance on the yes hypothesis. */
enum Stance:
  case Supports, Contradicts

  def word: String = toString.toLowerCase

object Stance:
  given JsonValueCodec[Stance]            = Words.codec(values.toSeq, _.word)
  def parse(text: String): Option[Stance] = Words.parse(values.toSeq, _.word, text)

/** A claim as the graph and prose arms are both given it (contracts/arm.md). */
final case class ClaimGiven(
    id: String,
    statement: String,
    stance: Stance,
    document: Int,
    source: String,
    publishedAt: Option[Instant],
    excerpt: String
)

/** The one value both arms are rendered from: the appraisal as read back from the graph. */
final case class AppraisalContent(claims: List[ClaimGiven], openPoints: List[String])

/**
 * The graph and prose renderings of one `AppraisalContent`, and `contentOf`, which reads either
 * back (contracts/arm.md). Every free-text field is escaped, so no field has a line of its own and
 * a quote in one is never taken for the end of a quoted excerpt.
 */
object Shape:

  /**
   * What the equality compares: each claim's id, stance, document, statement and excerpt; the open
   * points.
   */
  final case class Content(
      claims: Set[(String, Stance, Int, String, String)],
      openPoints: List[String]
  )

  def of(c: AppraisalContent): Content =
    Content(
      c.claims.map(k => (k.id, k.stance, k.document, k.statement, k.excerpt)).toSet,
      c.openPoints
    )

  def esc(s: String): String = s.flatMap {
    case '\\' => "\\\\"
    case '"'  => "\\\""
    case '\n' => "\\n"
    case '\r' => "\\r"
    case c    => c.toString
  }

  def unesc(s: String): String =
    val b = StringBuilder()
    var i = 0
    while i < s.length do
      if s(i) == '\\' && i + 1 < s.length then
        b += (s(i + 1) match
          case 'n' => '\n'
          case 'r' => '\r'
          case c   => c)
        i += 2
      else
        b += s(i)
        i += 1
    b.toString

  private def published(t: Option[Instant]): String = t.fold("undated")(Arm.when)

  /** The claims in the prose's order: by document in the pack's order, then as stated. */
  def ordered(c: AppraisalContent): List[ClaimGiven] =
    c.claims.zipWithIndex.sortBy((k, i) => (k.document, i)).map(_._1)

  val CaseFor     = "Case for yes:"
  val CaseAgainst = "Case against yes:"
  val OpenPoints  = "Open points:"
  val Unsettled   = "What the evidence does not settle: "

  def graph(q: QuestionGiven, c: AppraisalContent): String =
    def claims(s: Stance) = c.claims
      .filter(_.stance == s)
      .map(k =>
        s"- [${k.id}] ${esc(k.statement)}\n  from [${k.document}] ${esc(k.source)}, ${published(k.publishedAt)}: \"${esc(k.excerpt)}\""
      )
    (List(Arm.block(q), CaseFor) ++ claims(Stance.Supports) ++ List(CaseAgainst) ++
      claims(Stance.Contradicts) ++ List(OpenPoints) ++ c.openPoints.map(p => s"- ${esc(p)}"))
      .mkString("\n")

  def prose(q: QuestionGiven, c: AppraisalContent): String =
    val sentences = ordered(c).map(k =>
      s"According to [${k.document}] ${esc(k.source)} (${published(k.publishedAt)}), ${esc(k.statement)}; " +
        s"this ${k.stance.word} yes [${k.id}], on the words \"${esc(k.excerpt)}\"."
    )
    val open = Option.when(c.openPoints.nonEmpty)(
      Unsettled + c.openPoints.map(p => "\"" + esc(p) + "\"").mkString("; ") + "."
    )
    Arm.block(q) + "\n" + (sentences ++ open).mkString(" ")

  /** The content of a graph or a prose rendering; `None` when the text is neither. */
  def contentOf(text: String): Option[Content] =
    if text.contains(s"\n$CaseFor\n") then readGraph(text) else readProse(text)

  /**
   * A quoted field from just after its opening quote: its text and the index after its closing
   * quote.
   */
  private def quoted(s: String, from: Int): (String, Int) =
    var i = from
    while s(i) != '"' do i += (if s(i) == '\\' then 2 else 1)
    (unesc(s.substring(from, i)), i + 1)

  private def readGraph(text: String): Option[Content] = scala.util.Try {
    val lines   = text.substring(text.lastIndexOf(s"\n$CaseFor\n") + 1).split("\n", -1).toList
    var stance  = Option.empty[Stance]
    var claims  = List.empty[(String, Stance, Int, String, String)]
    var points  = List.empty[String]
    var pending = Option.empty[(String, String)]
    lines.foreach {
      case CaseFor     => stance = Some(Stance.Supports)
      case CaseAgainst => stance = Some(Stance.Contradicts)
      case OpenPoints  => stance = None
      case l if l.startsWith("  from [") =>
        val (id, statement) = pending.get
        val n               = l.substring(8, l.indexOf("] ")).toInt
        val (excerpt, end)  = quoted(l, l.indexOf(": \"") + 3)
        require(end == l.length)
        claims = claims :+ ((id, stance.get, n, statement, excerpt))
        pending = None
      case l if l.startsWith("- [") && stance.isDefined =>
        val close = l.indexOf("] ")
        pending = Some((l.substring(3, close), unesc(l.substring(close + 2))))
      case l if l.startsWith("- ") => points = points :+ unesc(l.substring(2))
      case l                       => throw IllegalArgumentException(s"not a line of the graph: $l")
    }
    require(pending.isEmpty)
    Content(claims.toSet, points)
  }.toOption

  private val Head    = """According to \[(\d+)\] """.r
  private val When    = """ \((\d{4}-\d{2}-\d{2} \d{2}:\d{2} UTC|undated)\), """.r
  private val StanceR = """; this (supports|contradicts) yes \[([^\]]+)\], on the words """".r

  private def readProse(text: String): Option[Content] = scala.util.Try {
    val p      = text.substring(text.lastIndexOf('\n') + 1)
    var i      = 0
    var claims = List.empty[(String, Stance, Int, String, String)]
    var points = List.empty[String]
    while p.startsWith("According to [", i) do
      val head           = Head.findPrefixMatchOf(p.substring(i)).get
      val n              = head.group(1).toInt
      val when           = When.findFirstMatchIn(p.substring(i + head.end)).get
      val from           = i + head.end + when.end
      val stance         = StanceR.findFirstMatchIn(p.substring(from)).get
      val statement      = unesc(p.substring(from, from + stance.start))
      val (excerpt, end) = quoted(p, from + stance.end)
      require(p(end) == '.')
      claims =
        claims :+ ((stance.group(2), Stance.parse(stance.group(1)).get, n, statement, excerpt))
      i = end + 1
      if i < p.length then
        require(p(i) == ' ')
        i += 1
    if p.startsWith(Unsettled, i) then
      i += Unsettled.length
      var more = true
      while more do
        require(p(i) == '"')
        val (point, end) = quoted(p, i + 1)
        points = points :+ point
        if p.startsWith("; ", end) then i = end + 2
        else
          require(p(end) == '.' && end + 1 == p.length)
          i = end + 1
          more = false
    require(i == p.length)
    Content(claims.toSet, points)
  }.toOption
