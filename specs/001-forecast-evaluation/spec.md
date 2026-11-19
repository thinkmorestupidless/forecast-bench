# Feature Specification: Forecast evaluation

**Feature Branch**: `001-forecast-evaluation`

**Created**: 2026-11-19

**Status**: Draft

**Input**: User description: "The forecast evaluation harness: a measurement framework that establishes, credibly enough for an investor, whether explicitly structured reasoning produces better-calibrated probabilistic forecasts than the same information given as unstructured text (Hypothesis B), and that later specs can extend to accumulated experience (Hypothesis C)."

The claim to be tested is that a forecast made from explicitly represented reasoning (hypotheses,
the claims that support and contradict them, the points the evidence leaves open) is better than a
forecast made from the same reasoning written out as prose. Nobody has measured it. This feature
builds the instrument that can: the bench. It imports questions about the future from public
venues, fixes a set of them and the ways of answering into a cohort, has each arm forecast each
question before its outcome exists, keeps every forecast unchangeably with everything it came from,
reads the outcomes from the venues, scores the forecasts, and reports what has been shown, with the
number of questions behind every figure and a plain statement of what has not been shown yet.

Five arms are built here. Three are the baselines every later arm is measured against: the venue's
own probability, the model alone, and the model with an evidence pack. Two answer the claim: the
graph arm and the prose arm read one shared appraisal of the evidence, the first as structure and
the second as one passage, so that they differ in shape and in nothing else.

The measurement comes before the thing measured on purpose: each later capability (retrieval, a
knowledge graph, reasoning history, accumulated experience) arrives as a new arm and is believed
only as far as this bench says.

## Clarifications

### Session 2026-11-19

- Q: What is the repository, and who is it for? → A: `forecast-bench`, private, named for no brand. It evaluates any forecasting system; the reasoning service and the prediction market product are its first subjects.
- Q: What is it built with? → A: An ankka service in Scala. Questions, cohorts, runs and outcomes are journaled, and the journal is the audit trail.
- Q: How much does this first feature cover? → A: The whole instrument and the two arms that answer Hypothesis B: the ledger, the arm contract, the venue-price, model-alone and evidence arms, the graph arm and its prose control, scoring and reports.
- Q: Where do questions come from? → A: Imported from live public venues and forecast prospectively. Questions already resolved when imported are development questions and never count.
- Q: Where does a question's evidence pack come from? → A: Gathered by search when the cohort is frozen, by a versioned gatherer, then never changed.
- Q: How does the graph arm arrive at its probability so that it differs from its control only in structure? → A: One shared appraisal, two readings. The appraisal reads the pack once and records claims with stances, and open points. The graph arm's model is given the case for, the case against and the open points; the prose arm's model is given the same claims, documents and open points as one passage. The graph arm's forecast is held in the reasoning service as a belief resting on its reasons.
- Q: How often does an arm forecast a question? → A: Once for each repetition in a cohort. The same question may be in a later cohort with a fresh pack.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A cohort of prospective questions, forecast and kept (Priority: P1)

The experimenter imports open questions from a venue, each with its statement, its resolution
criteria, the time it closes and the venue's probability. They draft a cohort: some of those
questions, the venue-price arm and the model-alone arm, one model, a forecast window, a number of
repetitions and a budget. They freeze it, and from then nothing about it changes. Within the window
each arm forecasts each question, and each answer is recorded as a run with everything it came
from: the arm, the model, the version of the instructions, exactly what the model was given and
what it answered, the cost, the tokens and the latency. A run is never changed or removed, and none
is recorded for a question that has closed or has an outcome.

**Why this priority**: a forecast is only evidence if it was fixed before the outcome and can be
shown to have been. Everything else in the bench rests on this record.

