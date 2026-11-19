# Data model: Forecast evaluation

Five event sourced entities, five views, one bucket. Events and wire names are a persisted
protocol from the first cohort on: fields and cases are added, never renamed or removed. Times are
ISO-8601 instants in UTC. Money is US dollars as a decimal with six places.

## Identifiers

| Thing | Id | Example |
|---|---|---|
| question | `<venue>.<venue's id, lower case, characters outside [a-z0-9.-] as ->` | `kalshi.kxfed-26dec-t2.75` |
| cohort | chosen by the experimenter, 1 to 24 of `[a-z0-9-]`, starting with a letter | `november` |
| qkey | first 12 hex of SHA-256 of the question id | `3fa9c1d2e07b` |
| pack, appraisal | `<cohort>.<qkey>` | `november.3fa9c1d2e07b` |
| run | `<cohort>.<qkey>.<arm>.<repetition>.<attempt>` | `november.3fa9c1d2e07b.graph.1.1` |

Arms are `venue-price`, `model-alone`, `evidence`, `graph`, `prose` in ids; the features' values
("venue price", "model alone") are their names.

## question (event sourced, `ComponentId("question")`)

State: `id, venue, venueId, eventGroup, statement, criteria, closesAt, category, domain,
standing (prospective | development), importedAt, outcomes: List[Outcome]`.

`Outcome`: `seq, value (yes | no | void), disputed, resolvedAt, readAt, revises: Option[seq]`.
The current outcome is the last; the question is waiting when there is none.

| Command | Refused when | Event |
|---|---|---|
| `import(VenueQuestion, domain, now)` | not two outcomes; no criteria (the endpoint leaves these out and counts them; the entity refuses as a guard) | first time: `QuestionImported` (standing from whether the venue had resolved it); again: `QuestionRefreshed` only when the close time or the criteria differ; never changes standing |
| `readOutcome(value, disputed, resolvedAt, now)` | | `OutcomeRead` when it differs from the current outcome in value or disputed, naming the one it revises; otherwise nothing |
| `get` | | |

A development question's outcome arrives with its import, as `QuestionImported` then `OutcomeRead`.
There is no command by which anyone states an outcome: `readOutcome` is called only by the outcome
reader, and no route reaches it (FR-032).

## cohort (event sourced, `ComponentId("cohort")`)

State: `id, standing, model, questions: List[questionId], arms: List[arm], window: Option[(from,
to)], repetitions, budget, pretendedTime: Option, status (drafted | frozen), frozenAt,
instructions: Map[name, (version, digest)], gathererVersion, scoringMethod, spent, reserved,
notMade: List[(questionId, arm, repetition, reason)], packs: Map[questionId, gathered | empty |
failed(reason)], forecastStartedAt`.

| Command | Refused when | Event |
|---|---|---|
| `draft(id, standing, model, pretendedTime?)` | id held; model not configured; development without a pretended time, or prospective with one | `CohortDrafted` |
| `addQuestion(q, standing, closesAt)` | frozen; standing differs from the cohort's | `QuestionAdded` |
| `removeQuestion`, `addArm`, `removeArm`, `setWindow`, `setRepetitions`, `setBudget`, `setModel` | frozen; arm not one the bench knows; repetitions under 1; budget not positive | the matching event |
| `freeze(now, instructionVersions, gathererVersion)` | frozen; no questions; no arms; no window; a prospective question closing before the window ends (named) | `CohortFrozen` with everything fixed |
| `packGathered(q, result)` | not frozen | `PackNoted` |
| `startForecast(now)` | not frozen; outside the window | `ForecastStarted` (once) |
| `reserve(runId, estimate, now)` | budget spent; outside the window | `Reserved`, or `RunNotMade(reason)` and the refusal |
| `settle(runId, cost)` | | `Settled` (moves reserved to spent) |
| `get`, `history` | | |

`history` is the entity's own events with their times, joined by the endpoint with the times of the
cohort's packs and runs (FR-031).

## pack (event sourced, `ComponentId("pack")`)

One event, `PackGathered`: `cohort, question, gathererVersion, gatheredAt, searches: List[(query,
writtenBy (statement | model), results: List[address])], documents: List[Document], usage, cost`.

