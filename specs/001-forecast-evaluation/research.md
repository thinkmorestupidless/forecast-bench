# Research: Forecast evaluation

Decisions made for the plan, numbered. Each says what was chosen, why, and what else was weighed.
Facts about ankka were read from `origin/main` of the platform (v0.18.0) and about the reasoning
service from its `origin/main` and tag `v0.1.1`; facts about the venues were read from their public
APIs on 2026-11-19.

## R1. One ankka service, one sbt module, ankka 0.18.0

**Decision.** `forecast-bench` stays the single service the template made, with packages
`forecastbench.{domain,application,api,venues,gather,reasoning,report}`. The pin moves from the
template's 0.17.0 to 0.18.0 in `build.sbt` and `service.json` together; `ankka-agent` and
`ankka-testkit` are added. sbt moves to 1.12.15, and `.scalafmt.conf`, the scalafmt plugin, the
pre-commit hook and the serialised test settings are copied from ankka-reasoning.

**Why.** The family pins the current release, and 0.18.0 adds only the OpenAPI document to what
0.17.0 has. The domain has no second consumer, so nothing earns a module of its own yet.

**Alternatives.** A pure `domain` module on `ankka-core` as ankka-predictions has: worth it when a
library is published; not here. Staying on 0.17.0: a pin behind on day one.

## R2. What is journaled: five event sourced entities

**Decision.** `question`, `cohort`, `pack`, `appraisal` and `run` are event sourced entities.
A fact written once is an entity with one founding event and no command that changes it: a `pack`
is `Gathered`, a `run` is `Recorded`, and each refuses a second founding command with `Conflict`.
What may happen later is a further event, never an edit: `OutcomeRead` on a question (each naming
the one it revises), `Scored` on a run (each naming its scoring method and the outcome it was made
from).

**Why.** The claim the bench exists to make is "this forecast was fixed before the outcome". ankka
never removes an event, sets the sequence and the time itself, and a view can be rebuilt from the
journal: the audit trail is the storage, with nothing to keep in step with it. FR-029 holds because
the entity has no command that could break it, which is stronger than a check.

**Alternatives.** Key value entities, as ankka-reasoning uses for text that must be withdrawable:
nothing here is a person's words and nothing needs withdrawing, and a key value entity keeps only
its latest value. One `cohort` entity holding its runs: one writer for a thousand runs and an
unbounded state.

## R3. What a model was given and answered lives in the object store, named by its digest

**Decision.** The exact text given to a model and the exact text it answered are written to the
bucket `given` as objects named by their SHA-256, before the run is recorded; the `Recorded` event
holds the two digests, the sizes and the parsed forecast. Documents' excerpts (at most 2,000
characters each, at most twelve to a pack) stay in the `pack`'s event.

**Why.** An evidence arm's input is tens of kilobytes; the platform's own guidance is that such
bytes belong in a bucket and the entity keeps the name. Naming by digest makes the journal
tamper-evident for the object: a changed object no longer matches the event. Reproducing a run
(FR-031) is reading the object and sending it again.

**Alternatives.** The whole text in the event: every view built from runs would carry it.
A separate table: nothing in ankka offers one.

## R4. Views are rows; every figure is computed in Scala from them

**Decision.** Views `questions`, `cohorts`, `packs`, `appraisals` and `runs` hold one row per
entity. A report reads a cohort's `runs` rows with a declared query (paged past the default
thousand) and computes every figure in `forecastbench.report`, a package of pure functions with no
ankka in it.

**Why.** A view's only aggregate is `count`; a report needs means, resampling and bands. Pure
functions over rows are what SC-004 checks against hand-worked figures, and they are tested with
plain munit.

**Alternatives.** Roll-up rows maintained by a keyed view: fixes the figures at write time, and a
new scoring method or interval method would mean a rebuild. A statistics library: the arithmetic is
a page, and a dependency would decide the method for us.

## R5. The forecast is two workflows: one per cohort, one per question

**Decision.** `cohort-forecast` (one per cohort) dispatches `question-forecast` workflows, at most
`bench.concurrency` (default 6) in flight, and pauses; each question workflow tells it when it
ends. `question-forecast` steps, for one question in one cohort: `gather` → `venue-price` →
`model-alone` → `evidence` → `appraise` → `graph` → `prose`, each arm step making that arm's runs
for every repetition, skipping an arm the cohort does not name. Before each model call the step
asks the cohort entity to reserve the estimated cost; a refusal (`budget spent`, `window ended`)
records a `RunNotMade` on the cohort and the workflow ends. Step timeout 5 minutes, no automatic
retry: a failure is a failed run, made again only when the experimenter asks (FR-015).

