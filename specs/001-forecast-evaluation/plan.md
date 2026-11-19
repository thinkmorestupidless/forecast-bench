# Implementation Plan: Forecast evaluation

**Branch**: `001-forecast-evaluation` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-forecast-evaluation/spec.md`

## Summary

The bench is one ankka service. Questions, cohorts, evidence packs, appraisals and runs are event
sourced entities, so the journal is the audit trail and a run cannot change because no command
could change it. Two workflows forecast a frozen cohort: one for the cohort, bounding how many
questions are in flight and the budget, and one for each question, which gathers its pack by
search, then makes each arm's runs. Model calls go through one agent behind a metering wrapper
that gives each run its tokens, cost and latency. The appraisal is code calling a model once for
each document, checking every claim's quote against the document, and writing the question, the
evidence and the claims to the reasoning service; the graph arm and the prose arm are two
renderings of the one value read back from its graph. Outcomes are polled from the venues hourly,
scoring follows each outcome, and a report is computed on request by pure functions over the runs.
Every scenario in `features/` is run through the real routes with scripted stand-ins for the
model, the venues, the search and the reasoning service.

## Technical Context

**Language/Version**: Scala 3.9.0, JDK 21, sbt 1.12.15

**Primary Dependencies**: ankka 0.18.0 (`ankka-sdk`, `ankka-runtime`, `ankka-http`, `ankka-agent`,
`ankka-telemetry-otlp`, `ankka-testkit`); jsoniter through ankka's `Codecs`; the JDK HTTP client
for venues and search. No statistics library.

**Storage**: ankka's journal and views in Postgres (the bench's own database); the object store
for what models were given and answered (`.ankka/storage` on a laptop).

**Testing**: munit; ankka's `GherkinSuite` over `features/`, one suite per feature file, through
`AnkkaTestKit` (which starts its own Postgres); `TestModelProvider`, `ScriptedService`, and the
bench's `ScriptedVenue`, `ScriptedSearch` and settable clock. Tests serialised. Docker required.

**Target Platform**: a laptop (`sbt run`), beside the reasoning service 0.1.1 and its stack in
Docker compose. Deployable to an ankka installation by tag; not deployed by this feature.

**Project Type**: one ankka service

**Performance Goals**: a cohort of 200 questions, five arms and three repetitions forecast within
a working day at six questions in flight; a report over 3,000 runs in under five seconds; outcomes
held within an hour of a venue resolving.

**Constraints**: nothing changes a frozen cohort or a recorded run; no model arm is given the
venue's probability; no development figure enters a prospective report; every figure is
reproducible from the journal; the bench's database is not shared with the reasoning service's.

**Scale/Scope**: hundreds of questions a cohort, thousands of runs, tens of cohorts. One
experimenter. 137 scenarios, 45 requirements.

## Constitution Check

`.specify/memory/constitution.md` is the unfilled template, as in every family repository; the
check is against the family's principles (`ankka-brain/principles.md`) and conventions.

| Principle | Holds | How |
|---|---|---|
| No BSL on the path | yes | ankka on Pekko; nothing added but the JDK |
| Effects are inert data | yes | entities decide and persist; every call outside (model, venue, search, reasoning service, bucket) is made in a workflow step, a timed action or an endpoint |
| Explicit registration | yes | components in `Main.scala`; arms in `Arms.all`; venues and models by name in configuration |
| Split by lifecycle and trust | yes | a private repository of its own; the reasoning service is called over its public routes at a release, never linked |
| Be your own customer | yes | what the bench lacks of ankka and of the reasoning service goes to `notes/ankka-requests.md` and `notes/reasoning-requests.md` (research R16) |
| Only a tag publishes | yes | the template's `deploy.yml`; this feature tags nothing |
| The honest not-implemented list | yes | `docs/limitations.md`; and it is the product's own rule: a report says what it cannot claim |
| Traps written down | yes | `CLAUDE.md` starts with the three rules of research R16 and a dated traps section |
| Same conventions | yes | spec-kit with bdd, review and design-build; scalafmt and hooks; serialised tests; thin Justfile; Conventional Commits |
| Downstream pins releases | one exception | the reasoning service is pinned at release 0.1.1 but its image is built locally from the tag, because the package cannot be pulled; recorded as a request |

No gate fails. After the design (data model and contracts): unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/001-forecast-evaluation/
├── spec.md
├── plan.md
├── research.md          # R1 to R16, verify at implementation, found during implementation
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── http.md          # the routes
│   ├── arm.md           # the arm contract, what each model is given, the answer form
│   ├── reasoning.md     # what is asked of the reasoning service
│   └── ports.md         # Venue and Search
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
build.sbt  project/{Dependencies.scala,plugins.sbt,build.properties}  .scalafmt.conf
service.json  docker-compose.yml  Justfile  scripts/reasoning-image.sh
CLAUDE.md  README.md  docs/limitations.md  notes/{ankka,reasoning}-requests.md
features/  GLOSSARY.md

src/main/resources/
├── application.conf                     # bench.models, bench.concurrency, limits, venues, local-services.reasoning
└── instructions/<name>/<n>.md           # model-alone, evidence, graph, prose,
                                         # appraisal-reader, appraisal-open-points, gatherer-queries

src/main/scala/forecastbench/
├── Main.scala                           # registration
├── domain/                              # pure: no ankka beyond Codecs
│   ├── Question.scala  Cohort.scala  Pack.scala  Appraisal.scala  Run.scala   # states, events, decisions
│   ├── Arm.scala  Arms.scala            # the contract and the five arms
│   ├── Shape.scala                      # AppraisalContent, the two renderings, contentOf
│   ├── Answer.scala                     # the typed answer and how it ends a run
│   └── Ids.scala  Clock.scala
├── application/
│   ├── QuestionEntity.scala  CohortEntity.scala  PackEntity.scala  AppraisalEntity.scala  RunEntity.scala
│   ├── views/{Questions,Cohorts,Packs,Appraisals,Runs,CohortQuestions}.scala
│   ├── CohortForecast.scala  QuestionForecast.scala        # the workflows
│   ├── Forecaster.scala  MeteredProvider.scala  Models.scala  Instructions.scala
│   ├── ReadOutcomes.scala  ScoreOnOutcome.scala            # timed action, consumer
│   └── Given.scala                                         # the bucket
├── venues/{Venue,Kalshi,Polymarket,VenueProbability}.scala
├── gather/{Search,Exa,Gatherer}.scala
├── reasoning/{Reasoning,Appraiser}.scala                   # the client and the appraisal's steps
├── report/{Scoring,Bootstrap,Wilson,Comparison,Calibration,Report,Markdown}.scala   # pure
└── api/{VenueEndpoint,QuestionEndpoint,CohortEndpoint,RunEndpoint,OutcomeEndpoint,ReportEndpoint}.scala

src/test/scala/forecastbench/
├── steps/{ServiceFixture,FeatureSuite,World}.scala and one <Name>Features.scala for each feature file
├── support/{ScriptedVenue,ScriptedSearch,ScriptedReasoning,TestClock}.scala
├── domain/  report/  venues/  gather/                     # unit suites
└── live/LiveSuite.scala                                    # skipped without keys
```

