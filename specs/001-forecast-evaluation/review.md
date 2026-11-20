# Review: 001 forecast evaluation (main...HEAD on `001-forecast-evaluation`)

**Verdict:** Request changes
**What it does:** Builds the bench as one ankka service. It imports binary questions from Kalshi and
Polymarket, freezes them with five arms into cohorts, gathers an Exa evidence pack and an appraisal
for each question, makes and journals one run per arm, question and repetition inside a budget and a
window, reads outcomes from the venues, scores runs, and computes reports led by the graph against
prose comparison.
**Should it exist:** yes. It is the instrument spec 001 describes, the design is the one plan.md and
research.md argue for, and all 83 tasks in tasks.md are checked off, including the T079 rehearsal
against the real edges.
**Read:** spec.md, plan.md, tasks.md, research.md (the T079 additions in full, the rest by section),
contracts/arm.md; CLAUDE.md, README.md, docs/limitations.md, build.sbt, .scalafmt.conf, ci.yml,
.githooks/commit-msg, the PR template. In full: `domain/Cohort.scala`, `domain/Run.scala`,
`domain/Arm.scala`, `domain/Arms.scala`, `application/CohortEntity.scala`,
`application/CohortForecast.scala`, `application/QuestionForecast.scala`,
`application/RunEntity.scala`, `application/Forecaster.scala`, `application/Given.scala`,
`application/MeteredProvider.scala`, `application/ScoreOnOutcome.scala`,
`application/ReadOutcomes.scala`, `application/views/Runs.scala`, `api/CohortEndpoint.scala`,
`api/ReportEndpoint.scala`, `api/OutcomeEndpoint.scala`, `report/Report.scala`,
`report/Comparison.scala`, `report/Bootstrap.scala`, `report/Scoring.scala`,
`venues/Venue.scala`, `venues/VenueProbability.scala`. In part: `venues/Kalshi.scala` (the market
mapping), `gather/Exa.scala` (the retry), `reasoning/Appraiser.scala` (the model calls),
`application/Settings.scala`, `application.conf`, `domain/Pack.scala` (the venue-host filter). Tests
read for the findings below: `steps/ArmsFeatures.scala`, `steps/RecordFeatures.scala`,
`report/ReportSuite.scala`, `live/LiveSuite.scala`. Outside the diff: ankka's
`docs/build/workflows.md` (step recovery and failover). Fixtures checked:
`src/test/resources/kalshi/events-*.json`. Not read line by line: `domain/Shape.scala`,
`domain/Appraisal.scala` (quote matching), `reasoning/Reasoning.scala`, `venues/Polymarket.scala`,
`gather/Gatherer.scala`, `report/Markdown.scala`, `report/Calibration.scala`, the remaining views
and endpoints, most step suites and the feature files. A second reader for the reasoning-service
client and the quote matcher would be worth having.
**Size:** 192 files, +17,389 / -470, all hand-written. That is far past the size at which a review
finds defects reliably. As a greenfield feature built task by task, with one commit per task or
small task group, it can be read commit by commit, so I do not ask for a split now. A change of this
shape would split along tasks.md's phases (setup, questions and cohorts, each user story, the real
edges, polish).

## Blocking

- `src/main/scala/forecastbench/venues/Kalshi.scala:202` and `src/main/scala/forecastbench/domain/Arm.scala:53`: issue (blocking): every model arm is given the venue's name, which breaks the repository's second rule and contracts/arm.md ("nothing in any arm's text is ... the venue's name"). Kalshi's `rules_primary` and `rules_secondary` are joined into the question's criteria as they are, and `Arm.block` puts the criteria into every model arm's user text. The same block goes to the gatherer's query model and the appraisal reader. The recorded fixtures show the text carries the name: `events-settled.json` holds "…then the market resolves to Yes. Kalshi is not affiliated…", and the rules also link to `kalshi-public-docs…` addresses. The guard cannot catch this: `steps/ArmsFeatures.scala:15` looks for the lower-case string `"kalshi"` in text built from scripted criteria that never name the venue. A case-sensitive match would also miss the real "Kalshi". The same leak lets the gatherer write queries that name the venue, which pulls in pages about the market itself.
  Suggestion: remove venue names and any address on a venue host from the text a model is given, in one place, `Arm.block` or a function it calls, so that questions already in the journal are covered as well. Doing it at import alone would miss them. Then make the guard able to fail: render `Arm.block` from questions mapped from the recorded `events-settled.json` and Polymarket fixtures, and assert case-insensitively that no venue name or venue host appears. Do the same for the gatherer's and the reader's user texts.
  Resolved: `Arm.block` blinds the venues' names and leaves out their addresses for every model text (arms, gatherer, reader), which covers questions already held; the guard is case-blind and `KalshiSuite` renders the block from the recorded replies, which name the venue, dfaa6ec.

