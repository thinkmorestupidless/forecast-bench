# Glossary

The words this project's features use, each in exactly one sense.

## People and the bench

### bench
*Proposed.* The system these features describe: it holds questions, cohorts, evidence packs, runs,
outcomes and scores, and makes reports.

Avoid: harness, system, platform

### experimenter
*Proposed.* The person who runs the bench: imports questions, drafts and freezes cohorts, asks for
forecasts and reads reports. The only person in the features.

Avoid: user, operator, researcher

## Questions

### venue
*Proposed.* A public place that asks questions about the future and says how each turned out: a
prediction market or a forecasting site. Every question comes from one venue.

Avoid: market, exchange, platform, provider

### question
*Proposed.* Something a venue asks about the future, with exactly two outcomes, yes and no. The
bench imports questions and never writes one.

Avoid: market, contract, event

### statement
*Proposed.* The sentence a question or a claim says.

### resolution criteria
*Proposed.* The venue's own words for what makes a question's outcome yes or no.

Avoid: rules, terms

### import
*Proposed.* To read questions from a venue and hold them. Also the account of one such reading:
what was held and what was left out.

### held
*Proposed.* Kept by the bench, or by the reasoning service, and readable back as it was given.

Avoid: stored, saved, persisted

### open
*Proposed.* Of a question at its venue: still taking forecasts, with no outcome.

### closes
*Proposed.* Of a question: the time after which its venue takes no more forecasts. A question that
has passed that time has closed.

### resolves
*Proposed.* Of a venue and a question: says how it turned out. A question its venue has resolved is
resolved.

### prospective
*Proposed.* Of a question: open at its venue when the bench first imported it. Of a cohort: made
only of prospective questions. Only prospective cohorts count as evidence of anything.

Avoid: live, real

### development
*Proposed.* Of a question: already resolved at its venue when the bench first imported it, so a
model may know its outcome. Of a cohort: made only of development questions, for rehearsing the
bench. A development question or cohort never becomes prospective.

Avoid: historical, backtest, test

### category
*Proposed.* The venue's own label for the subject of a question.

### domain
*Proposed.* The bench's label for the subject of a question, named by the experimenter for each
venue category; `other` where none is named.

Avoid: topic, theme, subject

### probability
*Proposed.* A number from nought to one saying how likely the outcome yes is taken to be.

Avoid: odds, confidence, likelihood

### venue's probability
*Proposed.* The probability of yes that a venue shows for a question at a time, as the bench
observed it.

Avoid: market price, crowd

### observed
*Proposed.* Of a venue's probability: read from the venue by the bench at a time the bench records.

## Cohorts

### cohort
*Proposed.* One experiment: a set of questions, the arms that forecast them, one model, a forecast
window, a number of repetitions and a budget.

Avoid: experiment, batch, round

### drafted
*Proposed.* Of a cohort: being put together, and changeable. Nothing forecasts a drafted cohort.

### frozen
*Proposed.* Of a cohort: fixed for good. A frozen cohort's questions, arms, model, forecast window
and repetitions never change.

Avoid: locked, started

### freezes
*Proposed.* Of the experimenter and a cohort: makes it frozen.

### forecast window
*Proposed.* The stretch of time, fixed when a cohort is frozen, in which every run of the cohort is
made.

### repetition
*Proposed.* One of the several times an arm forecasts the same question in one cohort. A cohort
names how many.

Avoid: sample, trial, attempt

### budget
*Proposed.* The most a cohort's runs may cost in all.

### pretended time
*Proposed.* For a development cohort only: the past time the cohort is forecast as if it were.

### history
*Proposed.* Everything that happened to a cohort, in the order it happened.

## Arms

### arm
*Proposed.* One way of answering a question with a probability. The arms the bench knows are the
venue-price arm, the model-alone arm, the evidence arm, the graph arm and the prose arm, named in
values as "venue price", "model alone", "evidence", "graph" and "prose".

Avoid: configuration, condition, system, treatment

### venue-price arm
*Proposed.* The arm whose forecast is the venue's probability. It uses no model.

### model arm
*Proposed.* An arm whose forecast is a model's answer: every arm but the venue-price arm.

### model-alone arm
*Proposed.* The model arm given only the question's statement, its resolution criteria, the time it
closes and the time of the run.

### evidence arm
*Proposed.* The model arm given what the model-alone arm is given and every document of the
evidence pack.

### graph arm
*Proposed.* The model arm given what the model-alone arm is given and the appraisal as structure:
the case for, the case against and the open points.

### prose arm
*Proposed.* The model arm given what the model-alone arm is given and the appraisal as one passage.
The control for the graph arm.

