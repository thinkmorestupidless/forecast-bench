# Contract: what the bench asks of the reasoning service

The service is `reasoning` (ankka-reasoning 0.1.1), called by name. The bench uses the belief
layer only. Ids are in `research.md` R8. Every write may be sent again: the service answers `200`
with the record as held.

| When | Call | Body |
|---|---|---|
| a cohort's first appraisal | `POST /holders` | `{"id": "<cohort>.appraisal", "kind": "model", "name": "<cohort> appraisal"}` and one `<cohort>.graph.r<n>` for each repetition |
| each appraisal | `POST /questions` | `{"id": "<cohort>.<qkey>", "statement": <first 500 chars of the statement>, "hypotheses": [{"id": "yes", "statement": "Yes"}, {"id": "no", "statement": "No"}]}` |
| each document with a claim | `POST /sources` | `{"id": "src.<12 hex>", "name": <source, first 200 chars>}` |
| | `POST /evidence` | `{"source", "locator": <address>, "excerpt": <the document's excerpt>, "publishedAt"?: <when it is not later than now>}` → its `id` |
| each claim | `POST /claims` | `{"id": "<cohort>.<qkey>.c<n>", "holder": "<cohort>.appraisal", "statement", "derivesFrom": [evidenceId], "stances": [{"hypothesis": "<cohort>.<qkey>/yes", "stance"}]}` |
| before the graph arm or the prose arm reads | `POST /graph/wait`, for each of the appraisal's claims | `{"kind": "claim", "id": <the claim>, "limitMs": <what is left of the read's 30 s>}`; the graph topic has several partitions, so a later claim can be applied before an earlier one. A read is not caught up when a wait is not, when the 30 s are spent, or when the cases read do not hold exactly the appraisal's claims; it is made again 10 s later, three reads in all (`bench.graph-read`), and then fails the run, `the graph had not caught up with the appraisal` |
| the graph and prose arms | `GET /answers/case?hypothesis=<q>/yes&stance=supports` and `&stance=contradicts` | read `claims[].claim` and `claims[].evidence[0]` with its source |
| after a graph run with a forecast is recorded | `POST /beliefs/revisions` | `{"id": "<cohort>.<qkey>.b<rep>", "holder": "<cohort>.graph.r<rep>", "hypothesis": "<q>/yes", "probability", "restsOn": [{"claim"}]}` |

Failures: any call unanswered, `503 graph.unavailable`, or refused, fails what was being done with
`the reasoning service could not be reached` (unanswered, 503) or the refusal's `rule` (refused),
and it is recorded: on the appraisal, or on the graph arm's run. A belief that cannot be written
after its run was recorded does not change the run; it is retried by the step and, failing that,
noted on the cohort.

The bench does not write a market, a price observation or a resolution to the service.
