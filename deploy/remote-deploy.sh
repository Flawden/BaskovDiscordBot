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
: "${BOT_IMAGE_DIGEST:?BOT_IMAGE_DIGEST is missing}"
: "${BOT_CONTAINER_NAME:?BOT_CONTAINER_NAME is missing}"
: "${DISCORD_BOT_TOKEN_B64:?DISCORD_BOT_TOKEN_B64 is missing}"
: "${DISCORD_BOT_PREFIX_B64:?DISCORD_BOT_PREFIX_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64:?DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64:?DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64 is missing}"
: "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64:?DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64:?DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT_B64:?DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64:?DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64:?DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64:?DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL_B64:?DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_MAX_AGE_B64:?DISCORD_BOT_MUSIC_SESSION_MAX_AGE_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP_B64:?DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER_B64:?DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED_B64:?DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS_B64:?DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS_B64 is missing}"
: "${DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF_B64:?DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF_B64 is missing}"
: "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED_B64:?DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED_B64 is missing}"
: "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL_B64:?DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL_B64 is missing}"
: "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION_B64:?DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION_B64 is missing}"
: "${BOT_NETWORK_MODE_B64:?BOT_NETWORK_MODE_B64 is missing}"
: "${DISCORD_BOT_VOICE_LOG_LEVEL_B64:?DISCORD_BOT_VOICE_LOG_LEVEL_B64 is missing}"
: "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64:?DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64 is missing}"
: "${DISCORD_BOT_MUSIC_MAX_VOLUME_B64:?DISCORD_BOT_MUSIC_MAX_VOLUME_B64 is missing}"
: "${LASTFM_API_KEY_B64:=}"
: "${LASTFM_API_BASE_URL_B64:?LASTFM_API_BASE_URL_B64 is missing}"
: "${DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT_B64:?DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT_B64 is missing}"
: "${DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT_B64:?DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT_B64 is missing}"

decode() {
  printf '%s' "$1" | base64 --decode
}

DISCORD_BOT_TOKEN="$(decode "${DISCORD_BOT_TOKEN_B64}")"
DISCORD_BOT_PREFIX="$(decode "${DISCORD_BOT_PREFIX_B64}")"
DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE="$(decode "${DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE_B64}")"
DISCORD_BOT_MUSIC_MAX_TRACK_DURATION="$(decode "${DISCORD_BOT_MUSIC_MAX_TRACK_DURATION_B64}")"
DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT="$(decode "${DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT_B64}")"
DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT="$(decode "${DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT_B64}")"
DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT="$(decode "${DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT_B64}")"
DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN="$(decode "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN_B64}")"
DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE="$(decode "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE_B64}")"
DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE="$(decode "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE_B64}")"
DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL="$(decode "${DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL_B64}")"
DISCORD_BOT_MUSIC_SESSION_MAX_AGE="$(decode "${DISCORD_BOT_MUSIC_SESSION_MAX_AGE_B64}")"
DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP="$(decode "${DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP_B64}")"
DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER="$(decode "${DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER_B64}")"
DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED="$(decode "${DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED_B64}")"
DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS="$(decode "${DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS_B64}")"
DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF="$(decode "${DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF_B64}")"
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED="$(decode "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED_B64}")"
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL="$(decode "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL_B64}")"
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION="$(decode "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION_B64}")"
BOT_NETWORK_MODE="$(decode "${BOT_NETWORK_MODE_B64}")"
DISCORD_BOT_VOICE_LOG_LEVEL="$(decode "${DISCORD_BOT_VOICE_LOG_LEVEL_B64}")"
DISCORD_BOT_MUSIC_DEFAULT_VOLUME="$(decode "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME_B64}")"
DISCORD_BOT_MUSIC_MAX_VOLUME="$(decode "${DISCORD_BOT_MUSIC_MAX_VOLUME_B64}")"
LASTFM_API_KEY="$(decode "${LASTFM_API_KEY_B64}")"
LASTFM_API_BASE_URL="$(decode "${LASTFM_API_BASE_URL_B64}")"
DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT="$(decode "${DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT_B64}")"
DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT="$(decode "${DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT_B64}")"

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
if [[ ! "${DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT}" =~ ^[1-9][0-9]*(ms|s|m)$ ]]; then
  echo "Music playback ready timeout must be a positive duration such as 10s" >&2
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
if [[ ! "${DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL}" =~ ^[1-9][0-9]*(ms|s|m)$ ]]; then
  echo "Music session checkpoint interval must be a positive duration such as 5s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_SESSION_MAX_AGE}" =~ ^[1-9][0-9]*(ms|s|m|h|d)$ ]]; then
  echo "Music session max age must be a positive duration such as 6h" >&2
  exit 1
fi
for session_boolean in \
  "${DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP}" \
  "${DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER}" \
  "${DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED}"; do
  if [[ ! "${session_boolean}" =~ ^(true|false)$ ]]; then
    echo "Music session boolean settings must be true or false" >&2
    exit 1
  fi