**Independent Test**: import twenty open questions from one venue, freeze a cohort of them with the
venue-price and model-alone arms and two repetitions, forecast it, and read sixty runs back, each
with its inputs, its answer and its time; try to change one and be refused.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/questions/import.feature`: an open question is imported with its criteria, the time it closes and its venue
- added `features/questions/import.feature`: a prospective question stays prospective after it resolves
- added `features/questions/import.feature`: a question with more than two outcomes is not imported
- added `features/questions/import.feature`: a question with no resolution criteria is not imported
- added `features/questions/import.feature`: a question imported twice is held once
- added `features/questions/import.feature`: a question takes its domain from its venue's category
- added `features/questions/import.feature`: a question in a category the experimenter has not named has the domain other
- added `features/questions/import.feature`: a venue that cannot be read leaves the questions held as they were
- added `features/cohorts/cohort.feature`: a cohort names its questions, its arms, its model, its forecast window and its budget
- added `features/cohorts/cohort.feature`: the experimenter adds questions and arms to a drafted cohort
- added `features/cohorts/cohort.feature`: a frozen cohort cannot be changed
- added `features/cohorts/cohort.feature`: no arm forecasts a cohort that is not frozen
- added `features/cohorts/cohort.feature`: a cohort cannot be frozen with a question that closes before its forecast window ends
- added `features/cohorts/cohort.feature`: a cohort cannot be frozen with no questions or no arms
- added `features/cohorts/cohort.feature`: each arm forecasts each question once for each repetition
- added `features/cohorts/cohort.feature`: a cohort stops making runs when its budget is spent
- added `features/cohorts/cohort.feature`: a run not made within the forecast window is never made
- added `features/cohorts/cohort.feature`: a question may be in a later cohort
- added `features/arms/arms.feature`: an arm answers a question with a probability
- added `features/arms/arms.feature`: the venue-price arm's forecast is the venue's probability observed in the forecast window
- added `features/arms/arms.feature`: the venue-price arm abstains when the venue gives no probability
- added `features/arms/arms.feature`: the model-alone arm is given the statement, the resolution criteria and the time the question closes
- added `features/arms/arms.feature`: no model arm is given the venue's probability
- added `features/arms/arms.feature`: every model arm of a cohort uses the cohort's model
- added `features/arms/arms.feature`: a model arm's instructions are named by version in each run
- added `features/arms/arms.feature`: an arm may abstain and say why
- added `features/arms/arms.feature`: an answer that is not a probability is a failed run
- added `features/arms/arms.feature`: a model that cannot be reached is a failed run
- added `features/arms/arms.feature`: a failed run is made again only when the experimenter asks, and both runs are kept
- added `features/arms/arms.feature`: a run with a forecast is never made again
- added `features/arms/arms.feature`: an arm the bench does not know is refused when added to a cohort
- added `features/runs/record.feature`: a run is recorded with everything its answer came from
- added `features/runs/record.feature`: a run records its cost, its tokens and its latency
- added `features/runs/record.feature`: the time of a run is set by the bench
- added `features/runs/record.feature`: a run is never changed
- added `features/runs/record.feature`: a run is never removed
- added `features/runs/record.feature`: no run is recorded for a prospective question after it closes
- added `features/runs/record.feature`: no run is recorded for a prospective question that has an outcome
- added `features/runs/record.feature`: the experimenter reads the history of a cohort in the order it happened
- added `features/runs/record.feature`: a run can be made again from its record without touching it

---

### User Story 2 - Outcomes, scores and an honest report (Priority: P1)

When a venue resolves a question, the bench reads the outcome: yes, no or void, with the venue and
the time. It never decides one itself. Each run with a forecast, on a question whose outcome is yes
or no and not disputed, is given a Brier score and a log score by a named scoring method. The
experimenter asks for the report of a cohort and reads, for each arm, its mean scores and the
number of questions behind them; for each pair of arms, a comparison on only the questions both
forecast, with the mean difference, its interval and its count; a calibration table for each arm;
and the same figures by domain and by horizon. The report says what it leaves out (questions
waiting, void, disputed; runs abstained, failed) and marks every comparison by how far it can be
trusted: too few, preliminary, or counted.

**Why this priority**: without outcomes and scores the record of forecasts says nothing, and a
report that overstates what it knows is worse than no report in front of an investor.

**Independent Test**: on a cohort with forecasts, resolve some questions at a stand-in venue, read
outcomes, and check the report's scores against hand-worked figures; with twelve scored questions
the comparison gives a count and no difference.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/outcomes/outcome.feature`: an outcome is read from the venue when the question resolves
- added `features/outcomes/outcome.feature`: a question its venue cancels has the outcome void
- added `features/outcomes/outcome.feature`: a question its venue has not resolved has no outcome
- added `features/outcomes/outcome.feature`: an outcome its venue marks as disputed is held as disputed
- added `features/outcomes/outcome.feature`: a dispute that ends leaves the outcome the venue settles on
- added `features/outcomes/outcome.feature`: an outcome its venue changes is revised and the earlier one kept
- added `features/outcomes/outcome.feature`: the experimenter cannot state an outcome
- added `features/outcomes/outcome.feature`: a venue that cannot be read leaves outcomes as they were
- added `features/scoring/score.feature`: a forecast is scored by the Brier score and the log score once its outcome is yes or no
- added `features/scoring/score.feature`: the log score of a forecast at an end is taken at the scoring method's bound
- added `features/scoring/score.feature`: a score names its scoring method
- added `features/scoring/score.feature`: a new scoring method scores again and the earlier scores are kept
- added `features/scoring/score.feature`: a run without a forecast has no score
- added `features/scoring/score.feature`: no run is scored on an outcome that is not yes or no and settled
- added `features/scoring/score.feature`: a revised outcome scores the run again and the earlier scores are kept
- added `features/scoring/score.feature`: an arm's forecast for a question is the mean of its repetitions
- added `features/scoring/score.feature`: an arm's forecast for a question uses only the repetitions that gave a forecast
- added `features/scoring/score.feature`: an arm with no forecast for a question has no score for it
- added `features/reports/report.feature`: a report gives each arm its scores and the number of questions behind them
- added `features/reports/report.feature`: a report gives each model arm its cost for each forecast and its latency
- added `features/reports/report.feature`: a comparison uses only the questions both arms have a score for
- added `features/reports/report.feature`: a comparison gives the mean difference, its interval and its count
- added `features/reports/report.feature`: a comparison says how far it can be trusted by its count
- added `features/reports/report.feature`: a comparison marked too few gives no difference
- added `features/reports/report.feature`: a comparison says which arm is better only when its interval leaves out nought
- added `features/reports/report.feature`: a comparison whose interval holds nought says no difference is shown
- added `features/reports/report.feature`: a calibration table gives each band its forecasts, how often yes happened and its interval
- added `features/reports/report.feature`: a calibration table has ten bands of equal width
- added `features/reports/report.feature`: a band with fewer than ten forecasts is marked too few
- added `features/reports/report.feature`: a report gives the scores by domain and by horizon
- added `features/reports/report.feature`: a question's horizon is the time from its forecast window to the time it closes
- added `features/reports/report.feature`: a report says what it leaves out
- added `features/reports/report.feature`: a report gives each arm its abstained and failed runs
- added `features/reports/report.feature`: a question in several cohorts is counted once in a report over them
- added `features/reports/report.feature`: a report asked for twice gives the same figures
- added `features/reports/report.feature`: a report names the scoring method, the cohort's model and the time it was made
- added `features/reports/report.feature`: a report with no scored question says nothing is known yet

