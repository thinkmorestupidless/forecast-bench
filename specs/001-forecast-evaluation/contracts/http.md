# Contract: the HTTP surface

Every route is `Acl.allowCallers(Callers.self)`. Bodies are JSON. A refusal is ankka's error body
with the entity's message; statuses are ankka's mapping of the error code (`409` conflict, `422`
invalid, `404` not found). Port 9000.

## Venues and questions

| Route | Does |
|---|---|
| `POST /venues/{venue}/import` | body `{"standing": "prospective" \| "development", "categories": [..]?, "closesAfter": t?, "closesBefore": t?, "leastVolume": n?, "onePerEvent": true, "limit": 500}`. Reads the venue, holds each question once. `200 {"held": n, "already": n, "leftOut": [{"venueId", "reason": "more than two outcomes" \| "no resolution criteria"}]}`. `503 {"error": "the venue could not be read"}` and nothing changed. |
| `PUT /venues/{venue}/domains` | body `{"Economics": "economy", ...}`: the experimenter's names for the venue's categories. Applies to questions imported afterwards. |
| `GET /questions?venue=&standing=&domain=&waiting=` | rows of the `questions` view |
| `GET /questions/{id}` | the question with every outcome it has had |

`{venue}` is `kalshi` or `polymarket`. An unknown venue is `404`.

## Cohorts

| Route | Does |
|---|---|
| `POST /cohorts` | `{"id", "standing", "model", "pretendedTime"?}` → `201` the cohort, drafted |
| `PUT /cohorts/{id}` | any of `{"model", "window": {"from","to"}, "repetitions", "budget"}`; `409` when frozen |
| `POST /cohorts/{id}/questions` | `{"questions": [id..]}` or `{"select": {"venue","domain"?,"closesAfter"?,"closesBefore"?,"limit"}}`; refused whole when any question's standing differs; `409` when frozen |
| `DELETE /cohorts/{id}/questions/{q}` | `409` when frozen |
| `POST /cohorts/{id}/arms` | `{"arms": ["venue-price", ...]}`; `422` for an arm the bench does not know; `409` when frozen |
| `POST /cohorts/{id}/freeze` | `{"instructions": {"graph": 2}?}` (versions; the latest where not named) → the frozen cohort; `422` naming the question or what is missing. Starts gathering. |
| `POST /cohorts/{id}/forecast` | starts the arms; `409 the cohort is not frozen`; `409` outside the window, with `notMade` |
| `POST /cohorts/{id}/retry-failed` | makes again each failed run with no later run that gave a forecast, within the window |
| `POST /cohorts/{id}/score?method=n` | scores every run again by that method |
| `GET /cohorts/{id}` | the cohort: settings, status, packs noted, spent, `notMade` |
| `GET /cohorts/{id}/history` | `[{"at", "what", ...}]` in order: drafted, each change, frozen, each pack gathered, each appraisal, each run recorded |
| `GET /cohorts/{id}/packs/{question}` | the pack, with its documents and searches; `404` before it is gathered |
| `GET /cohorts/{id}/appraisals/{question}` | the appraisal: claims, open points, versions |
| `GET /cohorts/{id}/report` | the report (`data-model.md`), JSON |
| `GET /cohorts/{id}/report.md` | the same figures as a page |
| `GET /cohorts/{id}/comparison?a=&b=` | one comparison |
| `GET /cohorts/{id}/calibration?arm=` | one calibration table |
| `GET /reports?cohorts=a,b` and `/reports.md` | a report over several; development cohorts left out and named |

## Runs and outcomes

| Route | Does |
|---|---|
| `GET /runs?cohort=&arm=&question=` | rows of the `runs` view |
| `GET /runs/{id}` | the run as recorded, with `given` and `answered` read from the bucket and checked against their digests (`"intact": true`) |
| `POST /runs/{id}/reproduce` | gives the model the run's `given` again; `200 {"answered", "probability"?, "usage", "latencyMs"}`; nothing is recorded as a run |
| `PUT`, `PATCH`, `DELETE /runs/{id}` | `405`: no such route exists; the features' "tries to change" and "tries to remove" are these |
| `POST /outcomes/read` | reads outcomes from the venues now; `200 {"read": n, "changed": n}`; a venue that cannot be read is named and its questions left as they were |
| `POST /questions/{id}/outcome` | `405`: nobody states an outcome |