done
if [[ ! "${DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS < 1 \
      || DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS > 10 )); then
  echo "Music session max recovery attempts must be between 1 and 10" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF}" =~ ^[0-9]+(ms|s|m)$ ]]; then
  echo "Music session recovery backoff must be a non-negative duration such as 2s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED}" =~ ^(true|false)$ ]]; then
  echo "Persistence backup enabled must be true or false" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL}" =~ ^[1-9][0-9]*(ms|s|m|h|d)$ ]]; then
  echo "Persistence backup interval must be a positive duration such as 6h" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION < 1 \
      || DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION > 100 )); then
  echo "Persistence backup retention must be between 1 and 100" >&2
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

if [[ ! "${LASTFM_API_BASE_URL}" =~ ^https:// ]]; then
  echo "Last.fm API base URL must use https" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT}" =~ ^[1-9][0-9]*(ms|s)$ ]]; then
  echo "Discovery request timeout must be a positive duration such as 3s" >&2
  exit 1
fi
if [[ ! "${DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT}" =~ ^[0-9]+$ ]] \
    || (( DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT < 5 || DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT > 100 )); then
  echo "Discovery candidate limit must be between 5 and 100" >&2
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
    printf 'DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT=%s\n' "${DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT}"
    printf 'DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN=%s\n' "${DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN}"
    printf 'DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE=%s\n' "${DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE}"
    printf 'DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=%s\n' "${DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE}"
    printf 'DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL=%s\n' "${DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL}"
    printf 'DISCORD_BOT_MUSIC_SESSION_MAX_AGE=%s\n' "${DISCORD_BOT_MUSIC_SESSION_MAX_AGE}"
    printf 'DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP=%s\n' "${DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP}"
    printf 'DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER=%s\n' "${DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER}"
    printf 'DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED=%s\n' "${DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED}"
    printf 'DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS=%s\n' "${DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS}"
    printf 'DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF=%s\n' "${DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF}"
    printf 'DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED=%s\n' "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED}"
    printf 'DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL=%s\n' "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL}"
    printf 'DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION=%s\n' "${DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION}"
    printf 'BOT_NETWORK_MODE=%s\n' "${BOT_NETWORK_MODE}"
    printf 'DISCORD_BOT_VOICE_LOG_LEVEL=%s\n' "${DISCORD_BOT_VOICE_LOG_LEVEL}"
    printf 'DISCORD_BOT_MUSIC_DEFAULT_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_DEFAULT_VOLUME}"
    printf 'DISCORD_BOT_MUSIC_MAX_VOLUME=%s\n' "${DISCORD_BOT_MUSIC_MAX_VOLUME}"
    printf 'LASTFM_API_KEY=%s\n' "${LASTFM_API_KEY}"
    printf 'LASTFM_API_BASE_URL=%s\n' "${LASTFM_API_BASE_URL}"
    printf 'DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT=%s\n' "${DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT}"
    printf 'DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT=%s\n' "${DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT}"
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
  local expected_digest="${2:-}"
  local require_native_dave="${3:-true}"
  local actual_image restart_count actual_network_mode expected_network_mode container_logs repo_digests

  actual_image="$(docker inspect --format '{{.Config.Image}}' "${BOT_CONTAINER_NAME}")"
  restart_count="$(docker inspect --format '{{.RestartCount}}' "${BOT_CONTAINER_NAME}")"
  actual_network_mode="$(docker inspect --format '{{.HostConfig.NetworkMode}}' "${BOT_CONTAINER_NAME}")"
  expected_network_mode="$(read_env_value BOT_NETWORK_MODE)"

  if [[ "${actual_image}" != "${expected_image}" ]]; then
    echo "Container image mismatch: expected ${expected_image}, got ${actual_image}" >&2
    return 1
  fi
  if [[ -n "${expected_digest}" ]]; then
    repo_digests="$(docker image inspect "${expected_image}" --format '{{range .RepoDigests}}{{println .}}{{end}}')"
    if ! grep -Fq "@${expected_digest}" <<< "${repo_digests}"; then
      echo "Container image digest mismatch: expected ${expected_digest}" >&2
      return 1
    fi
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
  if [[ "${require_native_dave}" == "true" ]]; then
    container_logs="$(docker logs "${BOT_CONTAINER_NAME}" 2>&1)"
    if ! grep -Fq "Native libDAVE ready:" <<< "${container_logs}"; then
      echo "Native libDAVE startup marker is missing" >&2
      return 1
    fi
    if ! grep -Fq "Modern YouTube source ready:" <<< "${container_logs}"; then
      echo "Modern YouTube source startup marker is missing" >&2
      return 1
    fi
    if ! grep -Fq "Voice recovery initialized:" <<< "${container_logs}"; then
      echo "Voice recovery startup marker is missing" >&2
      return 1
    fi
    if ! grep -Fq "Persistence readiness: READY" <<< "${container_logs}"; then
      echo "Persistence readiness startup marker is missing" >&2
      return 1
    fi
    if ! grep -Eq "Persistence backup (created|disabled)" <<< "${container_logs}"; then
      echo "Persistence backup startup marker is missing" >&2
      return 1
    fi
  fi

  echo "Runtime verification passed: image=${actual_image}, restarts=${restart_count}, network=${actual_network_mode}, dave_required=${require_native_dave}"
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
      if verify_runtime "${rollback_image}" "" false; then
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
      if verify_runtime "${BOT_IMAGE}" "${BOT_IMAGE_DIGEST}" true; then
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