`Document`: `n` (its place in the pack, from 1), `source, address, publishedAt: Option, excerpt
(≤ 2,000 chars), title`.

`gather` is refused with `Conflict` when the pack exists (FR-017). An empty pack is a pack with no
documents. A failed search records nothing here; the cohort notes it.

## appraisal (event sourced, `ComponentId("appraisal")`)

One of two events, once: `AppraisalMade(cohort, question, model, readerVersion, openPointsVersion,
reasoningQuestion, claims: List[ClaimRef], openPoints: List[String], usage, cost, madeAt)` or
`AppraisalFailed(reason, naming, usage, cost, at)`.

`ClaimRef`: `id` (the reasoning service's), `document` (its `n`), `evidence` (the reasoning
service's id), `stance`, `statement`.

## run (event sourced, `ComponentId("run")`)

Founding event, once: `RunRecorded(cohort, question, arm, repetition, attempt, ending, recordedAt,
model: Option, instructions: Option[(version, digest)], pack: Option[packId], appraisal:
Option[appraisalId], given: Option[ObjectRef], answered: Option[ObjectRef], reasons: List[claimId],
usage: Option[TokenUsage], cost, appraisalShare, latencyMs, venue: Option[VenueReading])`.

`ending`: `Forecast(probability)`, `Abstained(reason)` or `Failed(reason)`.
`ObjectRef`: `sha256, bytes`. `VenueReading`: `probability, rule (mid | last), bid, ask, last,
observedAt`.

Later events: `RunScored(method, outcomeSeq, outcome, brier, log, at)`; refused when a score for
that method and outcome is held.

| Command | Refused when |
|---|---|
| `record(...)` | the run is recorded (`Conflict`); the question has closed or has an outcome and the cohort is prospective (the workflow checks first; the entity is told the question's state and refuses as the last guard) |
| `score(method, outcomeSeq, outcome)` | no forecast; outcome void or disputed; already scored so |
| `get` | |

No other command exists (FR-029).

## Views

| View | Source | Row | Asked by |
|---|---|---|---|
| `questions` | `question` events | the state without its outcome list, plus the current outcome | venue, standing, domain, waiting |
| `cohorts` | `cohort` events | id, standing, status, model, counts, spent | all |
| `packs` | `pack` events | cohort, question, counts, gatheredAt | cohort |
| `appraisals` | `appraisal` events | cohort, question, made or failed, counts | cohort |
| `runs` | `run` events | everything in `RunRecorded` but the object refs' content, plus current scores by method | cohort; cohort and arm; question |

A second keyed view, `cohort-questions`, has a row per (cohort, question) so the outcome reader and
the scorer find a question's cohorts and runs without scanning.

## Bucket `given`

Private. Objects named `<sha256>`, content type `text/plain; charset=utf-8`: what a model was
given (system text, a line of dashes, user text) and what it answered. Written before the run is
recorded; never deleted.

## Workflows, timers, consumers

- `cohort-forecast` (id the cohort's): state `pending, inFlight, done`; steps `dispatch`, paused
  between; command `questionEnded(q)`.
- `question-forecast` (id `<cohort>.<qkey>`): state which steps are done; steps as research R5.
  Two entries: `gather` (at freeze) and `forecast` (when asked).
- `read-outcomes` timed action, hourly; `score-on-outcome` consumer on `question` events.

## Report (computed, not held)

```text
Report
  cohorts, madeAt, scoringMethod, model, instructions, development: Boolean
  questions: scored, waiting, void, disputed
  arms: [ArmFigures]         arm, questions scored, mean Brier (+interval), mean log (+interval),
                              runs: forecast / abstained / failed, mean cost, tokens, latency
  comparisons: [Comparison]  a, b, count, standing, mean difference (a − b), interval, says
  calibration: [arm → 10 Bands]  from, to, count, mean forecast, share yes, interval, too few
  byDomain, byHorizon: [arm → key → (questions scored, mean Brier, mean log)]
  leftOut: development cohorts named, runs not made
  notes: what each comparison measures (graph − prose: structure; evidence − graph: appraisal and
         structure together; any arm − venue price: against the crowd)
```
