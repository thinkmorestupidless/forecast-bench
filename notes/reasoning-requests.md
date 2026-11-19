# What the bench would like from ankka-reasoning

Requests to the reasoning service at 0.1.1, each with what it would remove from the bench. None
blocks the feature.

1. **A record for an open point**: something the evidence leaves unsettled, attached to a question.
   Would remove the bench's own holding of an appraisal's open points beside the claims the service
   holds, and the graph arm's reading of them from two places.
2. **A namespace for ids.** Every id the bench writes is prefixed by its cohort so that two cohorts
   never collide. A namespace given once per holder or per caller would remove the prefixes and the
   id helpers in `reasoning/Reasoning.scala`.
3. **A release image that can be pulled without credentials.** Would remove
   `scripts/reasoning-image.sh`, which builds the image from a sibling checkout at its tag. Voice
   needs the same.
4. **Several records written in one request**: a source, its evidence and the claim on it. An
   appraisal of twelve documents is some forty requests today, each waited for. Would remove the
   bench's write loop and shorten the graph and prose arms' wait for their appraisal.
5. **The graph's catch-up for one write**, rather than the bench awaiting its last write with
   `/graph/wait` before reading the two cases back. Would remove the bench's wait and its
   `NotCaughtUp` failure.
