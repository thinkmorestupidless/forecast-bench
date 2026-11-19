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

  val all: List[Arm] = List(venuePrice, modelAlone)

  def named(name: String): Option[Arm] = all.find(_.name == name)

  def names: Set[String] = all.map(_.name).toSet

  /** The features name arms in words ("venue price"); ids are hyphenated. */
  def byWords(words: String): String = words.trim.toLowerCase.replace(' ', '-')