The budget counts what runs cost. An appraisal's cost is in the cost of each run that reads it
(FR-027), so a question with both the graph and the prose arm counts it twice against the budget:
the budget errs towards stopping early, never late. The gatherer's cost is the pack's and is
outside the budget; the cohort shows it beside what was spent.

Freezing starts the gather steps only (the pack exists when the cohort is frozen, FR-016);
`POST /cohorts/{id}/forecast` starts the arms.

**Why.** Two hundred questions, five arms and repetitions is thousands of model calls of up to a
minute each: it has to survive a restart and be bounded in parallel, and a workflow is the
platform's durable unit. Reserving on the cohort entity makes the budget a single writer's
decision, so two questions cannot both spend the last pound.

**Alternatives.** A blueprint (the appraiser's shape in ankka-reasoning): no branch on which arms
a cohort names, the model is a registered name per worker rather than the cohort's, and its
judgments need a second provider in early access. A consumer per run: no order between gather,
appraise and the arms that read them.

## R6. Models: named providers, a metering wrapper, instructions as versioned resources

**Decision.** `bench.models` in `application.conf` names each model the bench may use
(`name`, provider, model id, max tokens, thinking, effort, and its prices per million input and
output tokens with the date they were read). A cohort's model must be one of those names. One
agent, `forecaster`, takes a system text and a user text and replies as a typed answer. Every
provider is wrapped in `MeteredProvider`, a decorator on ankka's `ModelProvider` that records the
`TokenUsage` and elapsed time of each call against the call's session id; the step reads it after
the call. Each call has its own session (`run:<runId>`), with no memory.

Instructions are files, `src/main/resources/instructions/<arm>/<n>.md`, one directory each for
`model-alone`, `evidence`, `graph`, `prose`, `appraisal-reader`, `appraisal-open-points` and
`gatherer-queries`. A version is the file's number; its SHA-256 is recorded with it. A cohort fixes
the versions when it is frozen (the latest, unless named).

The typed answer is `{"probability": 0.62, "abstain": null, "reasons": ["c3","c5"], "rationale":
"..."}`: `probability` from nought to one or null with `abstain` a sentence; `reasons` used by the
graph arm only. A reply that does not decode, or decodes to a number outside nought to one, is a
failed run, "the answer was not a probability".

Thinking is on, effort as the model's entry says, and both are recorded in the run. Temperature is
not settable for Claude through ankka and is not used.

**Why.** Cost and latency must be per run (FR-028) and ankka puts usage on the session, not the
reply, with no latency anywhere but traces; a decorator on the one trait every provider implements
gives both for every call without depending on how session memory records usage. Prices in
configuration with their date are the only honest source: ankka has no price table.

**Alternatives.** A session per call and reading `history.usage`: depends on memory writing with
`MemoryProvider.none`, which nobody has tested. Instructions in code: a change would not have a
version a run can name. Instructions as entities registered over HTTP: a way to change what a
frozen cohort runs.

## R7. The appraisal is code calling a model, not a model calling tools

**Decision.** The `appraise` step, for a question's pack:

1. for each document, one model call (`appraisal-reader`) answering JSON: zero or more claims, each
   `{statement, stance, quote}`, the quote copied from the document;
2. the bench checks each claim: the quote is found in the document's excerpt (white space
   normalised), the stance is `supports` or `contradicts`, the statement is 1 to 1,000 characters.
   One failing claim fails the appraisal, naming it (FR-022);
3. one model call (`appraisal-open-points`) over the question and the checked claims, answering
   the open points;
4. the bench writes to the reasoning service (R8) and records `AppraisalMade` with the claim ids,
   the open points, the instruction versions, the usage and the cost.

A document that yields no claim is in neither arm's input (FR-025).

**Why.** The bench must be able to say that every claim derives from a document of the pack, and a
quote that can be found is checkable where a model's word is not. Writing the records in code makes
the link from claim to document a fact rather than a tool call that might name the wrong evidence.

**Alternatives.** Tools the model calls to record evidence and state claims, as the appraiser in
ankka-reasoning does: then the critique needs a second model or a judgment, and a refused tool call
leaves half an appraisal held. One call over the whole pack: cheaper, and loses which document a
claim came from.

## R8. The reasoning service: 0.1.1, its whole local stack, read from the graph

**Decision.** The bench calls the reasoning service by name (`services("reasoning")`), at release
0.1.1, whose belief layer is all it uses. For an appraisal it writes, in order:

