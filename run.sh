#!/bin/bash
# Start AVL Editor application

cd "$(dirname "$0")"

PIDFILE=".run.pid"

# Stop a running instance first. Two things have to die: the sbt process (recorded in PIDFILE, so
# no pattern matching is needed — "sbt.*run" also matches unrelated shells that merely mention
# both words) and the JVM sbt forks, which survives its parent and keeps the window open. Left
# alone, instances pile up and every one of them writes ~/.avleditor/configuration.xml on exit.
if [ -f "$PIDFILE" ]; then
  kill "$(cat "$PIDFILE")" 2>/dev/null
  rm -f "$PIDFILE"
fi
pkill -f "com.abajar.avleditor.Main" 2>/dev/null

# Wait for the JVM to actually go away before starting another one.
for _ in $(seq 1 20); do
  pgrep -f "com.abajar.avleditor.Main" >/dev/null || break
  sleep 0.5
done
pkill -9 -f "com.abajar.avleditor.Main" 2>/dev/null

# Start the application
sbt run &
echo $! > "$PIDFILE"
