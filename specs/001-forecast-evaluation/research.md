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

## Measurements

*(empty)*

## Design finished

2026-11-19: analyze reported nothing above LOW.
