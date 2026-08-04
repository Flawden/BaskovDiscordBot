#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_DIR="${1:?Deployment directory is required}"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
HOST_COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.host-network.yml"
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
: "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64:?DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64:?DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64:?DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64:?DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64 is missing}"
: "${BOT_NETWORK_MODE_B64:?BOT_NETWORK_MODE_B64 is missing}"
: "${DISCORD_BOT_VOICE_LOG_LEVEL_B64:?DISCORD_BOT_VOICE_LOG_LEVEL_B64 is missing}"
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
DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT="$(decode "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64}")"
DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN="$(decode "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64}")"
DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE="$(decode "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64}")"
DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE="$(decode "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64}")"
BOT_NETWORK_MODE="$(decode "${BOT_NETWORK_MODE_B64}")"
DISCORD_BOT_VOICE_LOG_LEVEL="$(decode "${DISCORD_BOT_VOICE_LOG_LEVEL_B64}")"
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
if [[ ! "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT}" =~ ^[1-9][0-9]*(ms|s|m)$ ]]; then
  echo "Music voice connect timeout must be a positive duration such as 15s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN}" =~ ^[0-9]+(ms|s|m)$ ]]; then
  echo "Music voice failure cooldown must be a non-negative duration such as 30s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE}" =~ ^[1-9][0-9]*(ms|s|m)$ ]]; then
  echo "Music voice disconnect grace must be a positive duration such as 5s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE}" =~ ^(true|false)$ ]]; then
  echo "Voice watchdog enforce must be true or false" >&2
  exit 1
fi
if [[ ! "${BOT_NETWORK_MODE}" =~ ^(bridge|host)$ ]]; then
  echo "Bot network mode must be bridge or host" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_VOICE_LOG_LEVEL}" =~ ^(TRACE|DEBUG|INFO|WARN|ERROR|OFF)$ ]]; then
  echo "Discord voice log level is invalid" >&2
  exit 1
fi
if [[ "${BOT_NETWORK_MODE}" == "host" && ! -f "${HOST_COMPOSE_FILE}" ]]; then
  echo "Host-network compose override is missing" >&2
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

PREVIOUS_CONTAINER_RUNNING="$(
  docker inspect --format '{{.State.Running}}' "${BOT_CONTAINER_NAME}" 2>/dev/null || printf 'false'
)"
if [[ "${PREVIOUS_CONTAINER_RUNNING}" != "true" ]]; then
  PREVIOUS_CONTAINER_RUNNING="false"
fi

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
    printf 'DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT=%s\n' "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT}"
    printf 'DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN=%s\n' "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN}"
    printf 'DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE=%s\n' "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE}"
    printf 'DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=%s\n' "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE}"
    printf 'BOT_NETWORK_MODE=%s\n' "${BOT_NETWORK_MODE}"
    printf 'DISCORD_BOT_VOICE_LOG_LEVEL=%s\n' "${DISCORD_BOT_VOICE_LOG_LEVEL}"
    printf 'DISCORD_BOT_MUSIC_DEFAULT_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME}"
    printf 'DISCORD_BOT_MUSIC_MAX_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_MAX_VOLUME}"
  } > "${temp_file}"
  chmod 600 "${temp_file}"
  mv "${temp_file}" "${ENV_FILE}"
}


compose_with_env() {
  local env_file="$1"
  shift
  local mode
  mode="$(awk -F= '$1 == "BOT_NETWORK_MODE" {print $2; exit}' "${env_file}")"
  local args=(--env-file "${env_file}" -f "${COMPOSE_FILE}")
  if [[ "${mode:-bridge}" == "host" ]]; then
    args+=(-f "${HOST_COMPOSE_FILE}")
  fi
  docker compose "${args[@]}" "$@"
}

read_env_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted {sub(/^[^=]*=/, ""); print; exit}' "${ENV_FILE}"
}

verify_runtime() {
  local expected_image="$1"
  local actual_image restart_count actual_network_mode expected_network_mode

  actual_image="$(docker inspect --format '{{.Config.Image}}' "${BOT_CONTAINER_NAME}")"
  restart_count="$(docker inspect --format '{{.RestartCount}}' "${BOT_CONTAINER_NAME}")"
  actual_network_mode="$(docker inspect --format '{{.HostConfig.NetworkMode}}' "${BOT_CONTAINER_NAME}")"
  expected_network_mode="$(read_env_value BOT_NETWORK_MODE)"

  if [[ "${actual_image}" != "${expected_image}" ]]; then
    echo "Container image mismatch: expected ${expected_image}, got ${actual_image}" >&2
    return 1
  fi
  if [[ ! "${restart_count}" =~ ^[0-9]+$ ]] || (( restart_count != 0 )); then
    echo "Container restarted during verification: count=${restart_count}" >&2
    return 1
  fi
  if [[ "${expected_network_mode:-bridge}" == "host" && "${actual_network_mode}" != "host" ]]; then
    echo "Container network mismatch: expected host, got ${actual_network_mode}" >&2
    return 1
  fi
  if [[ "${expected_network_mode:-bridge}" == "bridge" && "${actual_network_mode}" == "host" ]]; then
    echo "Container network mismatch: expected bridge, got host" >&2
    return 1
  fi
  if ! docker exec "${BOT_CONTAINER_NAME}" /app/healthcheck.sh; then
    echo "In-container heartbeat verification failed" >&2
    return 1
  fi

  echo "Runtime verification passed: image=${actual_image}, restarts=${restart_count}, network=${actual_network_mode}"
  docker exec "${BOT_CONTAINER_NAME}" cat /tmp/baskov-discord-bot.ready
}

rollback() {
  echo "Deployment verification failed; attempting rollback" >&2
  if [[ ! -s "${PREVIOUS_ENV_FILE}" ]]; then
    echo "No previous deployment state is available" >&2
    compose_with_env "${ENV_FILE}" logs --tail=200 bot || true
    return 1
  fi

  cp "${PREVIOUS_ENV_FILE}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  compose_with_env "${ENV_FILE}" pull bot

  if [[ "${PREVIOUS_CONTAINER_RUNNING}" != "true" ]]; then
    compose_with_env "${ENV_FILE}" stop bot || true
    echo "Rollback restored the previous environment and kept the bot stopped"
    return 0
  fi

  compose_with_env "${ENV_FILE}" up -d --remove-orphans bot

  for _ in {1..24}; do
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${BOT_CONTAINER_NAME}" 2>/dev/null || true)"
    if [[ "${health}" == "healthy" ]]; then
      rollback_image="$(read_env_value BOT_IMAGE)"
      if verify_runtime "${rollback_image}"; then
        echo "Rollback succeeded"
        return 0
      fi
      break
    fi
    sleep 5
  done

  echo "Rollback did not become healthy" >&2
  compose_with_env "${ENV_FILE}" logs --tail=200 bot || true
  return 1
}

write_env "${BOT_IMAGE}" "${DISCORD_BOT_TOKEN}"
compose_with_env "${ENV_FILE}" pull bot
compose_with_env "${ENV_FILE}" up -d --remove-orphans bot

for _ in {1..24}; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${BOT_CONTAINER_NAME}" 2>/dev/null || true)"
  case "${health}" in
    healthy)
      if verify_runtime "${BOT_IMAGE}"; then
        echo "Deployment is healthy: ${BOT_IMAGE}"
        docker image prune -f --filter 'until=168h' >/dev/null 2>&1 || true
        exit 0
      fi
      break
      ;;
    unhealthy|exited|dead)
      break
      ;;
  esac
  sleep 5
done

rollback
exit 1
