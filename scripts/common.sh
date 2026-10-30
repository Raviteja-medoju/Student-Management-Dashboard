#!/usr/bin/env bash
# Shared helpers for the project scripts (backup, restore, preflight, setup, pack).
# Usage inside a script:   . "$(dirname "${BASH_SOURCE[0]}")/scripts/common.sh"

ROOT="${ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
RUN_DIR="$ROOT/.run"
BACKUP_DIR="$ROOT/backups"
DATA_DIR="$ROOT/backend/data"
DB_NAME="studentdb"
H2_URL="jdbc:h2:file:$DATA_DIR/$DB_NAME;AUTO_SERVER=TRUE"
TOOLS_DIR="$HOME/.local/share"   # where setup.sh --install-tools puts a JDK and Node (no sudo)
MIN_JAVA=21

# ---------- output ----------
if [[ -t 1 ]]; then
  BOLD=$'\e[1m'; DIM=$'\e[2m'; RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; RESET=$'\e[0m'
else
  BOLD=""; DIM=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""
fi
step() { printf '%s==>%s %s\n' "$BLUE$BOLD" "$RESET" "$*"; }
ok()   { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$*"; }
info() { printf '  %s·%s %s\n' "$DIM" "$RESET" "$*"; }
die()  { printf '%s✖ %s%s\n' "$RED$BOLD" "$*" "$RESET" >&2; exit 1; }

command_exists() { command -v "$1" >/dev/null 2>&1; }

port_in_use() { # true if something listens on localhost (IPv4 or IPv6)
  (echo >"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1 || (echo >"/dev/tcp/::1/$1") >/dev/null 2>&1
}

# a >= b for dotted versions, e.g. version_ge 24.22.1 24.15.0
version_ge() {
  [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n 1)" == "$2" ]]
}

# ---------- Java ----------
java_major() { # path-to-java
  "$1" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | awk -F. '{ print ($1 == "1") ? $2 : $1 }'
}

# Finds a JDK >= 21: $JAVA_HOME, java on PATH, then common install folders (Linux and macOS).
find_java_home() {
  local candidates=() dir java_path
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  if command_exists java; then
    java_path="$(command -v java)"
    java_path="$(readlink -f "$java_path" 2>/dev/null || echo "$java_path")"
    candidates+=("$(dirname "$(dirname "$java_path")")")
  fi
  for dir in "$TOOLS_DIR"/jdks/* "$TOOLS_DIR"/jdks/*/Contents/Home "$HOME"/.sdkman/candidates/java/current \
    /usr/lib/jvm/* /Library/Java/JavaVirtualMachines/*/Contents/Home "$HOME"/.jdks/*; do
    [[ -d "$dir" ]] && candidates+=("$dir")
  done
  for dir in "${candidates[@]}"; do
    if [[ -x "$dir/bin/java" ]]; then
      local major
      major="$(java_major "$dir/bin/java")"
      if [[ "$major" =~ ^[0-9]+$ ]] && ((major >= MIN_JAVA)); then
        echo "$dir"
        return 0
      fi
    fi
  done
  return 1
}

# Exports JAVA_HOME/PATH for a suitable JDK, or stops with install hints.
use_java() {
  JAVA_HOME="$(find_java_home)" || die "JDK $MIN_JAVA+ not found. Run ./setup.sh --install-tools, or install one (Ubuntu: sudo apt install openjdk-21-jdk · macOS: brew install openjdk@21)."
  export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
}

# ---------- Node ----------
# Uses a Node installed by setup.sh --install-tools when there is no suitable one on PATH.
add_local_node_to_path() {
  if [[ -x "$TOOLS_DIR/node/bin/node" ]]; then
    if ! command_exists node || ! node_version_ok "$(node -v | sed 's/^v//')"; then
      export PATH="$TOOLS_DIR/node/bin:$PATH"
    fi
  fi
}

# Angular 22 supports Node ^22.22.3, ^24.15.0 or >= 26.
node_version_ok() { # version without the leading v
  local v=$1 major=${1%%.*}
  { [[ $major == 22 ]] && version_ge "$v" 22.22.3; } ||
    { [[ $major == 24 ]] && version_ge "$v" 24.15.0; } ||
    ((major >= 26))
}

# ---------- database ----------
db_exists() { [[ -f "$DATA_DIR/$DB_NAME.mv.db" ]]; }

backend_running() {
  [[ -f "$RUN_DIR/backend.pid" ]] && kill -0 "$(cat "$RUN_DIR/backend.pid")" 2>/dev/null
}

# Path to the H2 jar the app uses (resolved once with Maven, then cached in .run/).
h2_jar() {
  local cache="$RUN_DIR/h2.classpath"
  if [[ ! -s "$cache" ]] || [[ ! -f "$(cat "$cache")" ]]; then
    mkdir -p "$RUN_DIR"
    (cd "$ROOT/backend" && chmod +x mvnw && ./mvnw -q -B dependency:build-classpath \
      -Dmdep.includeArtifactIds=h2 -Dmdep.outputFile="$cache") >/dev/null ||
      die "Could not resolve the H2 library with Maven (the first run needs network access)"
  fi
  cat "$cache"
}

h2_tool() { # e.g. h2_tool org.h2.tools.Script -url ...
  java -cp "$(h2_jar)" "$@"
}

# Prints "students courses enrollments" for the H2 database.
db_counts() {
  h2_tool org.h2.tools.Shell -url "$H2_URL" -user sa -password "" -sql \
    "SELECT (SELECT COUNT(*) FROM STUDENTS) AS S, (SELECT COUNT(*) FROM COURSES) AS C, (SELECT COUNT(*) FROM ENROLLMENTS) AS E" 2>/dev/null |
    awk -F'|' 'NR == 2 { gsub(/ /, ""); print $1, $2, $3 }'
}

describe_counts() { # "12 9 32" -> "12 students, 9 courses, 32 enrollments"
  # shellcheck disable=SC2086
  set -- $1
  echo "${1:-?} students, ${2:-?} courses, ${3:-?} enrollments"
}

newest_backup() { # newest h2-*.sql (or pg-*.sql with "pg")
  local prefix=${1:-h2}
  ls -1 "$BACKUP_DIR"/"$prefix"-*.sql 2>/dev/null | sort | tail -n 1
}

# Deletes the H2 database files (only inside backend/data; refuses to run with an empty path).
remove_db_files() {
  rm -f "${DATA_DIR:?}/${DB_NAME:?}".mv.db "${DATA_DIR:?}/${DB_NAME:?}".trace.db "${DATA_DIR:?}/${DB_NAME:?}".lock.db
}