---

### User Story 3 - Evidence packs and the evidence arm (Priority: P1)

When a cohort is frozen, the gatherer searches for each question and keeps what it finds as that
question's evidence pack: documents, each with its source, its address, the time it was published
and an excerpt, up to a limit, with the gatherer's version and the searches it made. A pack never
changes once gathered. The evidence arm is given what the model-alone arm is given and every
document of the pack. Every arm that takes evidence reads the same pack, none forecasts before the
pack exists, and on an empty pack they abstain.

**Why this priority**: the graph and prose arms both read the pack, and the evidence arm is the
baseline that says what the documents alone are worth. Without a fixed, shared pack no comparison
between arms that take evidence means anything.

**Independent Test**: freeze a cohort with the evidence arm against a stand-in search, read each
question's pack back with its documents and searches, forecast, and see each run name the pack and
hold the documents in what the model was given.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/evidence/pack.feature`: the gatherer makes one evidence pack for each question of a frozen cohort
- added `features/evidence/pack.feature`: a document is held with its source, its address, the time it was published and an excerpt
- added `features/evidence/pack.feature`: an evidence pack records the gatherer's version and the searches it made
- added `features/evidence/pack.feature`: an evidence pack does not change once it is gathered
- added `features/evidence/pack.feature`: no arm that takes evidence forecasts a question before its pack is gathered
- added `features/evidence/pack.feature`: every arm that takes evidence is given the same pack
- added `features/evidence/pack.feature`: a pack holds no more documents than the gatherer's limit
- added `features/evidence/pack.feature`: a question the search finds nothing for has an empty pack
- added `features/evidence/pack.feature`: an arm that takes evidence abstains on an empty pack
- added `features/evidence/pack.feature`: a search that fails leaves the question without a pack and says so
- added `features/arms/arms.feature`: the evidence arm is given what the model-alone arm is given and the evidence pack

---

### User Story 4 - The graph arm and its prose control (Priority: P1)

For each question of a cohort that names the graph arm or the prose arm, one appraisal reads the
evidence pack and records what it shows in the reasoning service: the question with its yes and no
hypotheses, and claims, each deriving from one document of the pack and taking one stance on the
yes hypothesis; and, held by the bench, the open points the evidence does not settle. A claim that
derives from no document of the pack fails the appraisal. The graph arm's model is then given the
case for, the case against and the open points; the prose arm's model is given the same claims,
stances, documents and open points as one passage in the order of the documents. Neither is given
anything the other is not. The graph arm's forecast is held in the reasoning service as a belief
resting on the claims it named as its reasons. The report leads with the comparison of the two.

**Why this priority**: this comparison is the reason the bench exists. The other arms say how good
a forecast is; this pair says whether structure is what made it so.

**Independent Test**: with a scripted model and a pack of four documents, forecast one question
with both arms; read what each model was given and check that every claim, stance, document and
open point in one is in the other; read the belief and its reasons back from the reasoning service.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/arms/appraisal.feature`: the appraisal is made once for each question of a cohort
- added `features/arms/appraisal.feature`: the appraisal records the question with a yes hypothesis and a no hypothesis in the reasoning service
- added `features/arms/appraisal.feature`: each claim derives from one document of the pack and takes one stance on the yes hypothesis
- added `features/arms/appraisal.feature`: the appraisal records the open points the evidence does not settle
- added `features/arms/appraisal.feature`: a claim that derives from no document of the pack fails the appraisal
- added `features/arms/appraisal.feature`: the graph arm and the prose arm abstain on a failed appraisal
- added `features/arms/appraisal.feature`: the graph arm is given the case for, the case against and the open points
- added `features/arms/appraisal.feature`: the prose arm is given the same claims, documents and open points as one passage
- added `features/arms/appraisal.feature`: the graph arm and the prose arm are given the same content
- added `features/arms/appraisal.feature`: the graph arm and the prose arm are given what the model-alone arm is given
- added `features/arms/appraisal.feature`: neither the graph arm nor the prose arm is given the documents themselves
- added `features/arms/appraisal.feature`: the graph arm's forecast is held in the reasoning service as a belief resting on its reasons
- added `features/arms/appraisal.feature`: the graph arm fails when the reasoning service cannot be reached
- added `features/arms/appraisal.feature`: the appraisal names the version of its instructions and its model
- added `features/arms/appraisal.feature`: the cost of the appraisal is counted once for each arm that reads it
- added `features/reports/report.feature`: the report leads with the comparison of the graph arm with the prose arm

