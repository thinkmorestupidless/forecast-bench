# Thin wrappers; everything works without `just`.

# The bench these recipes call; `just run` serves it here.
bench := env_var_or_default('FORECAST_URL', 'http://localhost:9000')
curl := 'curl -sS --fail-with-body -H "Content-Type: application/json"'

checker := 'uvx --from "git+https://github.com/thinkmorestupidless/speckit-bdd@v0.2.0#subdirectory=checker" speckit-bdd'

# Enable the commit hooks once per clone.
hooks:
    git config core.hooksPath .githooks

# The living features, the glossary and the specs that name them.
check:
    {{checker}} check --root . --glossary GLOSSARY.md --features features --specs specs

# Postgres with ankka's schema, for the service.
up:
    sbt schema && docker compose up -d

down:
    docker compose down -v

# The service on :9000.
run:
    sbt run

test:
    sbt test

# The reasoning service on :9100 with its Postgres, Kafka, Neo4j and the sink.
reasoning-up:
    ./scripts/reasoning-up.sh

# Stops the reasoning stack and drops its data; the bench's own Postgres stays.
reasoning-down:
    docker compose --profile reasoning rm -sfv reasoning reasoning-postgres kafka graph-topic neo4j graph-sink

# Builds the reasoning image from the sibling checkout's release tag, for when it cannot be pulled.
reasoning-image:
    ./scripts/reasoning-image.sh

# Imports questions: `just import kalshi development '{"categories":["Economics"],"limit":20}'`.
import venue standing filter='{}':
    jq -cn --argjson f '{{filter}}' '$f + {standing: "{{standing}}"}' | {{curl}} -d @- {{bench}}/venues/{{venue}}/import

# Drafts a cohort: `just cohort rehearsal development claude-opus-5-5 '{"pretendedTime":"..."}'`.
cohort id standing model more='{}':
    jq -cn --argjson m '{{more}}' '{id: "{{id}}", standing: "{{standing}}", model: "{{model}}"} + $m' | {{curl}} -d @- {{bench}}/cohorts

# Adds questions, by id or by a selection: `just add rehearsal '{"select":{"venue":"kalshi","limit":20}}'`.
add id body:
    {{curl}} -d '{{body}}' {{bench}}/cohorts/{{id}}/questions

# Names the cohort's arms: `just arms rehearsal venue-price model-alone evidence graph prose`.
arms id +names:
    jq -cn '{arms: $ARGS.positional}' --args {{names}} | {{curl}} -d @- {{bench}}/cohorts/{{id}}/arms

# Sets the window, repetitions, budget or model of a drafted cohort.
settings id body:
    {{curl}} -X PUT -d '{{body}}' {{bench}}/cohorts/{{id}}

# Freezes the cohort, which gathers its packs.
freeze id:
    {{curl}} -d '{}' {{bench}}/cohorts/{{id}}/freeze

forecast id:
    {{curl}} -X POST {{bench}}/cohorts/{{id}}/forecast

# Makes again each run that failed, as a new attempt.
retry id:
    {{curl}} -X POST {{bench}}/cohorts/{{id}}/retry-failed

runs id:
    {{curl}} "{{bench}}/runs?cohort={{id}}"

# The cohort's report as a page.
report id:
    {{curl}} {{bench}}/cohorts/{{id}}/report.md

# Reads the outcomes now rather than on the hour.
outcomes:
    {{curl}} -X POST {{bench}}/outcomes/read

