#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${MYSQL_PORT:=3306}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_KEEP_DAYS:=7}"

STAMP="$(date +%Y%m%d_%H%M%S)"
FILE="$BACKUP_DIR/${MYSQL_DATABASE}_${STAMP}.sql.gz"
TMP="$FILE.tmp"
mkdir -p "$BACKUP_DIR"
trap 'rm -f "$TMP"' EXIT

echo "[$(date '+%Y-%m-%d %H:%M:%S')] backup start -> $FILE"

MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
  --single-transaction --routines --triggers --hex-blob --no-tablespaces \
  "$MYSQL_DATABASE" > "$TMP"

gzip -f "$TMP"
mv "$TMP.gz" "$FILE"
gzip -t "$FILE"

find "$BACKUP_DIR" -maxdepth 1 -type f -name "${MYSQL_DATABASE}_*.sql.gz" \
  -mtime "+${BACKUP_KEEP_DAYS}" -delete

echo "[$(date '+%Y-%m-%d %H:%M:%S')] backup done: $(du -h "$FILE" | cut -f1)"
