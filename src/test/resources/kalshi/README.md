# Recorded Kalshi replies

Read from `https://api.elections.kalshi.com/trade-api/v2` on 2026-11-19, with no key, and trimmed:

| File | Route | What it holds |
|---|---|---|
| `open.json` | `GET /markets?status=open&mve_filter=exclude` | the January 2027 Fed decision market `KXFEDDECISION-27JAN-H0` (bid 0.39, ask 0.40); an untraded football market (bid 0.05, ask 0.95, no volume); and a copy of the first with `market_type` changed by hand to `scalar` and `-SCALAR` added to its ticker, since no scalar market was open |
| `last-page.json` | the same, after the cursor | an empty page with an empty cursor, written by hand |
| `event.json` | `GET /events/KXFEDDECISION-27JAN` | the event, category `Economics`; its markets trimmed to one |
| `finalized.json` | `GET /markets/{ticker}` of a settled market | a finalised market, result `no` |
| `historical.json` | `GET /historical/markets/{ticker}` | a market settled before the historical cutoff, result `no` |

A market Kalshi does not hold answers 404 with `{"error":{"code":"not_found","message":"not found"}}`
at both `/markets/{ticker}` and `/historical/markets/{ticker}`.