- `src/main/scala/forecastbench/report/Report.scala:194`: issue (blocking): the report counts the appraisal's cost twice in each graph and prose run's "cost for each forecast" (FR-037). `RunRecorded.cost` already includes the share: it is set to `forecast.cost + share` at `QuestionForecast.scala:285`, and `RecordFeatures.scala:163` asserts that. The report then adds `r.appraisalShare` again. No test can catch it: `ReportSuite.scala:140` checks only model-alone, whose share is nought, and `ReportFeatures.scala:206` checks only `> 0`.
  Suggestion: `armRuns.map(_.cost).sum / forecasts`, plus a `ReportSuite` case where a graph run has a non-zero `appraisalShare` and its expected cost per forecast is written out. Then check whether the T079 measurements table in research.md (graph $0.4889, prose $0.4907) was read from this route (see Questions).
  Resolved: the cost per forecast is `armRuns.map(_.cost).sum / forecasts`, with a `ReportSuite` case of graph runs holding a non-zero share (0.09 before, 0.05 now), 66a9d5a.

- `src/main/scala/forecastbench/application/QuestionForecast.scala:59-63` (with `:189-209`, `:254-290`) and `CohortForecast.scala:75-77`: issue (blocking): a model step can take longer than its 5-minute step timeout. Recovery is `maxRetries(0)` with no failover, and ankka's workflow docs say a step that fails past its retries fails the workflow. The arithmetic is in the code. `readContent` can take about 110 s (three reads with a 30 s wait limit each, plus two 10 s pauses), and the model call after it can take up to `Forecaster.Timeout` = 4 min. Together that is about 350 s against the 300 s timeout. Any non-`Conflict` `CommandError` from a component call also fails the step, since only `Conflict` is caught. When the step fails after `reserve`:
  - the reservation is never settled, so the cohort's budget shrinks for good;
  - the run is neither recorded nor noted as not made, which FR-008 and FR-031 need;
  - `QuestionForecastState.ended` never becomes true, so `CohortForecast.dispatchStep` keeps the question in `running` and pauses every 20 s indefinitely, and the round never ends.

  The appraise step has the same problem: the parallel reads (up to 3 min) and the open-points call (up to 3 min) run one after the other inside 5 minutes. The `MeteredProvider` doc comment states the intent ("never a thrown failure, so the step records the run as failed rather than failing the workflow"). The step timeout defeats it. The timing is shown by reading the code. What a failed workflow's `get` returns should be confirmed with a run.
  Suggestion: keep the bounded waits inside one step's timeout: reduce `Forecaster.Timeout` and the graph-read budget, or move the graph read into its own step. Then give `QuestionForecast` a `failoverTo` step that settles or releases the reservation in hand, notes the run as not made with the reason, and runs `finishStep`, so the parent always hears that the question ended. Add a feature scenario in which the scripted model does not answer within the step timeout and the round still ends.
  Resolved: each step's timeout covers the waits it bounds (model steps 7 min, appraisal 8, gathering 12); every step fails over to `abandon`, which records the run in hand as failed at what it reserved or notes it not made, holds a pack or appraisal as failed, and goes on; `FailoverFeatures` throws in a gathering and in a venue read and sees the round end (both fail without the failover), 935adaf. The model-step branch with a reservation in hand is not driven by a scenario: no scripted edge can fail that step, since the provider catches every non-fatal error.

## Should fix

