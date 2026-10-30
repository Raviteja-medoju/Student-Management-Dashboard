#!/usr/bin/env bash
#
# Backs up the database into backups/ as a plain SQL file (schema + data).
#
#   ./backup.sh                  Back up the H2 database (works while the app is running)
#   ./backup.sh --label move     Add a label to the file name, e.g. h2-2026-10-29_171500-move.sql
#   ./backup.sh --postgres       Back up the Docker PostgreSQL database (./start.sh docker) instead
#   ./backup.sh --help
#
# Restore with ./restore.sh (newest backup) or ./restore.sh backups/<file>.sql
#
set -euo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/common.sh"

LABEL=""
POSTGRES=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --label) LABEL="-${2:?--label needs a value}"; shift ;;
    --postgres) POSTGRES=true ;;
    -h | --help) sed -n '3,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown option: $1 (try ./backup.sh --help)" ;;
  esac
  shift
done
LABEL="${LABEL//[^A-Za-z0-9_-]/}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y-%m-%d_%H%M%S)"

if [[ "$POSTGRES" == true ]]; then
  step "Backing up PostgreSQL (Docker)"
  command_exists docker && docker info >/dev/null 2>&1 || die "Cannot use Docker (not running, or no permission; try sudo)."
  FILE="$BACKUP_DIR/pg-$STAMP$LABEL.sql"
  if ! (cd "$ROOT" && docker compose exec -T db pg_dump -U student -d studentdb --clean --if-exists --no-owner) >"$FILE"; then
    rm -f "${FILE:?}"
    die "pg_dump failed. Is the Docker stack running (./start.sh docker)?"
  fi
  ok "Saved ${FILE#"$ROOT"/} ($(du -h "$FILE" | cut -f1))"
  exit 0
fi

step "Backing up the H2 database"
db_exists || die "No database yet (backend/data/$DB_NAME.mv.db). Start the app once with ./start.sh, or restore a backup."
use_java
FILE="$BACKUP_DIR/h2-$STAMP$LABEL.sql"
TMP="$FILE.part"
if ! h2_tool org.h2.tools.Script -url "$H2_URL" -user sa -password "" -script "$TMP"; then
  rm -f "${TMP:?}"
  die "Backup failed"
fi
COUNTS="$(db_counts || true)"
{
  echo "-- Student Management database backup (H2)"
  echo "-- Created: $(date '+%Y-%m-%d %H:%M:%S %Z') on $(hostname)"
  echo "-- Contents: $(describe_counts "$COUNTS")"
  echo "-- Restore with: ./restore.sh backups/$(basename "$FILE")"
  cat "$TMP"
} >"$FILE"
rm -f "${TMP:?}"

grep -q '"PUBLIC"."STUDENTS"' "$FILE" || die "The backup file looks incomplete: $FILE"
ok "Saved ${FILE#"$ROOT"/} ($(du -h "$FILE" | cut -f1)) · $(describe_counts "$COUNTS")"
