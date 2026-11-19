# Quickstart: Forecast evaluation

How to run the bench on a laptop and see that the feature holds. Commands are `just` recipes; each
is one command and works without `just`.

## Once

```bash
just hooks                       # commit hooks
export ANTHROPIC_API_KEY=...     # the cohort's model
export EXA_API_KEY=...           # the gatherer's search
scripts/reasoning-image.sh       # builds ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.1 from ../ankka-reasoning
```

## Start

```bash
just up            # the bench's Postgres with ankka's schema
just reasoning-up  # the reasoning service on :9100 with its Postgres, Kafka, Neo4j and the sink
just run           # the bench on :9000
```

## Prove it without keys

```bash
just test          # every scenario of features/ through the real routes, with scripted model,
                   # venue, search and reasoning service; and the report's arithmetic
just check         # the features, the glossary and the spec: 0 findings
```

## A development cohort, end to end (minutes, a few dollars)

```bash
just import kalshi development '{"categories":["Economics"],"limit":20}'
just cohort rehearsal development claude-opus-5-5 '{"pretendedTime":"<a week before the questions closed>"}'
just add rehearsal '{"select":{"venue":"kalshi","limit":20}}'
just arms rehearsal venue-price model-alone evidence graph prose
just settings rehearsal '{"window":{"from":"<now>","to":"<in two hours>"},"repetitions":1,"budget":10}'
just freeze rehearsal            # gathers the packs
just forecast rehearsal
just report rehearsal            # marked: development: the model may know these outcomes
```

Check: 100 runs in `just runs rehearsal`; `GET /runs/<a graph run>` shows what the model was given
with `"intact": true`; the report's first comparison is graph with prose and is marked `too few`.

## The first prospective cohort

The same with `prospective`, no pretended time, 200 questions closing one to twelve weeks out
across at least three domains, three repetitions, and a budget worked out from the development
cohort's cost for each run. Outcomes arrive by themselves; `just report <cohort>` at any time.

## What to look for

| Requirement | Look |
|---|---|
| a frozen cohort cannot change | `just settings <frozen> '{"repetitions":5}'` answers 409 |
| a run cannot change | `curl -X PUT :9000/runs/<id>` answers 405; the run reads the same |
| graph and prose hold the same content | `just test` (the property), and `GET /cohorts/<id>/history` shows no run failed for it |
| no model saw the venue's probability | search every object in `.ankka/storage/given` for the venue's figure and name: nothing |
| the figures are right | `sbt "testOnly forecastbench.report.*"` |
