#!/usr/bin/env bash
#
# Packs the project for moving to another machine: a fresh database backup plus the source,
# without anything that can be re-downloaded or rebuilt (node_modules, target, the live DB file).
#
#   ./pack.sh               Back up the database, then write ../<folder>-YYYY-MM-DD_HHMM.tar.gz
#   ./pack.sh --no-backup   Pack without making a new backup
#   ./pack.sh --help
#
# On the new machine:  tar -xzf <archive> && cd <folder> && ./setup.sh --install-tools --start
#
set -euo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/common.sh"

BACKUP=true
for arg in "$@"; do
  case "$arg" in
    --no-backup) BACKUP=false ;;
    -h | --help) sed -n '3,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown option: $arg (try ./pack.sh --help)" ;;
  esac
done

if [[ "$BACKUP" == true ]] && db_exists; then
  "$ROOT/backup.sh" --label pack
fi

FOLDER="$(basename "$ROOT")"
PARENT="$(dirname "$ROOT")"
ARCHIVE="$PARENT/$FOLDER-$(date +%Y-%m-%d_%H%M).tar.gz"

step "Packing $FOLDER"
tar -C "$PARENT" -czf "$ARCHIVE" \
  --exclude="$FOLDER/frontend/node_modules" \
  --exclude="$FOLDER/frontend/dist" \
  --exclude="$FOLDER/frontend/.angular" \
  --exclude="$FOLDER/backend/target" \
  --exclude="$FOLDER/backend/data" \
  --exclude="$FOLDER/.run" \
  "$FOLDER"

ok "Wrote $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1)) · includes $(ls -1 "$BACKUP_DIR"/*.sql 2>/dev/null | wc -l | tr -d ' ') backup(s)"
printf '\n  On the new machine:\n    tar -xzf %s\n    cd %s\n    ./setup.sh --install-tools --start\n\n' "$(basename "$ARCHIVE")" "$FOLDER"
