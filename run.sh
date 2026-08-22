#!/usr/bin/env bash
# rebuild + boot all three machina nodes in the background
set -euo pipefail
cd "$(dirname "$0")"

./stop.sh > /dev/null 2>&1 || true

echo "[machina] building..."
mvn -q package

for id in 1 2 3; do
  port=$((7000 + id))
  log="/tmp/machina-node${id}.log"
  java -jar target/machina-1.0.jar --id "$id" --port "$port" > "$log" 2>&1 &
  echo "[machina] node ${id} on port ${port}   pid=$!   log=${log}"
done

echo "[machina] try: curl localhost:7001/status"