| Record | Id | Notes |
|---|---|---|
| holder | `<cohort>.appraisal`, kind `model` | states the claims |
| holder | `<cohort>.graph.r<n>`, kind `model` | one for each repetition, holds the graph arm's belief |
| source | `src.<12 hex of the source's name>` | a document's source |
| question | `<cohort>.<qkey>` with hypotheses `yes` and `no` | `qkey` is 12 hex of the question's id; a cohort's id is at most 24 characters |
| evidence | assigned by the service (hash of source, locator, excerpt) | locator is the document's address |
| claim | `<cohort>.<qkey>.c<n>` | one stance, on `<question>/yes` |

The graph arm then calls `POST /graph/wait` for the last claim and reads
`GET /answers/case?hypothesis=<q>/yes&stance=supports` and `...&stance=contradicts`; what it gives
the model is rendered from those two replies. After the run is recorded it writes
`POST /beliefs/revisions` (`<cohort>.<qkey>.b<rep>`, the holder for that repetition, probability
the forecast, `restsOn` the claims named as reasons that are claims of this appraisal; others are
dropped and counted). A write repeated after a retry answers `200` with the record as held, so
every write is safe to send again.

Running a cohort needs the reasoning service's own Postgres, Kafka, Neo4j and the graph sink: a
compose profile `reasoning` in this repository starts them and the service on port 9100, from an
image built from the sibling checkout's tag by `scripts/reasoning-image.sh` (the package cannot be
pulled anonymously; voice does the same). The bench's Postgres and the reasoning service's are
separate databases.

The prose arm reads the same two replies and renders them as one passage in document order: both
arms render from one in-memory value, `AppraisalContent`, built from the graph's answer, so
"the same content" (FR-025) is one value rendered twice and is asserted as such in every run.

**Why.** The person chose the graph as the product reads it. Building both renderings from the one
value read back from the graph is what makes the difference between the arms shape and nothing
else, and it means a claim the graph lost would be lost to both.

**Alternatives.** Reading claims back one by one without the graph: no Kafka or Neo4j, and the
graph arm never touches a graph; declined by the person. The reasoning service's `main` (the
process layer, ankka 0.12.0): unreleased, and nothing here needs it. Recording the venue as a
`market` holder in the reasoning service, so `/answers/comparison` could set the graph arm against
the venue: attractive, a later feature; here it would put the venue's probability one call away
from the arm that must not see it.

## R9. Venues behind a port: Kalshi first, then Polymarket; not Metaculus

**Decision.** `Venue` is a trait: `open(filter)`, `resolved(filter)`, `one(id)`, each returning
`VenueQuestion` (id, event group, statement, criteria, closes at, category, outcomes, probability
with how it was read, result, disputed, resolved at). Two implementations on the JDK HTTP client:

- **Kalshi** (`/trade-api/v2`, no key): `market_type = binary` only; statement `title`, criteria
  `rules_primary` and `rules_secondary`; closes `close_time`; category from the event; event group
  `event_ticker`; result `yes`/`no` when `status = finalized`, void when the settlement value is
  neither nought nor one, disputed while `status` is `disputed` or `amended`. Markets settled
  before the historical cutoff are read from `/historical/markets/{ticker}`.
- **Polymarket** (Gamma `/markets/keyset`, no key): binary when `outcomes` decodes to exactly
  `["Yes","No"]`; statement `question`, criteria `description`; closes `endDate`; category the
  first tag; event group the event's id; result from `outcomePrices` when `closed` (`["1","0"]`
  yes, `["0","1"]` no, anything else void), disputed while `umaResolutionStatus` is `disputed`.

The venue's probability is the middle of the best bid and ask when both exist and are no more than
0.10 apart, else the last traded price when there is one, else none (the venue-price arm abstains).
The rule and which branch was taken are recorded in the run.

An import takes the venue and a filter: categories, a range for the time it closes, a least
volume, and `onePerEvent` (default true: of an event's markets, the one whose probability is
nearest a half), so a ladder of forty strikes is one question.

**Why.** Both venues answer market data without a key and were read from a UK address. Kalshi has
the cleaner contract (an explicit close time, rules, an enumerated result with a documented dispute
path), so it is first. Metaculus requires a token, hides the community figure and the resolution
from an ordinary account, and its terms forbid using its data to evaluate models without written
permission.

**Alternatives.** Importing every market of an event: a cohort of two hundred would be five
events, and their scores would move together. A venue's own client library: none on the JVM worth a
dependency.

## R10. Outcomes are polled; scoring follows the outcome

