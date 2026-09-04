# Machina

A distributed key-value database in Java using Raft consensus.

Three nodes elect a leader, replicate a shared log by majority, and keep data consistent even when the leader dies.

## What it does

- Three nodes start from the same JAR with different IDs and ports
- They elect a leader using Raft-style voting
- Writes go to the leader, which appends to its log and replicates to followers
- Once a majority (2 of 3) have an entry, it is committed and applied to each node's key-value map
- Any node can serve reads
- Kill the leader mid-write - a new leader takes over and committed writes are not lost

## Endpoints

- `GET /status` - node info: role, term, leader, log size, commit index
- `POST /set` - body `{"key":"x","value":"5"}` (leader only; followers redirect)
- `GET /get?key=x` - read from any node

## Build

```bash
./run.sh
```

First run downloads dependencies (~30s). Logs go to `/tmp/machina-nodeN.log`.

## Test

```bash
# find the leader
curl localhost:7001/status
curl localhost:7002/status
curl localhost:7003/status

# write to the leader
curl -d '{"key":"x","value":"5"}' localhost:7002/set

# read from all three
curl localhost:7001/get?key=x
curl localhost:7002/get?key=x
curl localhost:7003/get?key=x

# kill the leader mid-burst, watch writes continue
pkill -f -- "--id 2"
```

## Stop

```bash
./stop.sh
```

## Tech

- Java 21, Maven, Gson
- JDK built-in `com.sun.net.httpserver`

## Structure

```
src/main/java/com/machina/
  Main.java   -- parses --id and --port, wires the node
  Node.java   -- state machine, log, elections, replication
  HttpApi.java -- HTTP endpoints: /status, /set, /get, /requestvote, /heartbeat
```