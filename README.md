# forecast-bench

An instrument for one question: is a probability forecast made from explicitly structured reasoning
(hypotheses, the claims that support and contradict them, the points the evidence leaves open)
better calibrated than one made from the same reasoning written out as prose?

The bench imports questions about the future from public venues (Kalshi and Polymarket), fixes a
set of them and the ways of answering them into a cohort, has each arm forecast each question
before its outcome exists, keeps every forecast unchangeably with everything it was given, reads the
outcomes from the venues, scores the forecasts, and reports what has been shown, with the number of
questions behind every figure and a plain statement of what has not been shown yet.

It is an [ankka](https://github.com/thinkmorestupidless/ankka) service in Scala. Questions, cohorts,
runs and outcomes are journaled, and the journal is the audit trail.

## The five arms

| Arm | Forecasts from |
|---|---|
| `venue-price` | the venue's own probability: the middle of its bid and ask, else its last price |
| `model-alone` | the model, given the question's statement, criteria, close time and the time now |
| `evidence` | the model, given that and an evidence pack of documents gathered by search |
| `graph` | the model, given that and one appraisal of the pack as structure: the case for, the case against, the open points |
| `prose` | the model, given that and the same appraisal written as one passage |

The graph and prose arms read one appraisal and differ in shape and in nothing else; the bench
checks that both texts hold the same content before either model is called. Their comparison is
what the report leads with. The other arms are the baselines a later arm is measured against.

## Run a cohort

Needs JDK 21, sbt, Docker, `jq` and [`just`](https://github.com/casey/just) (every recipe is one
command and works without it). `ANTHROPIC_API_KEY` for the model and `EXA_API_KEY` for the search;
without them the service starts and each call that needs one fails, naming it.

```bash
just up             # Postgres with ankka's schema
just reasoning-up   # the reasoning service on :9100, with its Postgres, Kafka, Neo4j and graph sink
just run            # the bench on :9000

just import kalshi development '{"categories":["Economics"],"limit":20}'
just cohort rehearsal development claude-opus-5-5 '{"pretendedTime":"2026-10-01T00:00:00Z"}'
just add rehearsal '{"select":{"venue":"kalshi","limit":20}}'
just arms rehearsal venue-price model-alone evidence graph prose
just settings rehearsal '{"window":{"from":"<now>","to":"<in two hours>"},"repetitions":1,"budget":10}'
just freeze rehearsal     # gathers the evidence packs
just forecast rehearsal
just report rehearsal     # a development cohort's report is marked as showing nothing
```

A prospective cohort is the same with `prospective` questions and no pretended time; its outcomes
arrive on the hour, or at once with `just outcomes`. `specs/001-forecast-evaluation/quickstart.md`
walks through both, and through checking each of the feature's guarantees by hand.

If the reasoning image cannot be pulled, `just reasoning-image` builds it from a sibling checkout of
`ankka-reasoning` at its release tag.

## Test

```bash
just test    # every scenario of features/ through the real routes, with scripted model, search,
             # venues and reasoning service; a throwaway Postgres in Docker
just check   # the features, the glossary and the spec hold together: 0 findings
just live    # three development questions from Kalshi, all five arms, real edges; spends money
```

## Where things are

- `specs/001-forecast-evaluation/`: the specification, the plan, the research and its decisions,
  the data model, the contracts, the tasks.
- `features/`: the living behaviour, one directory per capability, in the words of `GLOSSARY.md`.
- `docs/limitations.md`: what the bench does not do, and what is not proven.
- `notes/`: what the bench asks of ankka and of the reasoning service.
- `CLAUDE.md`: the repository's three rules and its traps.

## Commits and pull requests

Commit messages are [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/);
`.githooks/commit-msg` refuses anything else (`just hooks` enables it once per clone). A pull
request's description follows `.github/pull_request_template.md`.

## Upgrading ankka

The ankka version lives in two places that move together: `project/Dependencies.scala` and
`runtime` in `service.json`. Then `sbt schema` and `just down && just up` for the local database.