**Decision.** A recurring timer (`read-outcomes`, hourly) asks each venue for every question that
is in a frozen cohort and waiting or disputed, and sends what it reads to the question entity,
which records `OutcomeRead` only when it differs from the current outcome. A consumer on question
events scores the question's runs: for each run with a forecast, `Scored(method, outcomeSeq, brier,
log)` when the outcome is yes or no and not disputed. `POST /outcomes/read` does the same at once.
Scoring by another method is `POST /cohorts/{id}/score?method=<n>`.

**Why.** SC-008 asks for a day; an hour is cheap. The question owns its outcome because a question
may be in several cohorts. A consumer is at least once, and scoring is a pure function of the run
and the outcome, so a second delivery records nothing new (the run refuses a score it already has
for that method and outcome).

## R11. The scoring method and the intervals

**Decision.** Scoring method `1`:

- Brier score `(p − o)²` with yes as one; log score `−ln(p′)` of the probability given to what
  happened, with `p` held within `[0.01, 0.99]` for the log score only.
- An arm's forecast for a question is the mean of its repetitions that gave a forecast; its spread
  is their standard deviation.
- A comparison's interval is a 95% percentile bootstrap of the mean paired difference, 10,000
  resamples, resampling event groups rather than questions (questions of one venue event move
  together), seeded from the method, the cohorts and the two arms, so the same report is the same
  figures (FR-045). An arm's mean score has the same interval.
- A calibration band's interval is the 95% Wilson interval of the share of yes.
- Standings: under 30 `too few`, 30 to 99 `preliminary`, 100 or more `counted`; a band under 10
  forecasts `too few`.
- Over several cohorts a question counts once: the mean of its differences.

**Why.** The percentile bootstrap assumes nothing about the shape of Brier differences, which are
bounded and lumpy; resampling groups keeps a ladder of related questions from counting as
independent evidence. Wilson behaves at the ends, where the bands that matter most are.

**How many questions.** A first estimate, to be replaced by the first cohort's own spread: if the
paired difference in Brier score has a standard deviation near 0.10, showing a mean difference of
0.02 at the usual 80% power needs about 200 questions with both arms scored; a difference of 0.01
needs about 800. SC-009's first cohort of 200 can therefore show a large effect and bound a small
one, not find it.

**Alternatives.** A paired t interval: fine at a few hundred, and one more assumption to defend.
BCa: better coverage, harder to explain to a reader.

## R12. The gatherer: Exa behind a port, three searches, twelve documents

**Decision.** `Search` is a trait (`search(query, publishedBefore, limit)` returning results with
address, title, source, published time and text). Gatherer version `1`: three searches for a
question, its statement and two queries written by the cohort's model from the statement and the
criteria (`gatherer-queries`), ten results each from Exa with page text; results are merged by
address, ordered by the search's own rank interleaved across searches, cut to twelve, each
excerpt the first 2,000 characters of the text Exa returns. The venue's own pages are left out
(they show the venue's probability). For a development cohort the search is asked for results
published before the pretended time and any result with no published time, or a later one, is
dropped. The key is `EXA_API_KEY`; without it gathering fails and says so.

The pack records the gatherer's version, each query, each search's result addresses and the time.

**Why.** The person chose Exa: it returns text and a published date and filters by date. 2,000
characters is the reasoning service's excerpt limit, so a document's excerpt and its evidence are
the same text. The cost of the two query-writing calls is the gatherer's and is recorded on the
pack, outside any arm's cost.

**Verify.** The Exa API's current request and response shape, its date filter and its price, read
from its documentation at implementation; this entry was written from memory of it.

## R13. The HTTP surface is the only interface; `just` recipes are the experimenter's hands

**Decision.** Endpoints under `/venues`, `/questions`, `/cohorts`, `/runs`, `/outcomes`, all
`Acl.allowCallers(Callers.self)` (open on a laptop, closed once deployed). A report is
`GET /cohorts/{id}/report` (JSON) and `GET /cohorts/{id}/report.md` (the same figures as a page).
`Justfile` recipes wrap the calls (`just import kalshi ...`, `just cohort ...`, `just freeze`,
`just forecast`, `just report`). No web interface.

**Why.** One experimenter, one machine. The report as Markdown is readable as it is and pastes
into anything; the JSON is what a later page or investor document is drawn from.

## R14. Proof: every scenario through the real routes, with stand-ins at the edges

**Decision.** One `GherkinSuite` per feature file, steps driving the HTTP routes of a service
started once per test JVM by `AnkkaTestKit`, with `TestModelProvider` behind the metering wrapper,
a `ScriptedVenue`, a `ScriptedSearch`, a settable clock, the in-memory object store and
`ScriptedService` standing in for the reasoning service. The report package is also unit tested
against hand-worked figures. A live suite (skipped without `ANTHROPIC_API_KEY`, `EXA_API_KEY` and
a running reasoning stack) forecasts three real questions end to end and is run by hand.

**Why.** The features are written to be runnable against the whole system; the family's pattern
(ankka-reasoning's `FeatureSuite`) does exactly this. The clock must be settable because windows,
closing times and the pretended time decide refusals.

## R15. Time

**Decision.** One `Clock` is given to every component that reads the time; production is the
system clock. A run's time is the entity's, read from that clock when `Recorded` is persisted.
The forecast window is two instants fixed at freeze. "After it closes" compares the clock with the
question's close time as last read from the venue; the question is read again from the venue at the
start of its workflow, since venues close markets early.

## R16. Documents

**Decision.** This feature writes `README.md`, `CLAUDE.md` (the rules of the repository: nothing
changes a frozen cohort or a run; no model arm sees the venue's probability; a development figure
never enters a prospective report), `docs/limitations.md` (the honest list) and
`notes/reasoning-requests.md` and `notes/ankka-requests.md`. No docs site.

Requests found while planning, each with what it would remove:

- reasoning service: a record for an open point (the bench holds them itself); a namespace for
  ids (the bench prefixes by cohort); a release that can be pulled anonymously (the image script).
- ankka: token usage and elapsed time on a model reply (the metering wrapper); a price table or a
  cost hook; a timer at an instant rather than after a delay.

## Verify at implementation

- ankka 0.18.0 is on Maven Central with `ankka-agent` and `ankka-testkit`; else stay on 0.17.0.
- `ModelProvider` can be wrapped and the wrapper sees the session id of each call; if not, a
  session per call and `history.usage`, with latency timed in the step.
- An event of about 30 KB (a pack of twelve excerpts) is accepted without configuration; the
  default frame size for a command of that size.
- A declared view query can page past a thousand rows.
- `thenReplyAs` failing to decode surfaces as an error the step can catch and record as a failed
  run rather than failing the workflow.
- The reasoning service 0.1.1 accepts a question id of the planned form and answers
  `/answers/case` for a hypothesis with no claims with empty lists.
- Kalshi's representation of a void market (`result` and `settlement_value_dollars`) and
  Polymarket's (`outcomePrices` of a half each): both were inferred, not observed.
- Exa: as R12 says.
- The graph sink image runs under emulation on Apple silicon at a usable speed for 200 questions.

## For the person, before any figure is published

- Kalshi's Developer Agreement could not be read by the research and is reported to restrict
  storing and sharing API data; Polymarket's terms bar redistributing data to capital-market
  clients without agreement. Holding questions and scores privately is one thing; a report that
  quotes a venue's probabilities to an investor is another. Read both before the first report
  leaves the machine.

## Declined remediations

*(none)*

## Analysis

2026-11-19, by the session that designed the feature, reading the spec, the plan, the data model,
the contracts and the tasks against each other and against `features/`. Two findings, both
remedied without changing a decision or a scenario:

- MEDIUM: the contract gives a model the pretended time as `Now:` for a development cohort, while
  the spec said only "the time of the run". The spec's assumptions now say so.
- MEDIUM: nothing said whether the appraisal's and the gatherer's costs count against a cohort's
  budget. R5 now does.

Coverage: each of the 45 requirements is named by at least one scenario the spec lists (the
checker's `untraced-requirement` reports none), and each of the 137 scenarios is bound by a task
(T013, T019, T030 to T032, T038, T039, T045, T053, T055, T066, T067, T069, T070, T072).

This analysis was made by the designer and not by a reader with a fresh context; the review before
the pull request is the independent one.

## Found during implementation

- 2026-11-19 (T001): ankka 0.18.0 is on Maven Central with `ankka-agent` and `ankka-testkit`; the
  pin is 0.18.0.
- 2026-11-19 (T004): `Main.scala` starts `ProjectionRuntime` only. The HTTP server and
  `TimerRuntime` are added with the first endpoint (T012) and the outcome reader (T036), since
  each needs something to register.
- 2026-11-19: this machine's default JDK is 27, not the family's 21; the build compiles and its
  suite passes there, with deprecation warnings from the JDK about `sun.misc.Unsafe`. CI is on 21.
- 2026-11-19 (T013): 'a prospective question stays prospective after it resolves' needs a frozen
  cohort and the outcome reader; bound with T038.
- 2026-11-19 (T016): no keyed view cohort-questions: a cohort's row in the cohorts view names its
  questions, which is all the outcome reader needs; the scorer finds a question's runs in the runs
  view by question.
- 2026-11-19 (T018): a freeze is also refused with no budget ("the cohort has no budget"): FR-005
  says a cohort names one.
- 2026-11-19 (T019): the arms and runs features' background ("a frozen cohort ... and a prospective
  question") drafts the cohort with its question; the first step that forecasts adds the arms the
  scenario names and freezes it, since a frozen cohort could not be given them.
- 2026-11-19 (T015): `given` is a keyword in Scala 3, so the contract's method is written
  `` `given` ``; an arm answers its user text and the step gives the system text, the version of the arm's
  instructions the cohort fixed.
- 2026-11-19 (T023, T024): the forecaster is not an ankka agent. `ModelRequest` carries no session
  id, so a wrapper cannot attribute usage by session; and an agent's typed reply hides the exact
  text the bucket must keep. The steps call the cohort's model provider directly through
  `MeteredProvider`, which reads each reply's usage and times the call: the fallback of "Verify at
  implementation" without its dependence on session memory. A reply that does not decode, and a
  provider that errors or times out, are values the step records.
- 2026-11-19 (T026): a run's cost is reserved as an estimate: about three characters to a token of
  what the model is given, and two thousand tokens of answer, at the model's prices; settling
  replaces it with what the run cost. The venue-price arm costs nothing and reserves nothing. The
  question workflow makes one run a step, so no step outlives its five minutes whatever the
  repetitions; and its cohort workflow, since a command's state change is overwritten by a running
  step's, is only woken by a question's end and asks each question workflow itself, with a pause
  deadline of 20 seconds behind a lost wake.
- 2026-11-19 (T030): the budget is held by estimates, so questions forecast in parallel can each
  reserve under it while their runs, settled later, pass it: the overshoot is at most one run's cost
  for each question in flight (bench.concurrency). The scenario uses one question;
  docs/limitations.md says so.
- 2026-11-19 (T031): the forecast and retry routes read a cohort's runs from the run entities,
  attempt after attempt, not from the runs view, which may not yet hold the run that just failed.
- 2026-11-19 (T036): a timed action has no view client, so the outcome reader reads the questions a
  key value entity `watched` names, which the freeze route fills with the frozen cohort's questions.
  It reads every watched question, not only those waiting or disputed, so a venue's later change to
  a settled result is read and recorded as a revision (features/outcomes/outcome.feature).
- 2026-11-19 (T037): a consumer has no view client either, so a consumer of run events writes each
  run's id to a key value entity per question, `question-runs`, and scores the run at once if its
  question already has a settled outcome (a development cohort's runs come after their outcomes);
  the consumer of question events scores a question's runs from that list. The scoring methods are a
  value of the edges (`Edges.scoring`, method 1 only); the consumers and the score route compute a
  score and the run entity decides whether it may hold it. features/scoring/score.feature names a
  method 2, which the bench does not have: the suites' fixture adds one (method 1 with a log bound
  of 0.001) rather than the bench inventing a second method.
- 2026-11-19 (T038): GherkinSuite matches a step by its text whatever its keyword, so a state step
  that a feature writes both as a Given and a Then ("the outcome of \"rates\" is \"yes\"") arranges
  the state before the scenario's action and checks it after; every When step marks the action taken
  (test/steps/OutcomeSteps.scala).
- 2026-11-19 (T044): two endpoints cannot share a prefix (the server routes by it), so a cohort's
  report, comparison and calibration routes are the cohort endpoint's, built by `Reports`;
  `/reports` and `/reports.md` are endpoints of their own.
- 2026-11-19 (T045 is bound after T066): the report feature's background names the evidence, graph
  and prose arms, which the bench has from T046 and T056 on. `test/api/ReportRoutesSuite.scala`
  serves the routes on a model-alone cohort meanwhile.
- 2026-11-19 (T050, T053): every frozen cohort gathers (FR-016), whatever its arms, so every freeze
  fixes the version of gatherer-queries. The search is a value of the edges: Exa in the service, the
  scripted search in the suites.
- 2026-11-19 (T054): Exa's `POST /search` read from its documentation on 2026-11-19 (key in
  `x-api-key`; `endPublishedDate` for a pretended time; `contents.text.maxCharacters` for the
  excerpt). Exa names no source, so a document's source is its address's host. The replies in
  `src/test/resources/exa/` are written from the documented shape, not captured; no call has been
  made with a key (T079 is the first).
- 2026-11-19 (T056): Asked of the person. contracts/arm.md's prose sentence named no document, and
  free text with quotes, "; " or line breaks could not be read back, so `contentOf` could not
  recover what the contract says it does. The person chose: the prose sentence names the document as
  `[n]` after "According to", every source, statement, excerpt and open point is escaped (`\\`,
  `\"`, `\n`) in both renderings, and the prose's open points are each quoted. The contract is
  changed to say so. The property runs over 500 seeded random contents in munit; no property-testing
  library was added.
