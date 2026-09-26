#!/usr/bin/env bash
#
# Compile and run the Mili scheduler self-check.
#
# This is the "Java-side verification" for the scheduler work: it exercises the
# pure-Java scheduling core (fun.bm.mili.scheduler) with nothing but a JDK on the
# classpath. It deliberately does NOT initialise the folia-server submodule,
# apply the Minecraft patches, or run any Gradle task.
#
# Why that is possible at all: the scheduler core takes regions as opaque Object
# handles and reaches Minecraft only through injected SPIs (RegionOwnership.Resolver,
# RegionResolver.Locator, RegionLifecycle hooks). Anything that needs real
# game types lives in the adapter layer under fun.bm.mili.utils / dev.kaiijumc.kaiiju,
# which this harness does not touch.
#
# Usage:  scripts/scheduler-verify/run.sh
# Exit:   0 = all checks passed, 1 = at least one check failed
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

SCHEDULER_SRC="$ROOT/mili-server/src/main/java/fun/bm/mili/scheduler"

# A fresh directory per run, so a renamed class can never leave a stale .class
# behind. It is left in place afterwards for inspection; the OS clears it.
OUT_DIR="${OUT_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/mili-scheduler-verify.XXXXXX")}"
mkdir -p "$OUT_DIR"

# --- Locate a JDK -------------------------------------------------------------
# The project targets Java 25. Prefer JAVA_HOME, then the JDK the project's own
# documentation points at, then whatever javac is on PATH.
find_javac() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    echo "$JAVA_HOME/bin/javac"
    return 0
  fi
  local candidates=(
    "C:/Users/Administrator/Downloads/jdk-25_windows-x64_bin/jdk-25.0.4/bin/javac.exe"
    "C:/Program Files/Java/jdk-25/bin/javac.exe"
  )
  local c
  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      echo "$c"
      return 0
    fi
  done
  if command -v javac >/dev/null 2>&1; then
    command -v javac
    return 0
  fi
  return 1
}

if ! JAVAC="$(find_javac)"; then
  echo "error: no javac found. Set JAVA_HOME to a JDK 25 installation." >&2
  exit 2
fi
JAVA="${JAVAC%/javac*}/java"
[[ -x "$JAVA" ]] || JAVA="${JAVAC%\\javac*}\\java"

echo "Using: $JAVAC"
"$JAVAC" -version

# --- Compile ------------------------------------------------------------------
mapfile -t CORE_FILES < <(find "$SCHEDULER_SRC" -maxdepth 1 -name '*.java' | sort)
if [[ "${#CORE_FILES[@]}" -eq 0 ]]; then
  echo "error: no scheduler sources found under $SCHEDULER_SRC" >&2
  exit 2
fi

echo "Compiling ${#CORE_FILES[@]} scheduler source file(s) with -Xlint:all ..."
# -Xlint:all and no classpath: any accidental Minecraft dependency shows up here
# as a compile error, which is the point.
"$JAVAC" -Xlint:all -encoding UTF-8 -d "$OUT_DIR" "${CORE_FILES[@]}" "$HERE/SchedulerSelfCheck.java"

# --- Run ----------------------------------------------------------------------
# The harness declares `package fun.bm.mili.scheduler` so it can reach package
# internals, so it is launched by its fully-qualified name.
echo
"$JAVA" -cp "$OUT_DIR" fun.bm.mili.scheduler.SchedulerSelfCheck
