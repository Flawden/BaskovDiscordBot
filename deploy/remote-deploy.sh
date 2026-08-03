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

DISCORD_BOT_TOKEN="$(printf '%s' "${DISCORD_BOT_TOKEN_B64}" | base64 --decode)"
if [[ -z "${DISCORD_BOT_TOKEN}" ]]; then
  echo "Decoded Discord token is empty" >&2
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