- `docs/limitations.md` ("What is unproven"; "The venues"): issue (non-blocking): the honest list was not updated after T079 and now says things that are no longer true. "No cohort has been run against the real edges yet", "Exa's replies are written from its documentation" and "the cost and latency of each arm ... are estimates" were all overtaken by the rehearsal research.md records. It also leaves out three things the rehearsal found:
  - The venue-price arm abstains on every development question, because a settled market gives no probability (`Kalshi.scala:211`). That explains the "0 of 20" in the measurements table, where the wording "at the pretended time" suggests a different cause.
  - The appraisal counts against the budget once for each run that reads it, so a cohort stops early (research R5).
  - View paging past a thousand rows is still unverified.

  Suggestion: rewrite the "What is unproven" section from research.md's "Found during implementation" and "Measurements" sections, and add the venue-price entry under "Development cohorts show nothing".
  Resolved: "What is unproven" rewritten from the rehearsal; entries added for venue-price on development questions, the appraisal share in the budget, a failed step, the blinding, and the unobserved view paging, b8bdd67.

- `src/main/scala/forecastbench/api/CohortEndpoint.scala:270`: issue (non-blocking): `recorded` reads every run entity one at a time, and once more per run to find that the next attempt does not exist. It runs on the request thread for `/forecast`, `/retry-failed`, `/score` and `/history`. At the plan's scale (200 questions, five arms, three repetitions) that is at least 6,000 sequential entity calls per request. This is the pattern of the 2026-11-19 trap in CLAUDE.md ("Keep any route that walks every question bounded and parallel"). The rehearsal had 100 runs, so it could not show the problem.
  Suggestion: read the bounded, parallel way `OutcomeReader.readAll` does, or read the runs view with a fallback for a run that was only just recorded. Time `/forecast` against a seeded 3,000-run cohort.
  Resolved: `recorded` reads through `Bounded.map`, 32 at once, the helper `OutcomeReader` now uses too, with a `BoundedSuite` of its order and bound, 05f0212. Not timed against a seeded 3,000-run cohort.

- `src/main/scala/forecastbench/gather/Exa.scala:70`: issue (non-blocking): the `Retry-After` value is used with no upper bound. A reply of `Retry-After: 3600` would put the gathering step to sleep for an hour, far past its step timeout. Suggestion: `after.map(s => math.min(s, 30) * 1000)`, or treat a pause longer than that cap as a failed search.
  Resolved: a pause is at most `Exa.MaxPauseMs`, 10 s, whatever `Retry-After` asks, with a test of a `Retry-After: 3600`, 935adaf.

- Description: todo: there is no pull request yet. When it is opened, its description should follow `.github/pull_request_template.md` (Why / What changed / Left out / How it is tested / Risk and rollback, under about 300 words) and say where to start reading (`QuestionForecast.scala`, `Cohort.scala`, `Report.scala`), linking spec.md and research.md rather than restating them.
  Left: no pull request is raised from this run (the person chose to stop before it, with no remote); the body will follow the template when they raise it.

## Nits

- `src/main/scala/forecastbench/api/CohortEndpoint.scala:340`, `src/main/scala/forecastbench/report/Report.scala:190-191`: nitpick (non-blocking): `isInstanceOf[Ending.Failed]` / `[Ending.Abstained]`. A `match`, or an `isFailed`/`isAbstained` on `Ending`, keeps exhaustiveness in view.
  Resolved: `Ending.isAbstained` and `isFailed`, d2570a8.

- `src/main/scala/forecastbench/application/QuestionForecast.scala:156`: nitpick (non-blocking): `n.state == PackNote.Failed("").state` builds a value only to read a constant. Use a named constant, or better an enum for `PackNote.state`.
  Resolved: `PackNote.failed` against a named `FailedState`, d2570a8.

- `src/main/scala/forecastbench/application/RunEntity.scala:26-27`: nitpick (non-blocking): two `Left` branches with the same body; one is enough.
  Resolved: one `Left` branch, d2570a8.

- Commits `f8c05cf`, `7db36f0`, `f834dc9`, `7eb0ae6`, `b8b60e5`: nitpick (non-blocking): each body is one unwrapped line of several hundred characters, unlike the wrapped bodies of the earlier commits.
  Left: nit; those commits stay as they are (no rewriting history), and the review's fix commits wrap their bodies.

## Questions

- `src/main/scala/forecastbench/domain/Pack.scala:56`: question: the pack filter drops only pages on the venues' own hosts. A news page that quotes a market's odds ("traders on Kalshi put it at 62%") passes it and reaches the evidence, graph and prose arms. Is that inside the intent of rule 2 ("no model arm sees the venue's probability")? If not, it needs a limitations entry or a content filter. Searching the rehearsal's packs for the venue names would settle how often it happens.
  Answered: it was outside rule 2's intent, and the rehearsal showed it: one of 209 documents gave three arms Kalshi traders' odds. Decided by the person: gatherer version 2 leaves out any document whose title or excerpt names a venue, ee7b21f.