- 2026-11-19 (T061): the appraisal is made in round 0, after the gather, so it is once for a
  question by construction; the graph and prose steps wait for it as the evidence arm waits for its
  pack.
- 2026-11-19 (T063): an arm waits for its pack or its appraisal at most 300 times, 2 seconds apart
  (ten minutes), and is then not made, saying which it waited for; without a bound a round 0 that
  failed would leave its arms waiting for ever. The appraisal's cost is in each run that reads it
  (FR-027) and so in what the cohort has spent, once for each such run; the reservation includes it.
  A graph run's reasons that are not claims of its appraisal are dropped from the belief and the
  count noted on the cohort; a belief not written after one retry is noted on the cohort.
- 2026-11-19 (T064): the equality is checked before the second run's model is called, so a run that
  fails it costs nothing. Its failing path is exercised by a suite of the bench's own,
  `src/test/resources/features/arms/equality/equality.feature`, which makes the graph lose a claim
  between the two runs; no living scenario can.
- 2026-11-19 (T059): bodies sent to the reasoning service and to Exa leave an absent field out
  (`domain/Wire.scala`); ankka's `Codecs.make` writes it as `null`, which neither documents
  accepting.
- 2026-11-19 (T045): outcome reading of every watched question was serial (two entity calls each); a
  bench with thousands of watched questions outlasted the route's timeout. OutcomeReader now reads
  them on virtual threads, 32 at once.
