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
  to documents published before it; it cannot limit what the model already knows. The venue-price
  arm abstains on every development question: a settled market gives no probability.
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
  for each question in flight (`bench.concurrency`, six by default). A graph or prose run reserves
  its share of the appraisal with its own call, so a cohort can stop earlier than its spending
  alone would make it.
- **A step that fails is settled, not retried.** A step that throws or outlives its timeout leaves
  its run recorded as failed at what it reserved (the model may have been called), or not made when
  nothing was reserved; a gathering or an appraisal is held as failed. The round goes on and ends.
  `just retry` makes a failed run again as a new attempt.
- **Outcomes are polled.** They are read from the venues on the hour, or when asked. A venue that
  cannot be read is named and changes nothing; an outcome waits until it can be.
- **One model provider.** The bench has a client for Anthropic's models only; a cohort names one
  model, and every arm of it uses that model.
- **A run waits ten minutes at most** for its pack or its appraisal, and is then not made, saying
  which it waited for.

## The venues

- Kalshi and Polymarket are read through their public market data, without a key, as their replies
  were on 2026-11-19 and, for Kalshi, as read against the live venue on 2026-11-20
  (`src/test/resources/kalshi/`, `src/test/resources/polymarket/`). A change to either shape breaks
  the import until the venue's code follows. Polymarket has not been read against the live venue.
- A venue's resolution rules often name it and link to its pages. Every text a model is given has
  the venues' names read as "the venue" and their addresses left out. From gatherer version 2, a
  document whose title, address, source or excerpt names a venue is left out of the pack, so a news
  page quoting a venue's probability is too; one that quotes it without naming the venue is not.
  A cohort frozen under version 1, as the rehearsal's were, keeps the packs it gathered.
- A void market's representation at each venue was inferred from the documentation and from markets
  of 2020, not observed on a recent market.
- Polymarket's category is its first tag, which is sometimes a promotional label; such a category is
  named as a domain or left without one.
- **Their terms.** Kalshi's developer agreement is reported to restrict storing and sharing data
  from its API; Polymarket's terms bar redistributing its data to capital-market clients without an
  agreement. Holding questions and scores privately is one thing; a report that quotes a venue's
  probabilities to an investor is another. Both must be read before a report leaves the machine.

## What is unproven

- **One rehearsal against the real edges.** Three development cohorts of the same twenty Kalshi
  questions were forecast by all five arms against Kalshi, Exa, the model and the reasoning service
  on 2026-11-20; `specs/001-forecast-evaluation/research.md` records what they found and what they
  cost. Every other scenario runs through the real routes with a scripted model, search, venues and
  reasoning service. Polymarket, and a cohort of two hundred questions, have not run live.
- **The reasoning service is played by a script in the suites**, written from its documented routes
  at 0.1.1. The live suite and the rehearsal are the only checks against the service itself. Its
  graph sink and the service run under emulation on Apple silicon; twenty appraisals at once lagged
  it enough to need the retried read, and two hundred are not measured.
- **A view read past a thousand rows is not observed.** The routes ask for explicit limits; no view
  held a thousand rows in the rehearsal.
- **The spread of the graph and prose differences is not yet known for prospective questions.**
  The rehearsal's twenty, mostly near-certain, gave a spread of 0.014, which says nothing of
  uncertain questions; the first prospective cohort is sized on the assumed 0.10 until it gives its
  own.