- `src/main/scala/forecastbench/api/ReportEndpoint.scala:80-82`: question: a report over several cohorts scores every cohort by the first cohort's scoring method. Today only method 1 exists. Should a report refuse cohorts frozen under different methods, or name the method each one used?
  Answered: one method's scores are not another's, so a report over cohorts frozen under different methods is refused with a 422 naming them; `Report.methodOf`, tested in `ReportSuite`, 66a9d5a.

- `specs/001-forecast-evaluation/research.md` (Measurements): question: were the "cost a forecast" figures for graph and prose read from the report route or worked out by hand? If they came from the route, the blocking finding on `Report.scala:194` means they are too high.
  Answered: from the route, so too high. Recomputed from the runs: graph $0.3069 and prose $0.3087 a forecast, each with a $0.182 share of the appraisal; the runs' costs add up to the $12.73 spent, b8bdd67.

## Praise

- `src/main/scala/forecastbench/application/RunEntity.scala:14-17`: praise: a run has `record`, `score` and `get` and nothing else, so the first rule holds because no command could change a run, not because a check refuses it. The same goes for every drafted-only decision in `Cohort.scala`, routed through one `drafted` guard.
- `src/main/scala/forecastbench/report/Report.scala:107-110`: praise: a report over several cohorts drops the development ones and names them in `leftOut`, and the bootstrap is seeded from the method, the cohorts and the arms. The third rule holds, and every figure can be reproduced from the journal.
- `src/main/scala/forecastbench/domain/Cohort.scala:263-289`: praise: the budget has a single writer, which reserves before the model is called. Two questions in flight cannot both spend the last of it, and research R5 says honestly that the budget errs towards stopping early.
- `specs/001-forecast-evaluation/research.md` ("Found during implementation"): praise: each fault the live rehearsal found is written down with its cause, its fix and who decided it, in the commit that fixed it. That is the traps rule followed properly.

## Out of scope

- note: `.specify/memory/constitution.md` is still the unfilled template. plan.md says so and checks against the family's principles instead. That is a family-wide choice, not this change's.

## Checks applied

- General checklist, applied: Correctness, Design and scope, Error handling, Concurrency and state, Security (entry points are `Callers.self`; no secrets in the diff; CI interpolates only SHAs), Data and migrations (new journal, so no evolution yet; wire names are explicit manifests), Performance, Tests, Observability (a skim), API and compatibility (no prior API; the template's `ItemEntity` removed with its tests), Dependencies and build (ankka 0.18.0 and the family's Pekko line; no new third-party libraries; munit and ujson scoped to Test), Documentation, Commit and PR hygiene. Skipped: none.
- Every-language documentation rule: not applicable. The repository is one Scala service with no SDKs, client libraries, per-language templates or docs site. No REVIEW.md, contributing page or docs check states an every-language rule (the only mention is the speckit-review extension's own conditional instruction under `.specify/extensions/review/`). The service documents itself in Scala and HTTP only, so no page, sample or limitations entry is missing for a supported language.
- Language skills: `pr-review-scala` (bundled; the repository has no `pr-review-scala` of its own), applied in its order: concurrency, serialization, types and totality, error handling, collections, tests, build, API, idioms. Dialect: Scala 3.9.0, `runner.dialect = scala3`.
- Project rules read: CLAUDE.md (the three rules and the traps), the workspace CLAUDE.md and the ankka brain's principles, plan.md's constitution check, contracts/arm.md, the PR template, `.githooks/commit-msg`. No REVIEW.md and no `.claude/rules/`.
- Enforced by tooling and not reviewed: formatting (scalafmt 3.9.4 with a pre-commit hook and `scalafmtCheckAll` in CI); unused code and discarded values (`-Wunused:all`, `-Wvalue-discard`, warnings only, not `-Werror`); Conventional Commit headers (hook and CI `commits` job; every header parses).
- Tests run: `sbt "testOnly forecastbench.domain.* forecastbench.report.* forecastbench.venues.*"`: 129 passed, 0 failed. Not run: the feature suites, which need Docker and take about ten minutes (`ReportFeatures` alone about six), and `just live`, which spends money and was excluded by instruction.
