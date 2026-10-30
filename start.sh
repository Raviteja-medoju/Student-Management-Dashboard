#!/usr/bin/env bash
#
# Starts the whole Student Management stack and waits until it is ready.
#
#   ./start.sh              Start locally, or use Docker if local Node.js is unsupported
#   ./start.sh local        Local mode: Spring Boot API (embedded H2) + Angular dev server
#   ./start.sh docker       Docker mode: PostgreSQL + API + nginx frontend via Docker Compose
#   ./start.sh --open       Also open the app in your browser (works with either mode)
#   ./start.sh --help
#
# Stop everything with ./stop.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"            # pid files and logs (git-ignored)
BACKEND_PORT=8080
FRONTEND_PORT=4200
DB_PORT=5432
APP_URL="http://localhost:$FRONTEND_PORT"
API_URL="http://localhost:$BACKEND_PORT"
READY_URL="$API_URL/actuator/health/readiness"   # UP only after demo data is loaded
MIN_JAVA=21

# ---------- output helpers ----------
if [[ -t 1 ]]; then
  BOLD=$'\e[1m'; DIM=$'\e[2m'; RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; BLUE=$'\e[34m'; RESET=$'\e[0m'
else
  BOLD=""; DIM=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""
fi
step() { printf '%s==>%s %s\n' "$BLUE$BOLD" "$RESET" "$*"; }
ok()   { printf '  %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$RESET" "$*"; }
die()  { printf '%s✖ %s%s\n' "$RED$BOLD" "$*" "$RESET" >&2; exit 1; }

usage() {
  sed -n '3,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 0
}

# ---------- arguments ----------
MODE=auto
OPEN_BROWSER=false
for arg in "$@"; do
  case "$arg" in
    local) MODE=local ;;
    docker | --docker) MODE=docker ;;
    --open) OPEN_BROWSER=true ;;
    -h | --help) usage ;;
    *) die "Unknown option: $arg (try ./start.sh --help)" ;;
  esac
done

mkdir -p "$RUN_DIR"

# ---------- generic helpers ----------
command_exists() { command -v "$1" >/dev/null 2>&1; }

add_local_node_to_path() {
  if [[ -x "$HOME/.local/share/node/bin/node" ]]; then
    export PATH="$HOME/.local/share/node/bin:$PATH"
  fi
}

node_version_supported() {
  local version node_major node_minor node_patch
  version="$(node -v | sed 's/^v//')"
  IFS=. read -r node_major node_minor node_patch <<<"$version"
  [[ "$node_major" =~ ^[0-9]+$ && "$node_minor" =~ ^[0-9]+$ && "$node_patch" =~ ^[0-9]+$ ]] || return 1
  node_minor=$((10#$node_minor))
  node_patch=$((10#$node_patch))

  case "$node_major" in
    22) ((node_minor > 22 || (node_minor == 22 && node_patch >= 3))) ;;
    24) ((node_minor >= 15)) ;;
    26) return 0 ;;
    *) return 1 ;;
  esac
}

