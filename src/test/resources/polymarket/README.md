# Recorded Polymarket replies

Read from Gamma, `https://gamma-api.polymarket.com`, on 2026-11-19, with no key, and trimmed to the
first event of each market:

| File | Route | What it holds |
|---|---|---|
| `open.json` | `GET /markets/keyset?closed=false&include_tag=true` | market `559651`, "Xi Jinping out before 2027?" (bid 0.014, ask 0.016), and a copy of it with `outcomes` changed by hand to two teams and `-teams` added to its id, since none of a hundred open markets had outcomes other than Yes and No |
| `last-page.json` | the same, after `after_cursor` | an empty page with no `next_cursor`, written by hand |
| `resolved-yes.json` | `GET /markets/562793?include_tag=true` | the 2026 Senate market, closed, `outcomePrices` `["1", "0"]` |
| `resolved-void.json` | `GET /markets/12?include_tag=true` | a market of 2020, closed, `outcomePrices` `["0", "0"]` |
| `disputed.json` | none | `resolved-yes.json` with `umaResolutionStatuses` changed by hand to end in `disputed` |

What the replies showed against research R9: the market's own `tags` come only with
`include_tag=true`; the cursor of the keyset route is sent back as `after_cursor`; the dispute
history is `umaResolutionStatuses`, a JSON-encoded list whose last entry is the current status;
`closedTime` is written `2026-11-04 10:53:59.942479+00`, not ISO 8601. A market Gamma does not hold
answers 404 with `{"type":"not found error","error":"id not found"}`.
