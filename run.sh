#!/bin/bash
# Start AVL Editor application

cd "$(dirname "$0")"

# Stop a running instance first. The app runs in a JVM that sbt forks, so killing the sbt
# launcher alone leaves the window open: instances then pile up, all writing
# ~/.avleditor/configuration.xml when they exit, and the last one to die wins.
pkill -f "com.abajar.avleditor.Main" 2>/dev/null
pkill -f "sbt.*run" 2>/dev/null

# Wait for the JVM to actually go away before starting another one.
for _ in $(seq 1 20); do
  pgrep -f "com.abajar.avleditor.Main" >/dev/null || break
  sleep 0.5
done
pkill -9 -f "com.abajar.avleditor.Main" 2>/dev/null

# Start the application
sbt run &