port_in_use() { # true if something listens on localhost (IPv4 or IPv6)
  (echo >"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1 || (echo >"/dev/tcp/::1/$1") >/dev/null 2>&1
}

is_running() { # pidfile
  [[ -f "$1" ]] && kill -0 "$(cat "$1")" 2>/dev/null
}

# Waits until a URL answers 2xx. Fails early if the process we started has died.
wait_for_url() { # name url timeout_seconds [pid] [logfile]
  local name=$1 url=$2 timeout=$3 pid=${4:-} log=${5:-}
  local started=$SECONDS
  printf '  waiting for %s ' "$name"
  until curl -fsS -o /dev/null --max-time 3 "$url" 2>/dev/null; do
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      printf '\n'
      warn "$name stopped unexpectedly. Last lines of $log:"
      tail -n 25 "$log" | sed 's/^/      /'
      die "$name failed to start"
    fi
    if ((SECONDS - started > timeout)); then
      printf '\n'
      die "$name was not ready after ${timeout}s. Check ${log:-the logs}"
    fi
    printf '.'
    sleep 2
  done
  printf ' %sready%s (%ss)\n' "$GREEN" "$RESET" "$((SECONDS - started))"
}

open_browser() {
  if [[ "$OPEN_BROWSER" != true ]]; then return; fi
  if command_exists xdg-open; then xdg-open "$APP_URL" >/dev/null 2>&1 &
  elif command_exists open; then open "$APP_URL"
  else warn "Could not open a browser automatically; visit $APP_URL"
  fi
}

print_summary() {
  local db_line=$1 logs_line=$2
  printf '\n%s🎓 Student Management is running%s  (%s mode)\n\n' "$GREEN$BOLD" "$RESET" "$MODE"
  printf '  %-14s %s\n' "App" "$APP_URL"
  printf '  %-14s %s\n' "REST API" "$API_URL/api/students"
  printf '  %-14s %s\n' "Swagger UI" "$API_URL/swagger-ui.html"
  printf '  %-14s %s\n' "Database" "$db_line"
  printf '  %-14s %s\n' "Logs" "$logs_line"
  printf '\n  Stop everything with %s./stop.sh%s\n\n' "$BOLD" "$RESET"
}

# ---------- local mode ----------
java_major() { # path-to-java
  "$1" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | awk -F. '{ print ($1 == "1") ? $2 : $1 }'
}

# Finds a JDK >= 21: $JAVA_HOME, then java on PATH, then common install folders.
find_java_home() {
  local candidates=()
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
  if command_exists java; then
    local java_path
    java_path="$(command -v java)"
    java_path="$(readlink -f "$java_path" 2>/dev/null || echo "$java_path")"
    candidates+=("$(dirname "$(dirname "$java_path")")")
  fi
  local dir
  for dir in "$HOME"/.local/share/jdks/* "$HOME"/.local/share/jdks/*/Contents/Home \
    "$HOME"/.sdkman/candidates/java/current /usr/lib/jvm/* \
    /Library/Java/JavaVirtualMachines/*/Contents/Home "$HOME"/.jdks/*; do
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

start_local() {
  local backend_pid_file="$RUN_DIR/backend.pid" frontend_pid_file="$RUN_DIR/frontend.pid"
  local backend_log="$RUN_DIR/backend.log" frontend_log="$RUN_DIR/frontend.log"

  step "Checking prerequisites"
  command_exists curl || die "curl is required"

  JAVA_HOME="$(find_java_home)" || die "JDK $MIN_JAVA or newer not found.
   Install one (Ubuntu: sudo apt install openjdk-21-jdk · macOS: brew install openjdk@21 · or https://adoptium.net)
   and/or set JAVA_HOME. No Java? With Docker installed you can run: ./start.sh docker"
  export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
  ok "Java $(java_major "$JAVA_HOME/bin/java") ($JAVA_HOME)"

  add_local_node_to_path
  command_exists node || die "Node.js 22.22.3+, 24.15+, or 26+ is required. Install a supported version, or run ./start.sh docker."
  node_version_supported || die "Node.js $(node -v) is not supported by Angular 22 (needs 22.22.3+, 24.15+, or 26+). Install a supported version, or run ./start.sh docker."
  ok "Node $(node -v), npm $(npm -v)"

  # Already running from a previous ./start.sh?
  if is_running "$backend_pid_file" && is_running "$frontend_pid_file"; then
    ok "Already running"
    print_summary "H2 file backend/data/studentdb.mv.db" "$RUN_DIR/*.log"
    open_browser
    return
  fi

  for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
    if port_in_use "$port"; then
      die "Port $port is already in use. Run ./stop.sh (it also stops the Docker stack), or free the port."
    fi
  done

  if [[ ! -d "$ROOT/frontend/node_modules" || "$ROOT/frontend/package-lock.json" -nt "$ROOT/frontend/node_modules/.package-lock.json" ]]; then
    step "Installing frontend dependencies (first run only)"
    (cd "$ROOT/frontend" && npm ci --no-audit --no-fund --loglevel=error)
    ok "Dependencies installed"
  fi

  # set -m gives each background job its own process group, so stop.sh can stop
  # Maven *and* the JVM it forks (or npm *and* the Angular dev server) in one go.
  set -m

  # The H2 database file lives here; ./backup.sh and ./restore.sh use the same place.
  export APP_DATA_DIR="$ROOT/backend/data"
  mkdir -p "$APP_DATA_DIR"

  step "Starting backend (Spring Boot, port $BACKEND_PORT)"
  chmod +x "$ROOT/backend/mvnw"
  (cd "$ROOT/backend" && exec nohup ./mvnw -q -B spring-boot:run) >"$backend_log" 2>&1 &
  echo $! >"$backend_pid_file"
  ok "Started (pid $(cat "$backend_pid_file")); the first run downloads Maven and dependencies"

  step "Starting frontend (Angular dev server, port $FRONTEND_PORT)"
  (cd "$ROOT/frontend" && export NG_CLI_ANALYTICS=false && exec nohup npm start -- --port "$FRONTEND_PORT") >"$frontend_log" 2>&1 &
  echo $! >"$frontend_pid_file"
  ok "Started (pid $(cat "$frontend_pid_file"))"

  set +m

  step "Waiting for services"
  wait_for_url "backend" "$READY_URL" 600 "$(cat "$backend_pid_file")" "$backend_log"
  wait_for_url "frontend" "$APP_URL" 300 "$(cat "$frontend_pid_file")" "$frontend_log"
  wait_for_url "API through the frontend proxy" "$APP_URL/api/stats" 60

  echo local >"$RUN_DIR/mode"
  print_summary "H2 file backend/data/studentdb.mv.db (back up with ./backup.sh)" "$RUN_DIR/backend.log, $RUN_DIR/frontend.log"
  open_browser
}

# ---------- docker mode ----------
start_docker() {
  step "Checking Docker"
  command_exists curl || die "curl is required"
  command_exists docker || die "Docker is not installed (https://docs.docker.com/get-docker/)"
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required (the 'docker compose' command)"

  local docker_error
  if ! docker_error="$(docker info 2>&1 >/dev/null)"; then
    if grep -qi "permission denied" <<<"$docker_error"; then
      die "No permission to use Docker. Either add yourself to the docker group:
      sudo usermod -aG docker \$USER   (then log out and back in)
   or run: sudo ./start.sh docker"
    fi
    die "Docker is not running. Start Docker and try again."
  fi
  ok "$(docker compose version)"

  if [[ -f "$RUN_DIR/backend.pid" ]] && is_running "$RUN_DIR/backend.pid"; then
    die "The local-mode stack is running. Stop it first with ./stop.sh"
  fi
  local running
  running="$(cd "$ROOT" && docker compose ps --status running -q 2>/dev/null || true)"
  if [[ -z "$running" ]]; then
    for port in "$DB_PORT" "$BACKEND_PORT" "$FRONTEND_PORT"; do
      port_in_use "$port" && die "Port $port is already in use. Run ./stop.sh, or free the port."
    done
  fi

  step "Building and starting containers (PostgreSQL, backend, frontend)"
  (cd "$ROOT" && docker compose up --build --detach)

  step "Waiting for services"
  wait_for_url "backend" "$READY_URL" 600 "" "docker compose logs backend"
  wait_for_url "frontend" "$APP_URL" 120 "" "docker compose logs frontend"
  wait_for_url "API through nginx" "$APP_URL/api/stats" 60

  echo docker >"$RUN_DIR/mode"
  print_summary "PostgreSQL in Docker (localhost:$DB_PORT, db/user/password: studentdb/student/student)" \
    "docker compose logs -f"
  open_browser
}

if [[ "$MODE" == auto ]]; then
  add_local_node_to_path
  if command_exists node && node_version_supported; then
    MODE=local
  else
    if command_exists node; then
      warn "Node $(node -v) cannot run Angular 22; using Docker instead"
    else
      warn "Node.js is not installed; using Docker instead"
    fi
    MODE=docker
  fi
fi
printf '\n%sStudent Management%s · starting (%s mode)\n\n' "$BOLD" "$RESET" "$MODE"
case "$MODE" in
  local) start_local ;;
  docker) start_docker ;;
esac