---

### User Story 5 - Rehearsing on development questions (Priority: P2)

The experimenter imports questions a venue has already resolved, which are development questions
for ever, and makes a development cohort of them with a pretended time. Its packs hold only
documents published before that time. It is forecast, scored and reported like any cohort, so that
instructions, the gatherer and the appraisal can be worked on without waiting months, and without
touching a prospective question. Its report says on its face that it shows nothing, because the
model may know the outcomes, and no report over several cohorts counts it.

**Why this priority**: it is how the bench and its arms are developed. It is not evidence, and the
bench must make it impossible to pass off as evidence.

**Independent Test**: import resolved questions, forecast a development cohort, and see scores at
once; ask for a report over it and a prospective cohort together and see only the prospective one
counted.

**Acceptance Scenarios** *(each names a scenario in a living feature; none is written here)*:

- added `features/questions/import.feature`: a question its venue has already resolved is imported as a development question
- added `features/questions/import.feature`: a development question never becomes prospective
- added `features/cohorts/cohort.feature`: a prospective cohort refuses a development question
- added `features/cohorts/cohort.feature`: a development cohort refuses a prospective question
- added `features/evidence/pack.feature`: a development cohort's pack holds only documents published before its pretended time
- added `features/evidence/pack.feature`: a development cohort's pack holds no document without a time it was published
- added `features/reports/report.feature`: a report of a development cohort says on its face that it shows nothing
- added `features/reports/report.feature`: a report over several cohorts leaves out development cohorts

---

### Edge Cases

Each is a scenario, or a row of a `Scenario Outline`, named above.

- A venue changes a result after the bench has scored on it: the outcome is revised, the runs are
  scored again, and the earlier outcome and scores are kept.
- A forecast of exactly nought or one: the Brier score is taken as it is; the log score is taken at
  the scoring method's bound.