### takes evidence
*Proposed.* Of an arm: reads the evidence pack, itself or through the appraisal. The evidence arm,
the graph arm and the prose arm take evidence.

### model
*Proposed.* The language model a cohort names, by its name and version. Every model arm of a cohort
uses it, and so does the appraisal.

Avoid: LLM, AI

### instructions
*Proposed.* The fixed words a model arm, or the appraisal, gives the model besides the question and
what the arm is given. Named by version.

Avoid: prompt

### version
*Proposed.* The name of one fixed state of something that may be rewritten: instructions, the
gatherer, a scoring method.

### given
*Proposed.* Of a model: sent as the whole of what it reads for one answer.

### answers
*Proposed.* Of a model: what it sends back when given something. Of an arm: what it ends a run with.

### shape
*Proposed.* How the appraisal is laid out for a model: as structure for the graph arm, as one
passage for the prose arm.

### passage
*Proposed.* Running sentences with no grouping and no headings.

## Evidence

### evidence pack
*Proposed.* The documents every arm that takes evidence is given for one question in one cohort.
Also called the pack where the question is plain.

Avoid: context, corpus, snapshot

### document
*Proposed.* One thing the search found, held with its source, its address, the time it was
published and an excerpt.

Avoid: article, page

### source
*Proposed.* Who published a document.

### address
*Proposed.* Where a document is found.

Avoid: URL, link, locator

### published
*Proposed.* Of a document: made public by its source, at a time the document states.

### excerpt
*Proposed.* The part of a document the bench keeps as text, up to a limit.

### gatherer
*Proposed.* The part of the bench that makes evidence packs by search. Named by version.

Avoid: retriever, crawler

### search
*Proposed.* One query the gatherer puts to find documents, and what came back.

### gathered
*Proposed.* Of a pack: made by the gatherer, at a time the pack records. To gather is to make it.

### limit
*Proposed.* A bound the bench applies: the most documents in a pack, the longest excerpt.

### empty
*Proposed.* Of a pack: gathered, with no documents.

## The appraisal

### appraisal
*Proposed.* One reading of a question's evidence pack, made once for a cohort: the claims the
documents support and the open points they leave.

Avoid: analysis, extraction

### reasoning service
*Proposed.* The separate service that holds the appraisal's question, hypotheses and claims, and
the graph arm's belief.

### hypothesis
*Proposed.* One of the two answers to a question, as the reasoning service holds them: the yes
hypothesis and the no hypothesis.

### claim
*Proposed.* A statement the appraisal makes from one document, with one stance on the yes
hypothesis.

Avoid: fact, finding, argument

### derives from
*Proposed.* The connection from a claim to the document it was made from.

### stance
*Proposed.* How a claim bears on the yes hypothesis: it supports it or it contradicts it.

### supports
*Proposed.* A stance: the claim makes yes more likely.

### contradicts
*Proposed.* A stance: the claim makes yes less likely.

### open point
*Proposed.* A statement of something the evidence does not settle, made by the appraisal.

Avoid: uncertainty, gap, unknown

### case
*Proposed.* The claims that take one stance on the yes hypothesis, with their documents. The case
for is the claims that support it; the case against, the claims that contradict it.

### belief
*Proposed.* A probability for a hypothesis held in the reasoning service with the claims it rests
on.

### rests on
*Proposed.* The connection from a belief to each claim given as a reason for it.

### reasons
*Proposed.* The claims the graph arm names as what its forecast rests on.

### content
*Proposed.* What a model is given, apart from its shape: the claims, stances, documents and open
points.

## Runs

### run
*Proposed.* One arm's answer to one question in one cohort for one repetition, recorded once. A run
ends with a forecast, abstained or failed.

Avoid: attempt, call, prediction, trial

### forecast
*Proposed.* The probability a run ends with. As a verb, of an arm or a cohort: to make runs.

Avoid: prediction, estimate, bet

### abstained
*Proposed.* Of a run: ended with no forecast because the arm declined to give one, with the reason.

Avoid: skipped

### failed
*Proposed.* Of a run or an appraisal: ended with nothing usable because something went wrong, with
the reason.

Avoid: errored, broken

### recorded
*Proposed.* Of a run, an outcome or a score: held by the bench, at a time the bench sets.

### dated
*Proposed.* Of a run: carrying the time the bench recorded it.

### refused
*Proposed.* Not accepted: nothing is held or changed, and whoever asked is told why.

Avoid: rejected, denied

### cost
*Proposed.* What a run's model answers were charged, in money, including its share of the
appraisal.

### tokens
*Proposed.* The count of pieces of text a model read and wrote for a run, as the model's maker
counts them.

