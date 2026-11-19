# Contract: an arm

An arm is a value of

```scala
trait Arm:
  def name: String                       // "graph"
  def takesEvidence: Boolean
  def usesModel: Boolean
  def instructions: Option[String]       // the directory under instructions/, when it uses a model
  def given(q: QuestionGiven, pack: Option[Pack], appraisal: Option[AppraisalContent]): Either[Abstain, Given]

final case class QuestionGiven(statement: String, criteria: String, closesAt: Instant, now: Instant)
final case class Given(system: String, user: String)
final case class Abstain(reason: String)
```

registered by name in `Arms.all`. Adding an arm is adding a value and, when it uses a model, an
instructions directory: runs, scores and reports hold an arm by its name only (FR-010, SC-010).
The venue-price arm is the one arm with no `Given`; the step reads the venue instead.

## What each arm's model is given

Every model arm's user text begins with the same block, and nothing in any arm's text is the
venue's probability, the venue's name, or the question's address at the venue:

```text
Question: <statement>
Resolution criteria: <criteria>
Closes: <closesAt, as a date and time in UTC>
Now: <the time of the run, or the pretended time for a development cohort>
```

| Arm | After that block |
|---|---|
| model-alone | nothing |
| evidence | `Documents:` then each document of the pack in order: `[n] <source>, <published or "undated">, <address>` and its excerpt |
| graph | `Case for yes:` the claims that support, each `- [c<n>] <statement>` with `from [n] <source>, <published>: "<the evidence's excerpt>"` beneath; `Case against yes:` the same for the claims that contradict; `Open points:` each as `- <statement>` |
| prose | one paragraph: for each document in the pack's order, for each of its claims in the order stated, a sentence `According to <source> (<published>), <statement>; this <supports \| contradicts> yes [c<n>], on the words "<excerpt>".`; then `What the evidence does not settle: <open points joined by "; ">.` No headings, no lists, no blank lines. |

The graph and prose texts are both rendered from one `AppraisalContent`:

```scala
final case class AppraisalContent(claims: List[ClaimGiven], openPoints: List[String])
final case class ClaimGiven(id: String, statement: String, stance: Stance, document: Int,
                            source: String, publishedAt: Option[Instant], excerpt: String)
```

**The equality that is asserted.** `Shape.contentOf(text)` recovers from either rendering the set
of `(claim id, stance, document, statement, excerpt)` and the list of open points; for every pair
of graph and prose runs of one question and repetition the two are equal, and each equals the
`AppraisalContent`. A property test holds it for generated contents, and the bench checks it for
every pair when the second of them is recorded, failing that run if it does not hold.

## What a model answers

The system text (the arm's instructions) ends with the form of the answer:

```json
{"probability": 0.62, "abstain": null, "reasons": ["c3", "c5"], "rationale": "one or two sentences"}
```

| Reply | The run ends |
|---|---|
| `probability` a number from 0 to 1, `abstain` null | `Forecast(probability)` |
| `probability` null, `abstain` a sentence | `Abstained(that sentence)` |
| not JSON of this form; `probability` outside 0 to 1 or not a number; both or neither set | `Failed("the answer was not a probability")` |
| the provider errors or times out | `Failed("the model could not be reached")` |

`reasons` is read for the graph arm only and ignored for the others.

## Abstaining without asking

| Arm | Abstains when | Reason |
|---|---|---|
| venue-price | the venue gives no probability by the rule of research R9 | `the venue gave no probability` |
| evidence, graph, prose | the pack is empty | `there was no evidence` |
| graph, prose | the appraisal failed | `the appraisal failed` |
