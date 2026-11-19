# What the bench would like from ankka

Requests to ankka, each with what it would remove from the bench. None blocks the feature. The model
is satisfactory's `notes/ankka-requests.md`. Where voice asked the same, it says so.

1. **Token usage and elapsed time on a model reply, and a session id on a `ModelRequest`.** A
   wrapper cannot tell which call a reply belongs to, and an agent's typed reply hides the exact text
   the bench must keep. Would remove `MeteredProvider` and let the forecaster be an ankka agent.
2. **A price table or a cost hook for model calls.** Would remove the bench's own prices in
   `bench.models` and the cost arithmetic in `Models`.
3. **A timer at an instant rather than after a delay.** Would let a forecast asked before its
   cohort's window begins be scheduled for the window's start; today it is refused ("the forecast
   window has not begun") and the experimenter asks again.
4. **A view client for timed actions and consumers.** Voice asked the same (its request 8). Would
   remove the `watched` entity the freeze route fills for the outcome reader, and the
   `question-runs` entity the scoring consumer keeps.
5. **A codec setting that leaves an absent `Option` out rather than writing `null`.** Would remove
   `domain/Wire.scala`, which every body sent to Exa and to the reasoning service goes through.
6. **Two HTTP endpoints under one path prefix.** Would let the report, comparison and calibration
   routes be the report endpoint's rather than the cohort endpoint's.
7. **A component's dependencies given at its creation**, rather than read from a singleton. Would
   remove `Bench.install`, the one set of edges a JVM can hold, and let the live suite run beside the
   feature suites.
8. **A command that changes a workflow's state while one of its steps runs**, or a documented rule
   for it. Today the running step's state overwrites the command's. Would remove the cohort
   workflow's polling of each question workflow, with its 20-second deadline behind a lost wake.
9. **A route timeout an endpoint can raise for one route.** Voice asked for the gateway's. Would
   let `POST /outcomes/read` stay one simple loop over a large bench rather than a bounded parallel
   one.
