package forecastbench.domain

/** The arms the bench knows, by name. */
object Arms:

  /** The venue's own probability. It has no `Given`: its step reads the venue instead. */
  val venuePrice: Arm = new Arm:
    val name          = "venue-price"
    val takesEvidence = false
    val usesModel     = false
    val instructions  = None
    def `given`(q: QuestionGiven, pack: Option[Pack], appraisal: Option[AppraisalContent]) =
      Left(Abstain("the venue-price arm reads the venue, not a model"))

  /** The statement, the criteria, the time it closes and the time of the run, and nothing else. */
  val modelAlone: Arm = new Arm:
    val name          = "model-alone"
    val takesEvidence = false
    val usesModel     = true
    val instructions  = Some("model-alone")
    def `given`(q: QuestionGiven, pack: Option[Pack], appraisal: Option[AppraisalContent]) =
      Right(Given("", Arm.block(q)))

  val NoEvidence = "there was no evidence"

  /**
   * A document as the evidence arm's text gives it: its number, source, time and address, then its
   * excerpt.
   */
  def document(d: Document): String =
    s"[${d.n}] ${d.source}, ${d.publishedAt.fold("undated")(Arm.when)}, ${d.address}\n${d.excerpt}"

  /** What the model-alone arm is given, and every document of the pack in order. */
  val evidence: Arm = new Arm:
    val name          = "evidence"
    val takesEvidence = true
    val usesModel     = true
    val instructions  = Some("evidence")
    def `given`(q: QuestionGiven, pack: Option[Pack], appraisal: Option[AppraisalContent]) =
      pack.filterNot(_.empty) match
        case None => Left(Abstain(NoEvidence))
        case Some(p) =>
          Right(
            Given("", Arm.block(q) + "\nDocuments:\n" + p.documents.map(document).mkString("\n"))
          )

  val all: List[Arm] = List(venuePrice, modelAlone, evidence)

  def named(name: String): Option[Arm] = all.find(_.name == name)

  def names: Set[String] = all.map(_.name).toSet

  /** The features name arms in words ("venue price"); ids are hyphenated. */
  def byWords(words: String): String = words.trim.toLowerCase.replace(' ', '-')