- 2026-11-19 (T045/T067): the report scenarios' exact interval bounds (-0.031 to -0.004; -0.020 to
  0.011) cannot be produced through the routes, since the interval is drawn from the scores; the
  suite asserts the side of nought the bounds name and what the comparison says.
- 2026-11-19 (T073, Kalshi as read that day): prices are dollar strings (`yes_bid_dollars`,
  `yes_ask_dollars`, `last_price_dollars`, `volume_fp`); a bid of 0 and an ask of 1 are read as no
  order on that side, and a last price with no volume as no trade, so an untraded market gives no
  probability rather than 0. Combination markets (`KXMVE…`) are excluded with `mve_filter=exclude`.
  A missing market answers 404 at both `/markets/{t}` and `/historical/markets/{t}`; the venue then
  says none.
- 2026-11-19 (T074, Polymarket as read that day, against R9): tags come only with
  `include_tag=true`; the keyset cursor is sent back as `after_cursor`; the dispute field is
  `umaResolutionStatuses`, a JSON-encoded history whose last entry is read; `closedTime` is
  `2026-11-04 10:53:59.942479+00`, not ISO 8601. The first tag, which R9 makes the category, is
  sometimes a promotional label (`HFC`, `Earn 3.25%`); the experimenter's domain names map
  categories, so such a label is named there or left without a domain.