**Structure Decision**: the template's single module, with the pure parts (`domain`, `report`)
kept free of the runtime so they are tested without it and could become a library later. The
template's `Item*` sources and suites are removed.

## Order of building

Each stage ends with its feature suites green.

1. **Ground**: build conventions, 0.18.0, the template's sample out, the fixture, the clock,
   `FeatureSuite` with no steps.
2. **Questions** (US1): the `Venue` port, `ScriptedVenue`, `question`, import, domains. Then Kalshi.
3. **Cohorts** (US1): `cohort`, drafted and frozen, refusals.
4. **Runs with the two simplest arms** (US1): models, the metering wrapper, `forecaster`, the
   bucket, `run`, the two workflows with budget and window, venue-price and model-alone, retry,
   reproduce, history.
5. **Outcomes and scores** (US2): the outcome reader, the scorer, scoring method 1.
6. **Reports** (US2): the pure report package, then its routes and the Markdown page.
7. **Evidence** (US3): the `Search` port, `ScriptedSearch`, the gatherer, `pack`, the evidence
   arm. Then Exa.
8. **The appraisal, graph and prose** (US4): `Shape` and its property first, the reasoning client,
   the appraiser, `appraisal`, the two arms, the belief.
9. **Development cohorts** (US5): the pretended time through the gatherer and the arms, the
   report's marks and what a report over several leaves out.
10. **Second venue and the edges**: Polymarket, the compose profile and image script, the live
    suite, documents, `just` recipes, measurements.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| Two workflows rather than one | bounded parallelism and one budget across hundreds of questions | one workflow a cohort is hours long and serial; one a question has nowhere to hold the budget |
| A metering wrapper on the model provider | cost, tokens and latency for each run | ankka gives usage on a session and latency nowhere |
| The object store beside the journal | inputs of tens of kilobytes for each run | the platform's guidance against large events; views would carry them |
| The reasoning service's whole stack to run a cohort | the graph arm reads the graph, by the person's decision | reading record by record never touches a graph |
