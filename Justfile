# Thin wrappers; everything works without `just`.

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