- 2026-11-19 (T078): the live suite is gated on FORECAST_LIVE=1 as well as on the two keys and the
  reasoning stack: it installs the real edges in Bench, which is one per JVM, so it must never run
  beside the feature suites' scripted bench in one `sbt test`. `just live` sets the gate and runs it
  alone.
- 2026-11-20 (T079, Kalshi): listing `/markets` and reading each market's event for its category
  made one request an event, and an import of settled markets outlasted the route's 20 seconds. The
  venue now lists `/events?status=open|settled&with_nested_markets=true`, 200 events a page, the
  category read from each event.
- 2026-11-20 (T079): an import's `limit` was applied by the venue to markets, before an event's
  markets were made one, so three markets of one event held one question. The endpoint now asks
  the venue for at least 500 and takes the limit after `onePerEvent`.
- 2026-11-20 (T079, decided by the person, contracts/arm.md changed): about one model reply in six
  wrote its reasoning before the JSON object it was asked for alone. A reply is now read as itself
  or, failing that, as its last JSON object, which must still be of the form; the reply is kept
  whole. The same holds for the appraisal's reader and open points and the gatherer's queries.
- 2026-11-20 (T079, decided by the person, contracts/reasoning.md changed): the graph arm was given
  10 of its appraisal's 13 claims. The graph topic has three partitions, and the sink had applied
  the last claim before three earlier ones, so a wait for the last claim alone was not enough. The
  bench waits for each claim and fails the read, as not caught up, unless the cases hold exactly
  the appraisal's claims. The equality check stays, for a claim revised between the two reads.
- 2026-11-20 (T079, Exa): twenty questions gathered at once drew `429` from Exa for seven packs.
  At most four searches are in flight from one bench, and a `429` or `5xx` is asked again after a
  pause of 1, 2, 4 and 8 seconds, or Exa's `Retry-After`, before the search fails.
