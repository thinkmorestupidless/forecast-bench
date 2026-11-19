# Limitations

What the bench does not do, and what it has not proven. Nothing here is promised past this page.

## What it measures

- **Two outcomes only.** A question with other than two outcomes, or with no resolution criteria, is
  not held. Kalshi's scalar markets and Polymarket's markets whose outcomes are not Yes and No are
  left out of every import.
- **One forecast for each repetition.** An arm forecasts a question once for each repetition of its
  cohort, inside the forecast window, and never again in that cohort. Forecasts are not updated as
  news arrives; the same question may be put in a later cohort with a fresh evidence pack.
- **The appraisal is a model's.** The claims, stances and open points the graph and prose arms read
  are one model's reading of the pack. The bench checks that every claim quotes its document and
  that both arms are given the same content; it does not check that the reading is right. A better
  or worse appraisal moves both arms together, which is what the comparison of the two needs, and is
  not what it measures.
- **The evidence is a search's.** A pack is at most twelve documents from three searches on Exa,
  excerpts only, gathered once at the freeze. What the search missed, no arm was given.
- **Development cohorts show nothing.** A development question was resolved before it was imported,
  and the model may have learnt its outcome. A development cohort's report says so on its face, and
  a report over several cohorts leaves development cohorts out. A pretended time limits the evidence
  to documents published before it; it cannot limit what the model already knows.
- **Activity is not evidence until it is counted.** A comparison of fewer than 30 questions gives
  its count and no difference; under 100 it is marked preliminary. No result is claimed of a cohort
  that has not reached those counts, and none has yet.

## How it runs

- **One experimenter.** The routes are the only interface, and there are no accounts, roles or
  sign-in. Run locally, anyone who can reach port 9000 can call every route. In an ankka cluster
  the routes admit only another instance of the bench (`Callers.self`), so exposing the service
  reaches nothing; an experimenter's way in there is not built.
- **The budget can be passed by in-flight runs.** A run reserves an estimate of its cost before its
  model is called, and the bench stops making runs when reservations reach the budget. Runs already
  reserved settle at what they cost, so a cohort can end over its budget by at most one run's cost
  for each question in flight (`bench.concurrency`, six by default).
- **Outcomes are polled.** They are read from the venues on the hour, or when asked. A venue that
  cannot be read is named and changes nothing; an outcome waits until it can be.
- **One model provider.** The bench has a client for Anthropic's models only; a cohort names one
  model, and every arm of it uses that model.
- **A run waits ten minutes at most** for its pack or its appraisal, and is then not made, saying
  which it waited for.

## The venues

- Kalshi and Polymarket are read through their public market data, without a key, as their replies
  were on 2026-11-19 (`src/test/resources/kalshi/`, `src/test/resources/polymarket/`). A change to
  either shape breaks the import until the venue's code follows.
- A void market's representation at each venue was inferred from the documentation and from markets
  of 2020, not observed on a recent market.
- Polymarket's category is its first tag, which is sometimes a promotional label; such a category is
  named as a domain or left without one.
- **Their terms.** Kalshi's developer agreement is reported to restrict storing and sharing data
  from its API; Polymarket's terms bar redistributing its data to capital-market clients without an
  agreement. Holding questions and scores privately is one thing; a report that quotes a venue's
  probabilities to an investor is another. Both must be read before a report leaves the machine.

## What is unproven

- **No cohort has been run against the real edges yet.** Every scenario runs through the real routes
  with a scripted model, search, venues and reasoning service. The live suite (`just live`) and the
  hand-run of the quickstart's development cohort against Kalshi, Exa, the model and the reasoning
  service are the first contact with them; until they have run, the cost and latency of each arm,
  and the time to forecast a cohort, are estimates.
- **Exa's replies are written from its documentation**, not recorded from a call with a key.
- **The reasoning service is played by a script in the suites**, written from its documented routes
  at 0.1.1. The live suite is the only check against the service itself.
- **The spread of the graph and prose differences is unknown**, so the number of questions the first
  prospective cohort needs to show a difference, if there is one, is not yet known.
