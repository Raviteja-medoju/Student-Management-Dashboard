#!/usr/bin/env bash
#
# One-time setup on a new machine. Safe to run again.
#
#   ./setup.sh                   Check the machine, install project dependencies, build, and restore the
#                                newest backup from backups/ if there is no database yet
#   ./setup.sh --install-tools   Also install a missing JDK 21 / Node 24 into ~/.local/share (no sudo)
#   ./setup.sh --fresh           Don't restore a backup; start with demo data instead
#   ./setup.sh --test            Also run the backend and frontend test suites
#   ./setup.sh --start           Start the app when setup is done
#   ./setup.sh --help
#
set -euo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/common.sh"

INSTALL_TOOLS=false; FRESH=false; RUN_TESTS=false; START=false
for arg in "$@"; do
  case "$arg" in
    --install-tools) INSTALL_TOOLS=true ;;
    --fresh) FRESH=true ;;
    --test) RUN_TESTS=true ;;
    --start) START=true ;;
    -h | --help) sed -n '3,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown option: $arg (try ./setup.sh --help)" ;;
  esac
done

printf '\n%sStudent Management%s · setup\n\n' "$BOLD" "$RESET"
chmod +x "$ROOT"/*.sh "$ROOT"/scripts/*.sh "$ROOT/backend/mvnw" 2>/dev/null || true

# ---------- 1. toolchain ----------
step "Toolchain"
if ! find_java_home >/dev/null; then
  [[ "$INSTALL_TOOLS" == true ]] || die "JDK 21+ not found. Re-run with --install-tools, or install it yourself (see ./preflight.sh)."
  "$ROOT/scripts/install-tools.sh" jdk
fi
use_java
ok "Java $(java_major "$JAVA_HOME/bin/java") ($JAVA_HOME)"

add_local_node_to_path
if ! command_exists node || ! node_version_ok "$(node -v | sed 's/^v//')"; then
  [[ "$INSTALL_TOOLS" == true ]] || die "Node 22.22+/24.15+ not found. Re-run with --install-tools, or install Node 24 LTS yourself."
  "$ROOT/scripts/install-tools.sh" node
  export PATH="$TOOLS_DIR/node/bin:$PATH"
fi
ok "Node $(node -v), npm $(npm -v)"

# ---------- 2. preflight ----------
step "Preflight check"
mkdir -p "$RUN_DIR"
PREFLIGHT_LOG="$RUN_DIR/preflight.log"
"$ROOT/preflight.sh" >"$PREFLIGHT_LOG" 2>&1 || { cat "$PREFLIGHT_LOG"; die "Preflight found problems (see above)"; }
grep -E '(✔ Ready| ! )' "$PREFLIGHT_LOG" || true

# ---------- 3. dependencies and build ----------
step "Frontend dependencies (npm ci)"
(cd "$ROOT/frontend" && npm ci --no-audit --no-fund --loglevel=error)
ok "Installed"

step "Backend build (Maven wrapper; the first run downloads Maven and libraries)"
(cd "$ROOT/backend" && ./mvnw -q -B -DskipTests package)
h2_jar >/dev/null
ok "Built backend/target/$(cd "$ROOT/backend/target" && ls student-management-*.jar | head -n 1)"

# ---------- 4. database ----------
step "Database"
NEWEST="$(newest_backup h2)"
if db_exists; then
  ok "Keeping the existing database (backend/data/$DB_NAME.mv.db) · $(describe_counts "$(db_counts || true)")"
elif [[ "$FRESH" == true || -z "$NEWEST" ]]; then
  ok "No backup restored: the app will load demo data on first start"
else
  "$ROOT/restore.sh" --yes "$NEWEST"
fi

# ---------- 5. tests (optional) ----------
if [[ "$RUN_TESTS" == true ]]; then
  step "Backend tests"
  (cd "$ROOT/backend" && ./mvnw -q -B verify) && ok "Backend tests passed"
  step "Frontend tests"
  (cd "$ROOT/frontend" && npm run --silent test:ci >/dev/null) && ok "Frontend tests passed"
fi

printf '\n%s✔ Setup complete%s\n' "$GREEN$BOLD" "$RESET"
if [[ "$START" == true ]]; then
  "$ROOT/start.sh"
else
  printf '  Start the app with %s./start.sh%s · stop with %s./stop.sh%s · back up with %s./backup.sh%s\n\n' \
    "$BOLD" "$RESET" "$BOLD" "$RESET" "$BOLD" "$RESET"
fi
