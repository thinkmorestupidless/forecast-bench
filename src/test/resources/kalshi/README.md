# Recorded Kalshi replies

Read from `https://api.elections.kalshi.com/trade-api/v2` on 2026-11-19 and 2026-11-20, with no
key, and trimmed:

| File | Route | What it holds |
|---|---|---|
| `events-open.json` | `GET /events?status=open&with_nested_markets=true` | two events: the January 2027 Fed decision with its market `KXFEDDECISION-27JAN-H0` (bid 0.39, ask 0.40) and a copy of that market with `market_type` changed by hand to `scalar` and `-SCALAR` added to its ticker, since no scalar market was open; and a football event with one untraded market (bid 0.05, ask 0.95, no volume) |
| `events-settled.json` | `GET /events?status=settled&with_nested_markets=true` | an Elections event with a finalised market (result `no`); a settled event still holding an active market; and an event listed with no markets, as one whose markets have moved to the archive is |
| `last-page.json` | either, after the cursor | an empty page with an empty cursor, written by hand |
| `event.json` | `GET /events/KXFEDDECISION-27JAN` | the event, category `Economics`; its markets trimmed to one |
| `finalized.json` | `GET /markets/{ticker}` of a settled market | a finalised market, result `no` |
| `historical.json` | `GET /historical/markets/{ticker}` | a market settled before the historical cutoff, result `no` |

`GET /markets` lists one page of markets with no category; an import that read each market's
event for it made one request per event and outlasted the bench's route timeout, so imports read
`/events` instead. A market Kalshi does not hold answers 404 with `{"error":{"code":"not_found","message":"not found"}}`
at both `/markets/{ticker}` and `/historical/markets/{ticker}`.
