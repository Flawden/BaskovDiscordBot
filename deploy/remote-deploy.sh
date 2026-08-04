#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR="${1:?Deployment directory is required}"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
INPUT_FILE="${DEPLOY_DIR}/.deploy-input"
ENV_FILE="${DEPLOY_DIR}/.env"
PREVIOUS_ENV_FILE="${DEPLOY_DIR}/.env.previous"

cleanup() {
  rm -f "${INPUT_FILE}"
}
trap cleanup EXIT

if [[ ! -f "${COMPOSE_FILE}" || ! -f "${INPUT_FILE}" ]]; then
  echo "Deployment files are missing" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${INPUT_FILE}"
: "${BOT_IMAGE:?BOT_IMAGE is missing}"
: "${BOT_CONTAINER_NAME:?BOT_CONTAINER_NAME is missing}"
: "${DISCORD_BOT_TOKEN_B64:?DISCORD_BOT_TOKEN_B64 is missing}"
: "${DISCORD_BOT_PREFIX_B64:?DISCORD_BOT_PREFIX_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64:?DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64:?DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64 is missing}"
: "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64:?DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64:?DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_VOLUME_B64:?DISCORD_BOT_MUSIC_MAX_VOLUME_B64 is missing}"

decode() {
  printf '%s' "$1" | base64 --decode
}

DISCORD_BOT_TOKEN="$(decode "${DISCORD_BOT_TOKEN_B64}")"
DISCORD_BOT_PREFIX="$(decode "${DISCORD_BOT_PREFIX_B64}")"
DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE="$(decode "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64}")"
DISCORD_BOT_MUSIC_MAX_TRACK_DURATION="$(decode "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64}")"
DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT="$(decode "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64}")"
DISCORD_BOT_MUSIC_DEFAULT_VOLUME="$(decode "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64}")"
DISCORD_BOT_MUSIC_MAX_VOLUME="$(decode "${DISCORD_BOT_MUSIC_MAX_VOLUME_B64}")"

if [[ -z "${DISCORD_BOT_TOKEN}" ]]; then
  echo "Decoded Discord token is empty" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_PREFIX}" =~ ^[^[:space:]#=]{1,5}$ ]]; then
  echo "Discord bot prefix must contain 1-5 characters without whitespace, # or =" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE < 1 || DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE > 1000 )); then
  echo "Music max queue size must be between 1 and 1000" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION}" =~ ^[1-9][0-9]*(ms|s|m|h|d)$ ]]; then
  echo "Music max track duration must be a positive Spring duration such as 30m or 4h" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT}" =~ ^[0-9]+(ms|s|m|h|d)$ ]]; then
  echo "Music idle timeout must be a non-negative Spring duration such as 0s or 5m" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_MAX_VOLUME}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_MUSIC_MAX_VOLUME < 1 || DISCORD_BOT_MUSIC_MAX_VOLUME > 500 )); then
  echo "Music max volume must be between 1 and 500" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_MUSIC_DEFAULT_VOLUME < 0 \
      || DISCORD_BOT_MUSIC_DEFAULT_VOLUME > DISCORD_BOT_MUSIC_MAX_VOLUME )); then
  echo "Music default volume must be between 0 and max volume" >&2
  exit 1
fi

cd "${DEPLOY_DIR}"
umask 077

if [[ -f "${ENV_FILE}" ]]; then
  cp "${ENV_FILE}" "${PREVIOUS_ENV_FILE}"
else
  rm -f "${PREVIOUS_ENV_FILE}"
fi

write_env() {
  local image="$1"
  local token="$2"
  local temp_file
  temp_file="$(mktemp "${DEPLOY_DIR}/.env.XXXXXX")"
  {
    printf 'BOT_IMAGE=%s\n' "${image}"
    printf 'BOT_CONTAINER_NAME=%s\n' "${BOT_CONTAINER_NAME}"
    printf 'DISCORD_BOT_TOKEN=%s\n' "${token}"
    printf 'DISCORD_BOT_PREFIX=%s\n' "${DISCORD_BOT_PREFIX}"
    printf 'DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE=%s\n' "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE}"
    printf 'DISCORD_BOT_MUSIC_MAX_TRACK_DURATION=%s\n' "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION}"
    printf 'DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT=%s\n' "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT}"
    printf 'DISCORD_BOT_MUSIC_DEFAULT_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME}"
    printf 'DISCORD_BOT_MUSIC_MAX_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_MAX_VOLUME}"
  } > "${temp_file}"
  chmod 600 "${temp_file}"
  mv "${temp_file}" "${ENV_FILE}"
}

rollback() {
  echo "Deployment verification failed; attempting rollback" >&2
  if [[ ! -s "${PREVIOUS_ENV_FILE}" ]]; then
    echo "No previous deployment state is available" >&2
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=200 bot || true
    return 1
  fi

  cp "${PREVIOUS_ENV_FILE}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" pull bot
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans bot

  for _ in {1..24}; do
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${BOT_CONTAINER_NAME}" 2>/dev/null || true)"
    if [[ "${health}" == "healthy" ]]; then
      echo "Rollback succeeded"
      return 0
    fi
    sleep 5
  done

  echo "Rollback did not become healthy" >&2
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=200 bot || true
  return 1
}

write_env "${BOT_IMAGE}" "${DISCORD_BOT_TOKEN}"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" pull bot
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans bot

for _ in {1..24}; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${BOT_CONTAINER_NAME}" 2>/dev/null || true)"
  case "${health}" in
    healthy)
      echo "Deployment is healthy: ${BOT_IMAGE}"
      docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true
      exit 0
      ;;
    unhealthy|exited|dead)
      break
      ;;
  esac
  sleep 5
done

rollback
exit 1