- Some repetitions of an arm fail and others give a forecast: the arm's forecast is the mean of
  those that gave one.
- The budget is spent, or the forecast window ends, with runs unmade: they are never made, the
  cohort says which, and comparisons use only the questions both arms forecast.
- A question is in two prospective cohorts: a report over both counts it once.
- A document of the pack has no claim deriving from it: neither the graph arm nor the prose arm is
  given it.
- The search finds nothing, or fails: an empty pack makes the arms that take evidence abstain; a
  failed search leaves no pack and no runs of those arms, and the cohort says why.
- The reasoning service cannot be reached: the appraisal fails, the graph arm's run fails, and both
  are recorded.

## Requirements *(mandatory)*

### Functional Requirements

**Questions**

- **FR-001**: The bench MUST import questions from public venues, holding for each its statement,
  its resolution criteria, the time it closes, its venue, its category and the domain the
  experimenter has named for that category, `other` where none is named.
- **FR-002**: A question open at its venue when first imported MUST be prospective and one already
  resolved MUST be a development question, and neither MUST ever become the other.
- **FR-003**: The bench MUST NOT hold a question with other than two outcomes or with no resolution
  criteria, and the import MUST say which questions were left out and why.
- **FR-004**: Importing MUST hold each question once however often it is imported, and a venue that
  cannot be read MUST leave everything held as it was.

**Cohorts**

- **FR-005**: A cohort MUST name its questions, its arms, one model, a forecast window, a number of
  repetitions and a budget, and MUST be either prospective or development, refusing a question of
  the other kind.
- **FR-006**: A cohort MUST be changeable while drafted and MUST NOT change once frozen; a freeze
  MUST be refused for a cohort with no questions, no arms, or a question that closes before its
  forecast window ends.
- **FR-007**: Runs MUST be made only for a frozen cohort and only within its forecast window, one
  for each arm, question and repetition.
- **FR-008**: A cohort MUST stop making runs when their cost reaches its budget, and MUST say which
  runs were not made, for that reason or because the window ended.
- **FR-009**: A question MAY be named by more than one cohort.

**Arms**

- **FR-010**: Every arm MUST answer under one contract: given a question and what its kind is given,
  it ends a run with a probability from nought to one, abstained with a reason, or failed with a
  reason. The contract MUST admit arms added later without a change to runs, scores or reports.
- **FR-011**: The venue-price arm's forecast MUST be the venue's probability observed in the
  forecast window, and it MUST abstain when the venue gives none.
- **FR-012**: The model-alone arm MUST give the model the question's statement, its resolution
  criteria, the time it closes and the time of the run, and nothing else.
- **FR-013**: The evidence arm MUST give the model what the model-alone arm gives and every document
  of the question's evidence pack.
- **FR-014**: No model arm MUST be given the venue's probability, and every model arm of a cohort,
  and the appraisal, MUST use the cohort's model.
- **FR-015**: An answer that is not a probability from nought to one, or a model that cannot be
  reached, MUST be recorded as a failed run with its reason; a failed run MUST be made again only
  when the experimenter asks and only within the forecast window, the failed run being kept; a run
  with a forecast MUST never be made again.

**Evidence**

- **FR-016**: When a cohort is frozen the gatherer MUST make one evidence pack for each of its
  questions by search: documents, each with its source, its address, the time it was published and
  an excerpt, up to the gatherer's limit, with the gatherer's version, each search made and the time
  it was gathered.
- **FR-017**: An evidence pack MUST NOT change once gathered, and every arm that takes evidence
  MUST read the same pack for a question in a cohort.
- **FR-018**: No arm that takes evidence MUST forecast a question before its pack is gathered; on an
  empty pack each MUST abstain; a search that fails MUST leave no pack and the cohort MUST say why.
- **FR-019**: A development cohort MUST name a pretended time, and its packs MUST hold only
  documents that state a time they were published and were published before it.

**The appraisal, the graph arm and the prose arm**

- **FR-020**: For each question of a cohort that names the graph arm or the prose arm, the bench
  MUST make one appraisal of the question's evidence pack, naming the version of its instructions
  and the cohort's model.
- **FR-021**: The appraisal MUST record in the reasoning service the question with a yes hypothesis
  and a no hypothesis, and claims each with a statement, the one document of the pack it derives
  from and one stance on the yes hypothesis; and MUST hold its open points.
