#!/bin/sh
# The reasoning service on :9100 with its own Postgres, Kafka with the reasoning-graph topic, Neo4j
# and the graph sink: what the graph and prose arms write to and the graph's belief is read from.
set -eu
cd "$(dirname "$0")/.."

./scripts/reasoning-ddl.sh
docker compose --profile reasoning up -d
