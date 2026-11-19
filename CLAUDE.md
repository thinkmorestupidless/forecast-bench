# forecast-bench

The bench: one ankka service in Scala that measures whether a forecast made from explicitly
structured reasoning beats one made from the same reasoning written as prose. It imports questions
from public venues, fixes them and the arms that answer them into a cohort, records every forecast
unchangeably with what it was given, reads the outcomes from the venues, scores the forecasts and
reports what has been shown. The design is `specs/001-forecast-evaluation/`; the living behaviour is
`features/`, in the words of `GLOSSARY.md`.

## The three rules

Every change is checked against them before anything else.

1. **Nothing changes a frozen cohort or a run.** A frozen cohort's questions, arms, model, window,
   budget and instruction versions are fixed; a run is recorded once and never revised. A second
   attempt is a new run beside the first. There is no route, command or migration that edits either.
2. **No model arm sees the venue's probability.** Nor the venue's name, nor the question's address
   at the venue. Only the venue-price arm reads the venue, and it calls no model.
3. **A development figure never enters a prospective report.** A development question was resolved
   before it was imported; a development cohort's report is marked so on its face, and a report over
   several cohorts leaves development cohorts out and names them.

## Run and test

```bash
just up            # Postgres with ankka's schema
just reasoning-up  # the reasoning service on :9100 with Postgres, Kafka, Neo4j and the graph sink
just run           # the bench on :9000
just test          # every scenario of features/ through the real routes, scripted edges
just check         # features, glossary and spec: 0 findings
just live          # against Kalshi, Exa, the model and the reasoning stack; spends money
```

`just --list` names a recipe for each route the experimenter uses (`import`, `cohort`, `add`,
`arms`, `settings`, `freeze`, `forecast`, `retry`, `runs`, `report`, `outcomes`).

## Where things are

- `src/main/scala/forecastbench/domain/`: the decisions, pure: cohort, question, run, pack,
  appraisal, the two shapes, the arms.
- `application/`: the entities, the two forecast workflows, the consumers that score, the outcome
  reader, the edges (`Bench`, installed once before the service starts).
- `api/`: the HTTP routes, the only interface.
- `gather/`, `reasoning/`, `venues/`: the edges: Exa, the reasoning service, Kalshi and Polymarket.
- `report/`: every figure, computed in Scala from the views' rows, never held.
- `src/main/resources/instructions/<arm>/<n>.md`: each instruction text by version. A change is a
  new file, never an edit: a frozen cohort names the version it was given.
- `src/test/scala/forecastbench/steps/`: one suite per feature file, through the routes, with the
  scripted model, search, venues and reasoning service of `support/`.

## Traps

- 2026-11-19: `given` is a keyword in Scala 3. As a method, value or field name it must be written
  `` `given` ``; a local value named `given` fails to parse with an error that does not say so.
- 2026-11-19: ankka's `Codecs.make` writes an absent `Option` as `null`. Neither Exa nor the reasoning
  service accepts that; bodies sent to them use `domain/Wire.make`, which leaves the field out.
- 2026-11-19: `GherkinSuite` matches a step by its text whatever its keyword. A text bound as both a
  `Given` and a `When` is ambiguous; one binding serves both, and a state step written as Given and as
  Then arranges before the action and checks after it (`OutcomeSteps`).
- 2026-11-19: the scripted model chooses what it answers by the first words of the system text
  ("You write search queries", "You read one document", "You find what the evidence leaves open").
  An instruction file that starts otherwise is answered as a forecast.
- 2026-11-19: `Bench` holds one set of edges per JVM. The feature suites share one scripted bench;
  the live suite installs real edges and so runs alone, behind `FORECAST_LIVE=1`.
- 2026-11-19: two HTTP endpoints cannot share a path prefix: the server routes by it. A cohort's
  report, comparison and calibration routes are the cohort endpoint's.
- 2026-11-19: a timed action and a consumer have no view client. The outcome reader reads the
  `watched` entity the freeze route fills; the scoring consumer keeps a `question-runs` entity.
- 2026-11-19: `POST /outcomes/read` read every watched question one by one and outlasted the route's
  timeout once a few thousand were watched; it reads them 32 at a time now. Keep any route that walks
  every question bounded and parallel.
- 2026-11-19: scalafmt reformats on every check. A scripted edit that anchors on unformatted text
  fails silently unless it asserts the anchor was found.
- 2026-11-19: an incremental compile repeats a warning from an earlier run without its location;
  `sbt clean Test/compile` shows where it is. A default argument that every caller passes is reported
  as an unused private member.
- 2026-11-19: `ReportFeatures` seeds a hundred to two hundred questions a scenario and takes about six
  minutes; run it alone while working on reports.
