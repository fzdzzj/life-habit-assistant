#!/bin/bash
set -eu

echo "${BACKUP_CRON:-0 3 * * *} /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1" | crontab -

exec crond -n
