#!/bin/sh
set -eu

HEALTH_FILE="${BASKOV_HEALTH_FILE:-/tmp/baskov-discord-bot.ready}"
MAX_AGE_SECONDS="${BASKOV_HEALTH_MAX_AGE_SECONDS:-45}"

case "${MAX_AGE_SECONDS}" in
  ''|*[!0-9]*) exit 1 ;;
esac

[ -s "${HEALTH_FILE}" ] || exit 1
grep -qx 'status=CONNECTED' "${HEALTH_FILE}" || exit 1

now="$(date +%s)"
modified="$(stat -c %Y "${HEALTH_FILE}")"
age=$((now - modified))

[ "${age}" -ge 0 ] && [ "${age}" -le "${MAX_AGE_SECONDS}" ]
