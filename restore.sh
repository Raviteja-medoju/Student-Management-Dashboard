#!/usr/bin/env bash
#
# Restores a backup made by ./backup.sh. The current data is backed up first,
# so a restore can always be undone with another ./restore.sh.
#
#   ./restore.sh                        Restore the newest backup in backups/
#   ./restore.sh backups/<file>.sql     Restore a specific backup (h2-*.sql or pg-*.sql)
#   ./restore.sh --yes                  Don't ask for confirmation
#   ./restore.sh --help
#
# If the app is running it is stopped for the restore and started again afterwards.
#
set -euo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/common.sh"

FILE=""
YES=false
for arg in "$@"; do
  case "$arg" in
    -y | --yes) YES=true ;;
    -h | --help) sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "Unknown option: $arg (try ./restore.sh --help)" ;;
    *) FILE="$arg" ;;
  esac
done

if [[ -z "$FILE" ]]; then
  FILE="$(newest_backup h2)"
  [[ -n "$FILE" ]] || die "No backups found in backups/. Make one with ./backup.sh"
fi
[[ -f "$FILE" ]] || die "Backup not found: $FILE"
FILE="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"
NAME="$(basename "$FILE")"

confirm() {
  [[ "$YES" == true ]] && return 0
  if [[ -t 0 ]]; then
    read -r -p "  Replace the current database with $NAME? [y/N] " answer
    [[ "$answer" =~ ^[Yy] ]] || die "Cancelled"
  else
    die "Restoring replaces the current data. Re-run with --yes to confirm."
  fi
}

# ---------- PostgreSQL (Docker) ----------
if [[ "$NAME" == pg-* ]]; then
  step "Restoring $NAME into PostgreSQL (Docker)"
  command_exists docker && docker info >/dev/null 2>&1 || die "Cannot use Docker (not running, or no permission; try sudo)."
  confirm
  "$ROOT/backup.sh" --postgres --label pre-restore || warn "Could not back up the current PostgreSQL data first"
  (cd "$ROOT" && docker compose exec -T db psql -q -U student -d studentdb -v ON_ERROR_STOP=1) <"$FILE" ||
    die "Restore failed. Is the Docker stack running (./start.sh docker)?"
  (cd "$ROOT" && docker compose restart backend >/dev/null)
  ok "Restored $NAME and restarted the backend container"
  exit 0
fi

# ---------- H2 ----------
step "Restoring $NAME"
grep -q '"PUBLIC"."STUDENTS"' "$FILE" || die "$NAME does not look like a Student Management H2 backup"
use_java
if db_exists; then
  confirm
fi

WAS_RUNNING=false
if backend_running; then
  WAS_RUNNING=true
  info "Stopping the app for the restore"
  "$ROOT/stop.sh" >/dev/null 2>&1 || true
fi

if db_exists; then
  "$ROOT/backup.sh" --label pre-restore
fi

mkdir -p "$DATA_DIR"
remove_db_files
h2_tool org.h2.tools.RunScript -url "jdbc:h2:file:$DATA_DIR/$DB_NAME" -user sa -password "" -script "$FILE" ||
  die "Restore failed; your previous data is in the newest *-pre-restore.sql backup"
ok "Restored · $(describe_counts "$(db_counts || true)")"

if [[ "$WAS_RUNNING" == true ]]; then
  info "Starting the app again"
  "$ROOT/start.sh"
else
  info "Start the app with ./start.sh"
fi
