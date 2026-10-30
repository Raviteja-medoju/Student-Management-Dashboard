#!/usr/bin/env bash
#
# Stops everything ./start.sh started: the local backend and frontend processes
# and the Docker Compose stack (PostgreSQL, backend, frontend).
#
#   ./stop.sh               Stop everything (the PostgreSQL data volume is kept)
#   ./stop.sh --clean       Also delete the Docker database volume and the local logs
#   ./stop.sh --help
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"
PORTS=(8080 4200)

if [[ -t 1 ]]; then
  BOLD=$'\e[1m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; RESET=$'\e[0m'
else
  BOLD=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""
fi
step() { printf '%s==>%s %s\n' "$BLUE$BOLD" "$RESET" "$*"; }
ok()   { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$*"; }

CLEAN=false
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=true ;;
    -h | --help) sed -n '3,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) printf 'Unknown option: %s (try ./stop.sh --help)\n' "$arg" >&2; exit 1 ;;
  esac
done

command_exists() { command -v "$1" >/dev/null 2>&1; }
port_in_use() { # true if something listens on localhost (IPv4 or IPv6)
  (echo >"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1 || (echo >"/dev/tcp/::1/$1") >/dev/null 2>&1
}

# Sends TERM to a whole process group (e.g. Maven + the JVM it forked), then KILL if needed.
stop_group() { # name pid
  local name=$1 pid=$2
  if ! kill -0 "$pid" 2>/dev/null; then
    return 1
  fi
  kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null
  fi
  ok "Stopped $name (pid $pid)"
}

# Safety net for processes left behind (e.g. a pid file was deleted): stop whatever listens on
# our ports, but only if its working directory is inside this project.
stop_leftovers_on_port() { # port
  local port=$1 pid cwd
  command_exists lsof || return 0
  for pid in $(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null); do
    cwd="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1)"
    if [[ "$cwd" == "$ROOT"* ]]; then
      kill -TERM "$pid" 2>/dev/null && ok "Stopped leftover process on port $port (pid $pid)"
    fi
  done
}

printf '\n%sStudent Management%s · stopping\n\n' "$BOLD" "$RESET"

# ---------- local processes ----------
step "Stopping local backend and frontend"
stopped_any=false
for name in frontend backend; do
  pid_file="$RUN_DIR/$name.pid"
  if [[ -f "$pid_file" ]]; then
    stop_group "$name" "$(cat "$pid_file")" && stopped_any=true
    rm -f "${pid_file:?}"
  fi
done
for port in "${PORTS[@]}"; do
  stop_leftovers_on_port "$port"
done
sleep 1
for port in "${PORTS[@]}"; do
  stop_leftovers_on_port "$port"
done
[[ "$stopped_any" == true ]] || ok "No local processes were running"

# ---------- docker ----------
step "Stopping Docker Compose stack"
if ! command_exists docker; then
  ok "Docker not installed; nothing to stop"
elif ! docker info >/dev/null 2>&1; then
  warn "Cannot talk to Docker (not running, or no permission). If you used ./start.sh docker with sudo,"
  warn "run: sudo ./stop.sh"
else
  if [[ -n "$(cd "$ROOT" && docker compose ps -a -q 2>/dev/null)" ]]; then
    if [[ "$CLEAN" == true ]]; then
      (cd "$ROOT" && docker compose down --volumes --remove-orphans) && ok "Containers and database volume removed"
    else
      (cd "$ROOT" && docker compose down --remove-orphans) && ok "Containers stopped (database volume kept; use --clean to delete it)"
    fi
  elif [[ "$CLEAN" == true ]]; then
    (cd "$ROOT" && docker compose down --volumes >/dev/null 2>&1) || true
    ok "No containers were running; database volume removed"
  else
    ok "No containers were running"
  fi
fi

# ---------- tidy up ----------
rm -f "${RUN_DIR:?}/mode"
if [[ "$CLEAN" == true ]]; then
  rm -rf "${RUN_DIR:?}"
  ok "Removed logs in .run/"
fi

busy=()
for port in "${PORTS[@]}"; do
  port_in_use "$port" && busy+=("$port")
done
if ((${#busy[@]})); then
  warn "Port(s) ${busy[*]} are still in use by another program (not started by ./start.sh)."
else
  printf '\n%s✔ Everything is stopped%s (ports %s are free)\n\n' "$GREEN$BOLD" "$RESET" "${PORTS[*]}"
fi