### latency
*Proposed.* How long a run took from being asked for to being recorded.

### reproduces
*Proposed.* Of the experimenter and a run: gives the model again exactly what the run holds it was
given, to see an answer beside it. Nothing is recorded as a run.

Avoid: replays, reruns

## Outcomes and scores

### outcome
*Proposed.* How a question turned out, as its venue says: yes, no or void. Also either of the two
answers a question offers.

Avoid: verdict

### result
*Proposed.* The venue's own word for how a question turned out, before the bench reads it as an
outcome.

### void
*Proposed.* An outcome: the venue cancelled the question, so it turned out neither yes nor no.

Avoid: cancelled, invalid

### cancels
*Proposed.* Of a venue and a question: withdraws it without a result.

### disputed
*Proposed.* Of an outcome: challenged at its venue and not yet final. A dispute is that challenge.

### settles
*Proposed.* Of a venue and a dispute: ends it with a final result. Of evidence and an open point:
decides it.

### waiting
*Proposed.* Of a question in a frozen cohort: with no outcome yet.

Avoid: pending, unresolved

### revised
*Proposed.* Of an outcome: replaced by a later outcome for the same question. The earlier one is
kept.

### score
*Proposed.* A number saying how good a forecast was, once its question has an outcome of yes or no
that is not disputed. Lower is better. A run or an arm with one is scored.

Avoid: accuracy, error, loss

### Brier score
*Proposed.* A score: the square of the distance between the forecast and the outcome, where yes is
one and no is nought.

### log score
*Proposed.* A score: the surprise at the outcome, the natural logarithm of the probability the
forecast gave to what happened, with its sign changed.

Avoid: log loss

### scoring method
*Proposed.* The fixed way scores are made, named by version: which scores, and the bound.

### bound
*Proposed.* The nearest to nought or to one that a scoring method lets a forecast be when making a
log score.

### spread
*Proposed.* How far apart an arm's repetitions for one question were.

### mean
*Proposed.* The sum of some numbers divided by how many there are.

Avoid: average

## Reports

### report
*Proposed.* What the bench says a cohort, or several cohorts, has shown at a time: each arm's
scores, the comparisons, the calibration tables, and what was left out.

Avoid: results, dashboard, leaderboard

### comparison
*Proposed.* Two arms set against each other on the questions both have a score for: the mean
difference of their scores, its interval and its count.

### difference
*Proposed.* One arm's score for a question less another's.

### interval
*Proposed.* The range a figure is taken to lie in, given how many questions are behind it.

Avoid: error bar, margin

### count
*Proposed.* How many questions, forecasts or runs are behind a figure.

Avoid: sample size, n

### standing
*Proposed.* How far a comparison can be trusted by its count: `too few` under thirty, `preliminary`
from thirty to ninety-nine, `counted` from a hundred.

### counted
*Proposed.* Of a comparison: with a count of a hundred or more. Of a question, a run or a cohort:
included in a figure.

### calibration table
*Proposed.* For one arm: its forecasts sorted into bands, each band saying how often yes happened
among them.

Avoid: reliability diagram, calibration curve

### band
*Proposed.* One of ten equal ranges of probability in a calibration table.

Avoid: bin, bucket

### horizon
*Proposed.* How far ahead a question was forecast: `days` up to seven, `weeks` up to twenty-eight,
`months` beyond, counted from the end of the forecast window to the time the question closes.

### figure
*Proposed.* Any number a report gives.

## Everyday words

a, about, adds, after, again, against, all, also, an, and, any, anyone, anything, apart, are, as, asked,
asks, at, be, been, before, beside, better, between, both, but, by, came, can, cannot, change, changes, checked, compared, could, days, did,
does, each, earlier, either, else, end, ended, ends, equal, every, exactly, except, fewer, finds, finished,
first, follows, for, found, from, further, gave, gives, had, happened, has, have, having, holds,
how, in, includes, is, it, its, itself, kept, known, knows, last, later, leads, leaves, left, less, lists, lower,
made, make, makes, making, marked, marks, may, more, most, named, names, naming, neither, never,
no, none, nor, not, nothing, nought, number, of, often, on, once, one, only, or, order, other, out, over,
own, pair, part, puts, reaches, read, reads, removal, remove, removed, runs, same, say, saying,
says, second, set, several, shown, shows, since, so, something, spent, state, states, still, stops, taken, takes,
ten, than, that, the, their, them, themselves, then, there, they, this, those, through, time, to,
told, touching, tried, tries, twice, two, until, used, uses, was, were, what, when, where, which,
while, who, whose, why, width, with, within, without, yes, yet
