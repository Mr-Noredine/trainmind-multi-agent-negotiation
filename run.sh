#!/usr/bin/env bash
set -euo pipefail

JADE_JAR="JADE-all-4.6.0/JADE-bin-4.6.0/jade/lib/jade.jar"

mkdir -p bin
mapfile -t SOURCES < <(find src/main/java -name '*.java' | sort)
javac -cp "$JADE_JAR" -d bin "${SOURCES[@]}"

java -cp "$JADE_JAR:bin" jade.Boot -agents "agent_manager:Agent_Manager;agent_r:Agent_R;agent_p:Agent_P;agent_user:Agent_User"