- **FR-022**: An appraisal with a claim that derives from no document of the pack MUST fail, and
  the graph arm and the prose arm MUST abstain on a failed appraisal.
- **FR-023**: The graph arm MUST give the model what the model-alone arm gives, the case for the
  yes hypothesis, the case against it, each claim with its document, and the open points.
- **FR-024**: The prose arm MUST give the model what the model-alone arm gives and one passage
  saying each claim with its stance and its document, and each open point, in the order of the
  documents in the pack.
- **FR-025**: The graph arm and the prose arm MUST be given the same claims, stances, documents and
  open points, and nothing of a document except through a claim that derives from it.
- **FR-026**: The graph arm's forecast MUST be held in the reasoning service as a belief in the yes
  hypothesis resting on the claims the arm named as its reasons; when the reasoning service cannot
  be reached the run MUST be recorded as failed.
- **FR-027**: The cost of an appraisal MUST be counted in the cost of each run that reads it.

**Runs**

- **FR-028**: A run MUST be recorded once with its cohort, question, arm, repetition, model, the
  version of its instructions, its evidence pack, exactly what the model was given and what it
  answered, its forecast or the reason it has none, its cost, its tokens and its latency, dated by
  the bench.
- **FR-029**: A run MUST never be changed or removed.
- **FR-030**: No run MUST be recorded for a prospective question that has closed or has an outcome.
- **FR-031**: The experimenter MUST be able to read any run as recorded, read the history of a
  cohort in the order it happened, and reproduce a run from its record without anything being
  recorded as a run.

**Outcomes and scores**

- **FR-032**: The bench MUST read each question's outcome from its venue as yes, no or void, with
  the venue and the time it resolved and whether it is disputed, and MUST refuse an outcome stated
  by anyone else; a venue that cannot be read MUST leave outcomes as they were.
- **FR-033**: An outcome the venue changes, or settles after a dispute, MUST be held as a new
  outcome that revises the earlier, which is kept.
- **FR-034**: Each run with a forecast, on a question whose outcome is yes or no and not disputed,
  MUST be given a Brier score and a log score by a scoring method named by version; the log score
  of a forecast nearer an end than the method's bound MUST be taken at the bound.
- **FR-035**: Scores MUST be made again, and the earlier kept, when an outcome is revised or a new
  scoring method is applied.
- **FR-036**: An arm's forecast for a question MUST be the mean of its repetitions that gave a
  forecast, with their spread kept; an arm with none MUST have no score for that question.

**Reports**

- **FR-037**: A report of a cohort MUST give each arm its mean Brier score, mean log score and the
  number of questions scored; its counts of runs with a forecast, abstained and failed; and, for
  each model arm, its mean cost for each forecast, mean tokens and mean latency.
- **FR-038**: A comparison of two arms MUST use only the questions both have a score for and MUST
  give the mean difference of their Brier scores with its interval and its count; it MUST be marked
  `too few` under thirty questions and then give no difference, `preliminary` from thirty to
  ninety-nine, and `counted` from a hundred.
- **FR-039**: A comparison MUST say one arm is better only when its interval leaves out nought, and
  otherwise that no difference is shown.
- **FR-040**: A report MUST lead with the comparison of the graph arm with the prose arm and then
  compare every other pair of the cohort's arms.
- **FR-041**: A report MUST give each arm a calibration table of ten bands of equal width, each
  with its count, mean forecast, how often the outcome was yes and the interval of that, a band
  with fewer than ten forecasts marked `too few`.
- **FR-042**: A report MUST give each arm's scores by domain and by horizon (`days` up to seven,
  `weeks` up to twenty-eight, `months` beyond), each with its count.
- **FR-043**: A report MUST give the counts of questions scored, waiting, void and disputed, and
  with no scored question MUST say nothing is known yet.
- **FR-044**: A report of a development cohort MUST be marked as showing nothing, and a report over
  several cohorts MUST leave out development cohorts and say so, and MUST count a question in
  several of them once.
- **FR-045**: A report MUST name its scoring method, the cohort's model, each arm's instructions by
  version and the time it was made, and MUST give the same figures when asked for again with
  nothing recorded since.

### Key Entities *(each a term in the project glossary)*

- **Question**: what a venue asks, with two outcomes: statement, resolution criteria, the time it
  closes, venue, category, domain; prospective or development for good.
- **Cohort**: one experiment: questions, arms, model, forecast window, repetitions, budget;
  drafted then frozen; prospective or development, the latter with a pretended time.
