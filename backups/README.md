# Database backups

`./backup.sh` writes plain SQL files here (schema + data), named by date and time:

- `h2-YYYY-MM-DD_HHMMSS[-label].sql`: the default H2 database (`backend/data/studentdb.mv.db`)
- `pg-YYYY-MM-DD_HHMMSS[-label].sql`: the Docker PostgreSQL database (`./backup.sh --postgres`)

Restore with `./restore.sh` (newest `h2-*` backup) or `./restore.sh backups/<file>.sql`.
A restore first saves the current data as `*-pre-restore.sql`, so it can be undone.

The `.sql` files are ignored by git (they contain real data) but are included by `./pack.sh`,
so they travel to a new machine, where `./setup.sh` restores the newest one automatically.
