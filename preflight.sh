#!/usr/bin/env bash
#
# Checks that this machine can build and run Student Management, without changing anything.
#
#   ./preflight.sh          Full report; exits with 1 if something required is missing
#   ./preflight.sh --help
#
# Fix what it reports, or run ./setup.sh --install-tools to install a JDK and Node for you.
#
set -uo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/common.sh"

case "${1:-}" in
  -h | --help) sed -n '3,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

PASS=0; WARNS=0; FAILS=0
pass() { printf '  %s✔ %-16s%s %s\n' "$GREEN" "$1" "$RESET" "$2"; PASS=$((PASS + 1)); }
warning() { printf '  %s! %-16s%s %s\n' "$YELLOW" "$1" "$RESET" "$2"; WARNS=$((WARNS + 1)); }
fail() { printf '  %s✖ %-16s%s %s\n' "$RED" "$1" "$RESET" "$2"; FAILS=$((FAILS + 1)); }
note() { printf '  %s· %-16s%s %s\n' "$DIM" "$1" "$RESET" "$2"; }

printf '\n%sStudent Management%s · preflight check for %s\n\n' "$BOLD" "$RESET" "$(hostname)"

# ---------- system ----------
step "System"
case "$(uname -s)" in
  Linux | Darwin) pass "OS" "$(uname -s) $(uname -m)" ;;
  MINGW* | MSYS* | CYGWIN*) warning "OS" "$(uname -s): use WSL for the scripts, or run the manual steps in the README" ;;
  *) warning "OS" "$(uname -s) is untested" ;;
esac
pass "bash" "$BASH_VERSION"
command_exists curl && pass "curl" "$(curl --version | head -n 1 | cut -d' ' -f1-2)" || fail "curl" "required (sudo apt install curl)"
command_exists tar && pass "tar" "available" || warning "tar" "needed only for ./setup.sh --install-tools and ./pack.sh"

FREE_KB="$(df -Pk "$ROOT" | awk 'NR == 2 {print $4}')"
if [[ -n "$FREE_KB" ]] && ((FREE_KB > 2 * 1024 * 1024)); then
  pass "Disk space" "$((FREE_KB / 1024 / 1024)) GB free"
else
  warning "Disk space" "$((${FREE_KB:-0} / 1024)) MB free; about 2 GB is needed for dependencies"
fi

# ---------- toolchain ----------
step "Toolchain"
if JH="$(find_java_home)"; then
  pass "Java" "$(java_major "$JH/bin/java") at $JH"
else
  fail "Java" "JDK $MIN_JAVA+ not found → ./setup.sh --install-tools (or: sudo apt install openjdk-21-jdk / brew install openjdk@21)"
fi

add_local_node_to_path
if command_exists node; then
  NODE_V="$(node -v | sed 's/^v//')"
  if node_version_ok "$NODE_V"; then
    pass "Node.js" "v$NODE_V at $(command -v node)"
  else
    fail "Node.js" "v$NODE_V is not supported by Angular 22 (needs 22.22.3+, 24.15+ or 26+) → ./setup.sh --install-tools"
  fi
  command_exists npm && pass "npm" "$(npm -v)" || fail "npm" "missing (comes with Node.js)"
else
  fail "Node.js" "not found → ./setup.sh --install-tools (or install Node 24 LTS from nodejs.org)"
fi

# ---------- network ----------
step "Network (needed the first time, to download dependencies)"
for target in "Maven Central|https://repo.maven.apache.org/maven2/" "npm registry|https://registry.npmjs.org/" \
  "Adoptium (JDK)|https://api.adoptium.net/v3/info/available_releases" "nodejs.org|https://nodejs.org/dist/index.json"; do
  label="${target%%|*}"; url="${target#*|}"
  if curl -fsS -o /dev/null --max-time 8 "$url" 2>/dev/null; then
    pass "$label" "reachable"
  else
    warning "$label" "not reachable (fine only if everything is already downloaded)"
  fi
done

# ---------- project ----------
step "Project"
for f in backend/pom.xml backend/mvnw frontend/package.json frontend/package-lock.json start.sh stop.sh; do
  [[ -f "$ROOT/$f" ]] || fail "Files" "missing $f (is this a complete copy?)"
done
((FAILS == 0)) && pass "Files" "backend, frontend and scripts present"
[[ -x "$ROOT/backend/mvnw" && -x "$ROOT/start.sh" ]] && pass "Permissions" "scripts are executable" ||
  warning "Permissions" "scripts lost their executable flag → chmod +x *.sh scripts/*.sh backend/mvnw (setup.sh does this)"
[[ -d "$ROOT/frontend/node_modules" ]] && note "Frontend deps" "installed" || note "Frontend deps" "not installed yet (setup.sh runs npm ci)"
[[ -d "$ROOT/backend/target/classes" ]] && note "Backend build" "built" || note "Backend build" "not built yet (setup.sh builds it)"

# ---------- data ----------
step "Database"
if db_exists; then
  pass "Database" "backend/data/$DB_NAME.mv.db ($(du -h "$DATA_DIR/$DB_NAME.mv.db" | cut -f1))"
else
  note "Database" "none yet"
fi
BACKUP_COUNT="$(ls -1 "$BACKUP_DIR"/*.sql 2>/dev/null | wc -l | tr -d ' ')"
NEWEST="$(newest_backup h2)"
if [[ -n "$NEWEST" ]]; then
  CONTENTS="$(sed -n 's/^-- Contents: //p' "$NEWEST" | head -n 1)"
  pass "Backups" "$BACKUP_COUNT file(s); newest: backups/$(basename "$NEWEST")${CONTENTS:+ · $CONTENTS}"
  db_exists || note "On setup" "./setup.sh will restore backups/$(basename "$NEWEST")"
else
  note "Backups" "none (the app starts with demo data)"
fi

# ---------- ports ----------
step "Ports"
for port in 8080 4200; do
  if port_in_use "$port"; then
    if backend_running; then
      pass "Port $port" "in use by this app (already running)"
    else
      warning "Port $port" "in use by another program; stop it before ./start.sh"
    fi
  else
    pass "Port $port" "free"
  fi
done

# ---------- optional ----------
step "Optional: Docker (only for ./start.sh docker)"
if ! command_exists docker; then
  note "Docker" "not installed"
elif docker info >/dev/null 2>&1; then
  docker compose version >/dev/null 2>&1 && pass "Docker" "$(docker compose version | head -n 1)" ||
    warning "Docker" "Docker Compose v2 missing"
else
  note "Docker" "installed, but this user can't use it (sudo usermod -aG docker \$USER, then log in again)"
fi

printf '\n'
if ((FAILS > 0)); then
  printf '%s✖ %d problem(s) to fix%s, %d warning(s), %d passed.\n' "$RED$BOLD" "$FAILS" "$RESET" "$WARNS" "$PASS"
  printf '  Missing Java or Node? Run %s./setup.sh --install-tools%s (installs into ~/.local/share, no sudo).\n\n' "$BOLD" "$RESET"
  exit 1
fi
printf '%s✔ Ready%s · %d passed, %d warning(s). Next: %s./setup.sh%s\n\n' "$GREEN$BOLD" "$RESET" "$PASS" "$WARNS" "$BOLD" "$RESET"