- **Arm**: one way of answering: venue price, model alone, evidence, graph, prose. Model arms have
  instructions named by version.
- **Evidence pack**: the documents for one question in one cohort, with the gatherer's version and
  its searches. A **document** has a source, an address, a published time and an excerpt.
- **Appraisal**: one reading of a pack for a cohort: claims (statement, document, stance) held in
  the reasoning service, and open points held by the bench.
- **Run**: one arm's answer to one question in one cohort for one repetition: what was given, what
  was answered, a forecast or abstained or failed, cost, tokens, latency, the time recorded.
- **Outcome**: yes, no or void, from the venue, possibly disputed, possibly revised.
- **Score**: a Brier score and a log score for a run, by a scoring method, on an outcome.
- **Report**: for a cohort or several at a time: arm figures, comparisons with interval, count and
  standing, calibration tables, figures by domain and horizon, and what was left out.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An experimenter takes a venue's open questions to a frozen, forecast cohort of 100
  questions and five arms in one working session, without writing or editing a file by hand.
- **SC-002**: Every run of a cohort can be read back with exactly what the model was given and what
  it answered, and no run recorded can be shown to have changed: reading it at two times a month
  apart gives identical content.
- **SC-003**: For every pair of runs of the graph arm and the prose arm on the same question and
  repetition, the claims, stances, documents and open points given are identical, checked over
  every run of a cohort rather than sampled.
- **SC-004**: The report's Brier scores, log scores and calibration counts for a cohort of at least
  fifty scored questions agree with figures worked independently from the recorded runs and
  outcomes to four decimal places.
- **SC-005**: No report gives a difference between two arms without its interval and its count, and
  none gives one at all on fewer than thirty questions.
- **SC-006**: No figure in any report over prospective cohorts comes from a development question,
  and no forecast it counts was recorded after its question closed.
- **SC-007**: A reader who has not seen the bench can say from a cohort's report alone how many
  questions each figure rests on, which comparisons are not yet trustworthy, and what was left out.
- **SC-008**: Within a day of a venue resolving a question, its outcome is held and its runs scored
  without the experimenter doing anything.
- **SC-009**: A first prospective cohort of at least 200 questions is frozen and forecast by all
  five arms within its budget, with fewer than five in a hundred runs failed.
- **SC-010**: An arm added after this feature is forecast, scored and reported without a change to
  how runs, outcomes, scores or reports are held.

## Assumptions

- Questions have exactly two outcomes. Questions with several outcomes, numeric questions and
  questions resolved by anyone but their venue are out of scope here.
- The first venues are public prediction markets and forecasting sites whose questions, prices and
  results can be read without trading on them. Which are read first is settled in the plan, from
  what each allows; the features are written against any venue.
- One experimenter, on their own machine or one installation. Sign-in, several experimenters and
  sharing a report with a reader outside are later.
- A report is figures and plain statements that can be read as a page and as data. A designed
  investor document made from it is later.
- The interval of a comparison and of a calibration band is a 95% interval. The method (which kind
  of interval, how many resamples, the seed that makes a report repeatable) is the plan's, and is
  part of the scoring method's version.
- The standings (thirty, a hundred) say how far to trust a figure by its count. They are not a
  power analysis: how many questions are needed to show a difference of a given size is worked out
  from the first cohort's spread and recorded in `research.md`.
- A cohort names one model. Comparing models is done with two cohorts over the same questions.
- The appraisal is itself a model's work, shared by the graph and prose arms. Its quality limits
  both equally; whether a better appraisal changes the answer is a later cohort with a new version
  of its instructions.
- Open points are held by the bench because the reasoning service has no record for them; that it
  should is a request to it, kept in `notes/`.
- The evidence arm reads whole documents and the graph and prose arms read claims drawn from them,
  so evidence against graph measures appraisal plus structure, and only graph against prose
  measures structure alone. The report says so beside those comparisons.
- Development cohorts cannot be made safe from a model's knowledge of the outcome. They exist to
  exercise the bench; the pretended time limits what the pack leaks and nothing more.
- Retrieval by the arm itself, a knowledge graph arm, reasoning history, repeated forecasts over a
  question's life and accumulated experience (Hypothesis C) are later features. Each is a new arm
  or a new kind of cohort over what this feature holds.
- The bench asks nothing of the reasoning service that is particular to it or to any brand; what it
  lacks is requested and, until then, held by the bench.
