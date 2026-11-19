# Tasks: Forecast evaluation

**Input**: Design documents from `specs/001-forecast-evaluation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: included. Every scenario in `features/` ends as a test through the real routes that
fails without the feature (research R14): one `GherkinSuite` for each feature file, its steps
written with the code they exercise. The pure packages (`domain`, `report`) are unit-tested with
no runtime.

**Organization**: by user story, after a foundational phase every story stands on. A task that
names scenarios binds their steps and leaves them green; a feature suite is complete when the last
task naming its file is done, and until then its unbound scenarios are tagged `@ignore` in the
suite's own filter, never in the feature file.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: US1 a cohort forecast and kept, US2 outcomes, scores and the report, US3 evidence
  packs, US4 the graph arm and its prose control, US5 development cohorts

Paths are from the repository root. `main/` is `src/main/scala/forecastbench/`, `test/` is
`src/test/scala/forecastbench/`, `res/` is `src/main/resources/`.

---

## Phase 1: Setup

**Purpose**: the family's build, and an empty service that tests can start.

- [X] T001 Move the pin to ankka 0.18.0 in `build.sbt` and `service.json` together (stay on 0.17.0 and record it in research.md "Found during implementation" if 0.18.0 is not on Maven Central); add `ankka-agent` and `ankka-testkit`; versions into `project/Dependencies.scala` (`object V`); sbt 1.12.15 in `project/build.properties`
- [X] T002 [P] Copy from `../ankka-reasoning`: `.scalafmt.conf`, the scalafmt plugin line into `project/plugins.sbt`, `.githooks/pre-commit`; add to `build.sbt` the serialised test settings (`Tags.limit(Tags.Test, 1)`, `Test / fork`, `parallelExecution := false`, `-Xmx2g`) and scalac `-deprecation -feature -Wunused:all -Wvalue-discard`
- [X] T003 [P] `.github/workflows/ci.yml`: job `build` runs `sbt scalafmtCheckAll scalafmtSbtCheck compile test`, job `features` runs the checker as `Justfile`'s `check` does, keeping the template's commit check
- [X] T004 Remove the template's sample: `main/domain/Item.scala`, `main/application/Item*.scala`, `main/api/ItemEndpoint.scala`, `test/Item*Suite.scala`; `main/Main.scala` registers nothing yet and starts `ProjectionRuntime`, `TimerRuntime`, the HTTP server
- [X] T005 [P] `main/domain/Clock.scala` (a trait with `now: Instant`, the system one) and `main/domain/Ids.scala` (question id, `qkey`, pack, appraisal and run ids as data-model.md; validation of a cohort id); `test/domain/IdsSuite.scala`
- [X] T006 `res/application.conf`: `bench.models` (one entry `claude-opus-5-5` with provider, model id, max tokens, thinking, effort, prices and their date), `bench.concurrency = 6`, `bench.limits` (documents 12, excerpt 2000, searches 3, results 10), `bench.venues`, `ankka.local-services.reasoning = "http://localhost:9100"`; `service.json` declares the bucket `given` (private); `main/application/Settings.scala` reads them, refusing a model with no price
- [X] T007 `test/support/TestClock.scala`; `test/steps/ServiceFixture.scala` (one service for the test JVM through `AnkkaTestKit.start` with `HttpServer.at("127.0.0.1", 0)`, the in-memory bucket, `TestModelProvider`, the settable clock, and places for the scripted venue, search and reasoning service); `test/steps/World.scala` and `test/steps/FeatureSuite.scala` (an abstract `GherkinSuite` with HTTP helpers, unique ids for each scenario, and a filter for scenario names not yet bound)

**Checkpoint**: `sbt test` green with no suites; `just run` serves `/_ankka/health`.

---

## Phase 2: Foundational (questions and cohorts)

**Purpose**: what every story reads: questions held from a venue and cohorts that freeze. No story
work begins before T019.

- [X] T008 [P] `main/venues/Venue.scala`: the port and `VenueQuestion`, `VenueFilter`, `VenueReading`, `VenueUnreadable` as contracts/ports.md; `main/venues/VenueProbability.scala`: the rule of research R9 (middle of bid and ask within 0.10, else last, else none) with `test/venues/VenueProbabilitySuite.scala`
- [X] T009 [P] `test/support/ScriptedVenue.scala`: a `Venue` the steps fill with open and resolved questions, results, disputes, cancellations and "cannot be read"
- [X] T010 [P] `main/domain/Question.scala`: state, `Outcome`, events `QuestionImported`, `QuestionRefreshed`, `OutcomeRead`, and the decisions (standing fixed at first import; an outcome recorded only when it differs, naming the one it revises); `test/domain/QuestionSuite.scala`
- [X] T011 `main/application/QuestionEntity.scala` (`ComponentId("question")`, commands `import`, `readOutcome`, query `get`, manifests as data-model.md) with `EventSourcedTestKit` cases in `test/application/QuestionEntitySuite.scala`; `main/application/views/Questions.scala` (row and the queries by venue, standing, domain, waiting)
- [X] T012 `main/api/VenueEndpoint.scala` and `main/api/QuestionEndpoint.scala`: `POST /venues/{venue}/import`, `PUT /venues/{venue}/domains` (kept in a key value entity `domains`), `GET /questions`, `GET /questions/{id}` as contracts/http.md, with `onePerEvent`; register in `main/Main.scala`
- [X] T013 `test/steps/ImportFeatures.scala` binds every scenario of `features/questions/import.feature` except the two of development questions (T069)
- [X] T014 [P] `main/domain/Cohort.scala`: state, events and decisions as data-model.md (drafted and frozen; refusals for a frozen cohort, a question of the other standing, an unknown arm, no questions, no arms, no window, a question closing before the window ends; `reserve` and `settle` against the budget and the window; `RunNotMade`); `test/domain/CohortSuite.scala` covering each refusal
- [X] T015 [P] `main/domain/Arm.scala` and `main/domain/Arms.scala`: the contract of contracts/arm.md and the five arms by name, with `given` for venue-price and model-alone only at this point
- [X] T016 `main/application/CohortEntity.scala` (`ComponentId("cohort")`) and `main/application/views/Cohorts.scala`, `main/application/views/CohortQuestions.scala` (keyed, a row for each cohort and question)
- [X] T017 `main/application/Instructions.scala`: reads `res/instructions/<name>/<n>.md`, the latest version and its SHA-256 for each name; `res/instructions/model-alone/1.md` written (a forecaster's instructions ending with the answer form of contracts/arm.md); `test/application/InstructionsSuite.scala`
- [ ] T018 `main/api/CohortEndpoint.scala`: `POST /cohorts`, `PUT /cohorts/{id}`, questions (by ids and by `select`), arms, `POST /cohorts/{id}/freeze` (fixing instruction versions, the gatherer's version and scoring method 1), `GET /cohorts/{id}`
- [ ] T019 `test/steps/CohortFeatures.scala` binds in `features/cohorts/cohort.feature`: a cohort names its questions, its arms, its model, its forecast window and its budget; the experimenter adds questions and arms to a drafted cohort; a frozen cohort cannot be changed; a cohort cannot be frozen with a question that closes before its forecast window ends; a cohort cannot be frozen with no questions or no arms; a question may be in a later cohort; and in `features/arms/arms.feature`: an arm the bench does not know is refused when added to a cohort

**Checkpoint**: questions are imported from the scripted venue and a cohort freezes and then refuses every change.

---

## Phase 3: User Story 1 - A cohort of prospective questions, forecast and kept (Priority: P1) 🎯 MVP

**Goal**: the venue-price and model-alone arms forecast a frozen cohort, and every run is kept
with what it came from and cannot change.

**Independent Test**: spec.md, User Story 1.

- [ ] T020 [P] [US1] `main/domain/Answer.scala`: the typed answer and how a reply ends a run (the table of contracts/arm.md); `test/domain/AnswerSuite.scala` with each row, including `1.4`, `-0.2` and `likely`
- [ ] T021 [P] [US1] `main/domain/Run.scala`: `RunRecorded`, `RunScored`, `Ending`, `ObjectRef`, the decisions (recorded once; no record for a prospective question that has closed or has an outcome; a score only for a forecast, once for a method and an outcome); `test/domain/RunSuite.scala`
- [ ] T022 [P] [US1] `main/application/Given.scala`: writes and reads the bucket `given` by SHA-256, and checks a read against its digest; `test/application/GivenSuite.scala` on the kit's in-memory store
- [ ] T023 [US1] `main/application/MeteredProvider.scala` (the wrapper of research R6: tokens and elapsed time for each call, by session id) and `main/application/Models.scala` (providers by name from `bench.models`, cost from tokens and the model's prices); `test/application/MeteredProviderSuite.scala`. If the wrapper cannot see the session id, fall back as "Verify at implementation" says and record which in research.md
- [ ] T024 [US1] `main/application/Forecaster.scala`: the agent `forecaster` (system text, user text, the cohort's model, no memory, a session for each call) replying as the typed answer, with a decode failure and a provider failure returned as values the step records
- [ ] T025 [US1] `main/application/RunEntity.scala` (`ComponentId("run")`, commands `record`, `score`, query `get`, nothing else) with `test/application/RunEntitySuite.scala` (a second `record` is refused; there is no command that changes a forecast); `main/application/views/Runs.scala` with paged declared queries by cohort, by cohort and arm, by question
- [ ] T026 [US1] `main/application/QuestionForecast.scala`: the workflow for one question in one cohort with the steps `venue-price` and `model-alone` (reads the question from the venue again first; for each repetition reserves on the cohort, builds `Given`, writes it to the bucket, calls the forecaster, records the run, settles the cost; a refusal to reserve ends it); step timeout 5 minutes, no retry
- [ ] T027 [US1] `main/application/CohortForecast.scala`: the workflow for a cohort (`dispatch` up to `bench.concurrency` question workflows, pause, `questionEnded`); `POST /cohorts/{id}/forecast` in `main/api/CohortEndpoint.scala` with its refusals (not frozen; outside the window, with `notMade`)
- [ ] T028 [US1] `POST /cohorts/{id}/retry-failed` in `main/api/CohortEndpoint.scala`: a new attempt for each failed run with no later forecast, within the window; a run with a forecast is never made again
- [ ] T029 [US1] `main/api/RunEndpoint.scala`: `GET /runs`, `GET /runs/{id}` (with `given`, `answered` and `intact`), `POST /runs/{id}/reproduce`; `GET /cohorts/{id}/history` in `main/api/CohortEndpoint.scala` (the cohort's events joined with its packs' and runs' times, in order)
- [ ] T030 [US1] `test/steps/CohortFeatures.scala` binds the rest of `features/cohorts/cohort.feature` but the two of development cohorts: no arm forecasts a cohort that is not frozen; each arm forecasts each question once for each repetition; a cohort stops making runs when its budget is spent; a run not made within the forecast window is never made
- [ ] T031 [US1] `test/steps/ArmsFeatures.scala` binds every scenario of `features/arms/arms.feature` that names only the venue-price and model-alone arms (an arm answers a question with a probability; both venue-price scenarios; the model-alone arm is given the statement, the resolution criteria and the time the question closes; a model arm's instructions are named by version in each run; an arm may abstain and say why; an answer that is not a probability is a failed run; a model that cannot be reached is a failed run; a failed run is made again only when the experimenter asks, and both runs are kept; a run with a forecast is never made again)
- [ ] T032 [US1] `test/steps/RecordFeatures.scala` binds every scenario of `features/runs/record.feature` whose arm is model-alone, and the history and reproduce scenarios once T029 is done; the scenarios naming the evidence or graph arm wait for T055 and T066

**Checkpoint**: MVP. A cohort of scripted questions is frozen and forecast by two arms; sixty runs read back; a `PUT` to a run is refused.

---

## Phase 4: User Story 2 - Outcomes, scores and an honest report (Priority: P1)

**Goal**: outcomes read from venues, runs scored, and a report that says what it knows.

**Independent Test**: spec.md, User Story 2.

- [ ] T033 [P] [US2] `main/report/Scoring.scala`: Brier score, log score with the bound, an arm's forecast for a question as the mean of its repetitions with their spread, scoring method `1` as a value; `test/report/ScoringSuite.scala` with every row of `features/scoring/score.feature`'s examples
- [ ] T034 [P] [US2] `main/report/Bootstrap.scala`: the 95% percentile bootstrap of a mean, 10,000 resamples, resampling groups, seeded (research R11); `test/report/BootstrapSuite.scala`: the same seed gives the same interval; a constant sample gives a point; coverage on a known distribution within tolerance
- [ ] T035 [P] [US2] `main/report/Wilson.scala` with `test/report/WilsonSuite.scala` against worked values
- [ ] T036 [US2] `main/application/ReadOutcomes.scala`: the timed action (hourly, scheduled at start) that reads each venue for the questions of frozen cohorts that are waiting or disputed and sends what it reads to the question; `main/api/OutcomeEndpoint.scala`: `POST /outcomes/read`; a venue that cannot be read is named and changes nothing
- [ ] T037 [US2] `main/application/ScoreOnOutcome.scala`: the consumer on `question` events that scores a question's runs by its cohorts' scoring method, and again on a revised outcome; `POST /cohorts/{id}/score?method=n` in `main/api/CohortEndpoint.scala`
- [ ] T038 [US2] `test/steps/OutcomeFeatures.scala` binds every scenario of `features/outcomes/outcome.feature` (the experimenter cannot state an outcome: `POST /questions/{id}/outcome` answers 405)
- [ ] T039 [US2] `test/steps/ScoreFeatures.scala` binds every scenario of `features/scoring/score.feature`
- [ ] T040 [P] [US2] `main/report/Comparison.scala`: the questions both arms have a score for, the mean difference, its interval by event group, its count, its standing, and what it says (better only when the interval leaves out nought); several cohorts counting a question once; `test/report/ComparisonSuite.scala` including the rows 12, 29, 30, 99, 100
- [ ] T041 [P] [US2] `main/report/Calibration.scala`: ten bands of equal width, each with its count, mean forecast, share of yes and Wilson interval, `too few` under ten; `test/report/CalibrationSuite.scala`
- [ ] T042 [US2] `main/report/Report.scala`: the value of data-model.md built from run rows, questions and cohorts (arm figures with counts of runs by ending, cost, tokens, latency; comparisons with graph and prose first where both are named; calibration; by domain and by horizon with the horizon's bounds; scored, waiting, void and disputed; `nothing is known yet`; the notes on what each comparison measures); `test/report/ReportSuite.scala` against a fixture of fifty questions worked by hand to four places (SC-004), and that the same input gives the same figures
- [ ] T043 [P] [US2] `main/report/Markdown.scala`: the report as a page, every figure with its count, every comparison with its interval and standing, a "not yet known" section; `test/report/MarkdownSuite.scala` (no difference is printed for `too few`)
- [ ] T044 [US2] `main/api/ReportEndpoint.scala`: `GET /cohorts/{id}/report`, `/report.md`, `/comparison`, `/calibration`, `GET /reports`, `/reports.md`
- [ ] T045 [US2] `test/steps/ReportFeatures.scala` binds every scenario of `features/reports/report.feature` but the two of development cohorts (T072) and the one that leads with graph and prose (T067); the steps seed runs and outcomes through the routes and the scripted venue

**Checkpoint**: on a forecast cohort, resolving questions at the scripted venue gives scores and a report whose figures match the hand-worked fixture.

---

## Phase 5: User Story 3 - Evidence packs and the evidence arm (Priority: P1)

**Goal**: a frozen cohort's questions each get one pack by search, and the evidence arm reads it.

**Independent Test**: spec.md, User Story 3.

- [ ] T046 [P] [US3] `main/gather/Search.scala` (the port of contracts/ports.md) and `test/support/ScriptedSearch.scala` (results by query, "finds nothing", "fails", results with and without a published time)
- [ ] T047 [P] [US3] `main/domain/Pack.scala`: `PackGathered`, `Document`, and the pure merge of research R12 (by address, interleaved by rank, the venue's own pages left out, cut to the limit, excerpts cut to the limit, and for a pretended time only documents published before it); `test/domain/PackSuite.scala`
- [ ] T048 [US3] `res/instructions/gatherer-queries/1.md`; `main/gather/Gatherer.scala`: gatherer version `1` (the statement and two queries the cohort's model writes, each search recorded with its result addresses, usage and cost on the pack); `test/gather/GathererSuite.scala` with the scripted search and model
- [ ] T049 [US3] `main/application/PackEntity.scala` (`ComponentId("pack")`, `gather` once, `get`) and `main/application/views/Packs.scala`; `test/application/PackEntitySuite.scala` (a second `gather` is refused and the pack is as it was)
- [ ] T050 [US3] The `gather` step in `main/application/QuestionForecast.scala` and its entry at freeze: `main/api/CohortEndpoint.scala`'s freeze starts gathering for every question, the cohort notes each pack as gathered, empty or failed with the reason; the arm steps wait for the pack
- [ ] T051 [US3] `res/instructions/evidence/1.md`; the evidence arm's `given` in `main/domain/Arms.scala` (the documents block of contracts/arm.md) and its step in `main/application/QuestionForecast.scala`, abstaining on an empty pack
- [ ] T052 [US3] `GET /cohorts/{id}/packs/{question}` in `main/api/CohortEndpoint.scala`
- [ ] T053 [US3] `test/steps/PackFeatures.scala` binds every scenario of `features/evidence/pack.feature` but the two of development cohorts (T070), binding "every arm that takes evidence is given the same pack" for the evidence arm now and for graph and prose in T066
- [ ] T054 [P] [US3] `main/gather/Exa.scala`: the `Search` on Exa's API, written from its current documentation (research R12 "Verify"), key from `EXA_API_KEY`, a failure when there is none; `test/gather/ExaSuite.scala` decoding recorded replies in `src/test/resources/exa/`
- [ ] T055 [US3] `test/steps/ArmsFeatures.scala` and `test/steps/RecordFeatures.scala` bind the scenarios that name the evidence arm (the evidence arm is given what the model-alone arm is given and the evidence pack; a run is recorded with everything its answer came from)

**Checkpoint**: freezing gathers packs from the scripted search; the evidence arm's runs name the pack and hold its documents in what the model was given.

---

## Phase 6: User Story 4 - The graph arm and its prose control (Priority: P1)

**Goal**: one appraisal for each question, held in the reasoning service; two arms given the same
content in two shapes; the report leads with their comparison.

**Independent Test**: spec.md, User Story 4.

- [ ] T056 [P] [US4] `main/domain/Shape.scala`: `AppraisalContent`, `ClaimGiven`, the graph rendering, the prose rendering and `contentOf` exactly as contracts/arm.md; `test/domain/ShapeSuite.scala`: a property over generated contents (including statements and excerpts with quotes, brackets, semicolons and line breaks) that `contentOf` of each rendering equals the content; that the prose has no heading, list or blank line; that the graph groups by stance
- [ ] T057 [P] [US4] `main/domain/Appraisal.scala`: `AppraisalMade`, `AppraisalFailed`, `ClaimRef`, and the check of a reader's claim (the quote is found in the document's excerpt with white space normalised; the stance is one of two; the statement's length), failing on the first that does not hold and naming it; `test/domain/AppraisalSuite.scala`
- [ ] T058 [P] [US4] `test/support/ScriptedReasoning.scala`: a `ScriptedService` holding what it is sent and answering `/graph/wait` and `/answers/case` from it, with "cannot be reached" and refusals
- [ ] T059 [US4] `main/reasoning/Reasoning.scala`: the client of contracts/reasoning.md over `services("reasoning")` (holders, question, sources, evidence, claims, wait, the two cases, the belief), each failure a value naming unreachable or the refusal's rule; `test/reasoning/ReasoningSuite.scala` against the scripted service, including a write sent twice
- [ ] T060 [US4] `res/instructions/appraisal-reader/1.md` and `res/instructions/appraisal-open-points/1.md`; `main/reasoning/Appraiser.scala`: the four steps of research R7 for one pack, returning made (claims, open points, usage, cost) or failed
- [ ] T061 [US4] `main/application/AppraisalEntity.scala` (`ComponentId("appraisal")`, once) and `main/application/views/Appraisals.scala`; the `appraise` step in `main/application/QuestionForecast.scala`, run when the cohort names the graph or the prose arm, once for the question
- [ ] T062 [US4] `res/instructions/graph/1.md` and `res/instructions/prose/1.md` (the same words but for the one sentence that says how the material is laid out); the graph and prose arms' `given` in `main/domain/Arms.scala` from one `AppraisalContent` read back through `Reasoning`'s two cases
- [ ] T063 [US4] The `graph` and `prose` steps in `main/application/QuestionForecast.scala`: abstain on an empty pack or a failed appraisal; each run's cost includes the appraisal's; the graph run's `reasons` kept; after a graph run with a forecast is recorded, the belief written resting on the reasons that are claims of this appraisal; the reasoning service unreachable is a failed graph run
- [ ] T064 [US4] The equality of contracts/arm.md checked when the second of a pair of graph and prose runs is recorded, in `main/application/QuestionForecast.scala`: unequal content fails that run with `the two shapes did not hold the same content`
- [ ] T065 [US4] `GET /cohorts/{id}/appraisals/{question}` in `main/api/CohortEndpoint.scala`
- [ ] T066 [US4] `test/steps/AppraisalFeatures.scala` binds every scenario of `features/arms/appraisal.feature`; `test/steps/ArmsFeatures.scala`, `test/steps/PackFeatures.scala` and `test/steps/RecordFeatures.scala` bind what waited for these arms (no model arm is given the venue's probability; every model arm of a cohort uses the cohort's model; every arm that takes evidence is given the same pack; a run records its cost, its tokens and its latency)
- [ ] T067 [US4] `test/steps/ReportFeatures.scala` binds: the report leads with the comparison of the graph arm with the prose arm

**Checkpoint**: all five arms forecast a scripted cohort; for every pair of graph and prose runs the content is equal; the scripted reasoning service holds a belief with its reasons.

---

## Phase 7: User Story 5 - Rehearsing on development questions (Priority: P2)

**Goal**: development cohorts run end to end at once and can never be read as evidence.

**Independent Test**: spec.md, User Story 5.

- [ ] T068 [US5] Import of resolved questions in `main/api/VenueEndpoint.scala` (`"standing": "development"` reads `Venue.resolved`, holds each as a development question with its outcome)
- [ ] T069 [US5] `test/steps/ImportFeatures.scala` binds: a question its venue has already resolved is imported as a development question; a development question never becomes prospective. `test/steps/CohortFeatures.scala` binds: a prospective cohort refuses a development question; a development cohort refuses a prospective question
- [ ] T070 [US5] The pretended time through `main/gather/Gatherer.scala` (asked of the search and applied to its results) and through every arm's `Now:` line in `main/domain/Arms.scala`; a development cohort's runs are recorded though its questions have outcomes; `test/steps/PackFeatures.scala` binds the two scenarios of a development cohort's pack
- [ ] T071 [US5] `main/report/Report.scala` and `main/report/Markdown.scala`: a development cohort's report marked `development: the model may know these outcomes` at its head; a report over several leaves development cohorts out and names them
- [ ] T072 [US5] `test/steps/ReportFeatures.scala` binds: a report of a development cohort says on its face that it shows nothing; a report over several cohorts leaves out development cohorts

**Checkpoint**: every scenario of `features/` is bound; `FeatureSuite`'s filter of unbound names is empty and removed.

---

## Phase 8: The real edges

**Purpose**: the venues, the reasoning service's stack, and a run against real models.

- [ ] T073 [P] `main/venues/Kalshi.scala`: the `Venue` of research R9 on the JDK HTTP client (open and settled markets with cursors, category from the event, the historical fallback, void and disputed), `test/venues/KalshiSuite.scala` decoding replies recorded in `src/test/resources/kalshi/` (an open binary market with its event, a finalised one, a scalar one left out)
- [ ] T074 [P] `main/venues/Polymarket.scala`: the `Venue` on Gamma's keyset routes (the JSON-encoded `outcomes` and `outcomePrices`, binary only, the first tag as category, the event as group, `closedTime`'s format, disputed from `umaResolutionStatus`); `test/venues/PolymarketSuite.scala` on recorded replies in `src/test/resources/polymarket/`
- [ ] T075 `main/Main.scala` wires the real venues, Exa, the reasoning client and the models from settings, and the scripted ones stay in `test/`; a missing key starts the service and fails the call that needs it, naming the key
- [ ] T076 [P] `scripts/reasoning-image.sh` (builds `ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1` from `../ankka-reasoning` at its tag, as voice's script does) and the `reasoning` profile in `docker-compose.yml` (the reasoning service on 9100 with its own Postgres, Kafka with the `reasoning-graph` topic, Neo4j, the graph sink image)
- [ ] T077 [P] `Justfile` recipes, each one command: `reasoning-up`, `reasoning-down`, `import`, `cohort`, `add`, `arms`, `settings`, `freeze`, `forecast`, `retry`, `runs`, `report`, `outcomes`
- [ ] T078 `test/live/LiveSuite.scala`: skipped without `ANTHROPIC_API_KEY`, `EXA_API_KEY` and the reasoning stack; imports three development questions from Kalshi, forecasts them with all five arms, and checks the runs, the equality of content, the belief in the reasoning service and a report
- [ ] T079 Run quickstart.md's development cohort by hand against the real edges; settle each entry of research.md "Verify at implementation" in "Found during implementation"; record under "Measurements" the cost and latency for each arm's run, the time to forecast twenty questions, and the spread of the paired differences with what it implies for the count the first prospective cohort needs

---

## Phase 9: Polish

- [ ] T080 [P] `CLAUDE.md`: what the repository is, the three rules (nothing changes a frozen cohort or a run; no model arm sees the venue's probability; a development figure never enters a prospective report), how to run and test, a dated traps section with what this feature met
- [ ] T081 [P] `README.md` (what the bench is for, the five arms, how to run a cohort, where the spec and the features are) and `docs/limitations.md` (two outcomes only; one experimenter; one forecast for each repetition; the appraisal is a model's; development cohorts show nothing; venues' terms; what is unproven)
- [ ] T082 [P] `notes/ankka-requests.md` and `notes/reasoning-requests.md` from research R16 and what implementation found, each with what it would remove
- [ ] T083 `sbt scalafmtCheckAll compile test` warning-free and green; `just check` at 0 findings; the task ticks, and the scenario count in the checker's output equal to the number of tests the feature suites ran

---

## Dependencies & Execution Order

- **Setup (1)** then **Foundational (2)**: block everything.
- **US1 (3)**: needs 2. The MVP.
- **US2 (4)**: needs US1's runs. T033 to T035, T040, T041, T043 are pure and can start with Phase 2.
- **US3 (5)**: needs US1's workflows. T046, T047, T054 can start with Phase 2.
- **US4 (6)**: needs US3's packs. T056 to T058 can start with Phase 2; T067 needs US2's report.
- **US5 (7)**: needs US2, US3 and US4.
- **Real edges (8)**: T073 and T074 need only T008; T076, T077 any time; T078 and T079 need everything.
- **Polish (9)**: last.

Within a story: domain before entity, entity before workflow step, routes before the feature
suite that drives them.

### Parallel opportunities

```text
After T007:   T008, T009, T010, T014, T015                    (ports, domain)
With Phase 3: T033, T034, T035, T040, T041, T043               (the report's arithmetic)
              T046, T047, T056, T057, T058, T073, T074         (pure and scripted parts of later stories)
```

## Implementation Strategy

**MVP first**: Phases 1 to 3. A cohort is frozen and forecast by the venue-price and model-alone
arms and every run is kept: the ledger exists and can be shown.

**Then, in order**: US2 makes the ledger say something; US3 adds evidence; US4 adds the pair of
arms the bench exists for; US5 lets everything be rehearsed without waiting; Phase 8 connects the
real venues, search, reasoning service and model, ending in a development cohort run by hand and
the measurements that size the first prospective cohort.

Commit after each task with `Tasks: T0NN` in the footer. Nothing is merged, tagged or deployed by
the build.