- 2026-11-20 (T079, decided by the person): five of twenty appraisals failed whole on one claim each
  whose quote was not found, every one quoting speech from a document set with curly quotes. A
  quote is now matched with its typography plain on both sides (curly and angled quotes, primes,
  dashes, the ellipsis, non-breaking and thin spaces) as well as its white space; it is otherwise
  verbatim, and one claim that does not hold still fails the appraisal (FR-022). A failed appraisal
  keeps the reply it failed on in the bucket `given`, named on the failure as `answered`.
- 2026-11-20 (T079, decided by the person, contracts/reasoning.md changed): twenty appraisals written
  at once put about 260 claims through the sink, and three runs found the graph had not caught up
  within ten seconds; both graphs were complete minutes later. A read now has 30 seconds for all its
  waits, and a read not caught up is made again ten seconds later, three reads in all
  (`bench.graph-read`), inside the step's five minutes.
- 2026-11-20 (T079, the "Verify at implementation" list, settled against the real edges):
  - ankka 0.18.0 on Maven Central: yes (T001).
  - `ModelProvider` and the session id: no session id; the fallback is taken (T023, T024).
  - An event of about 30 KB: packs of up to 31 KB as served were held and read back without
    configuration in all three rehearsals.
  - A view query paging past a thousand rows: not observed. No view held a thousand rows in the
    rehearsals; the routes ask for explicit limits (100,000 questions, 500 a cohort's runs).
    Still open, for the first prospective cohort.
  - `thenReplyAs` failing to decode: moot. The forecaster is not an agent (T023), and a reply that
    does not decode is a value the step records.
  - The reasoning service 0.1.1: it accepts question ids of the planned form
    (`rehearsal-3.38bc882b0f92`), and answers `/answers/case` for a hypothesis with no claims with
    `{"claims":[],"revisedClaims":[]}`.
  - Void markets: still inferred, not observed. None of the questions imported resolved void.
  - Exa as R12: real searches on 2026-11-20 answered in the documented shape, read by the
    recorded-shape decoder. Its rate limit was not in R12 (see above).
  - The graph sink under emulation on Apple silicon: both the sink and the reasoning service are
    amd64 images. About 260 claims from twenty appraisals were applied within tens of seconds,
    slowly enough to need the retried read. Two hundred questions are not measured.

## Measurements

2026-11-20 (T079), three development cohorts of the same twenty Kalshi questions (Economics,
resolved before 2026-11-20, pretended time 2026-07-25), all five arms, one repetition, on
`claude-opus-5-5`. `rehearsal` and `rehearsal-2` found the faults recorded above.
`rehearsal-3`, run after the fixes, is the one measured.

| Arm | Forecasts | Cost a forecast (USD) | Tokens a forecast | Model call, mean |
|---|---|---|---|---|
| venue-price | 0 of 20: the venue gave no probability at the pretended time | 0 | 0 | none |
| model-alone | 20 of 20 | 0.0115 | 1,030 | 5.2 s |
| evidence | 20 of 20 | 0.0400 | 8,412 | 4.4 s |
| graph | 19 of 20: one appraisal failed on a true misquote | 0.4889 | 29,538 | 4.7 s |
| prose | 19 of 20: the same appraisal | 0.4907 | 29,802 | 5.0 s |

- The graph and prose costs each count the whole appraisal (FR-027), so the cohort spent
  $12.73 in all, less than the sum of its runs' costs, about $20.6. That is about $0.64 a
  question for all five arms; the appraisal is most of it.
- Forecasting twenty questions took 1 min 41 s from the forecast route to the last run recorded,
  packs gathered and appraisals made within it (`rehearsal-2`: 2 min 3 s).
- Brier scores over the twenty (17 resolved no, 3 yes): model-alone 0.0785, evidence 0.0735,
  graph 0.0731, prose 0.0740.
- The paired graph minus prose differences, nineteen pairs: mean -0.0009, standard deviation
  0.014, fourteen of them exactly nought, extremes -0.032 and +0.047.
- **What it implies for the first prospective cohort: little yet.** At that spread a difference of
  0.02 would need 4 questions and 0.01 would need 16, against R11's assumed 0.10 (about 200 and
  800). But the twenty are not like a prospective cohort. Twelve are one family of IPO-by-a-date
  questions that both arms put near nought, and on near-certain questions the two shapes cannot
  differ. The spread to plan on stays R11's 0.10 until a prospective cohort of uncertain questions
  gives its own; SC-009's 200 stands.

## Design finished

2026-11-19: analyze reported nothing above LOW.
