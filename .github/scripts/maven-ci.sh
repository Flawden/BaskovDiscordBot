#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_NAME="$(basename "$0")"
readonly MAVEN_VERIFY_ATTEMPTS="${MAVEN_VERIFY_ATTEMPTS:-2}"
readonly MAVEN_VERIFY_TIMEOUT_SECONDS="${MAVEN_VERIFY_TIMEOUT_SECONDS:-420}"
readonly MAVEN_VERSION_TIMEOUT_SECONDS="${MAVEN_VERSION_TIMEOUT_SECONDS:-120}"
readonly MAVEN_RETRY_DELAY_SECONDS="${MAVEN_RETRY_DELAY_SECONDS:-10}"
readonly DIAGNOSTICS_DIR="${BASKOV_MAVEN_DIAGNOSTICS_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/baskov-maven-diagnostics}"

mkdir -p "${DIAGNOSTICS_DIR}"

log() {
  printf '[maven-ci] %s\n' "$*"
}

probe_url() {
  local name="$1"
  local url="$2"
  local output_file="${DIAGNOSTICS_DIR}/repository-probes.log"
  local response
  local status

  set +e
  response="$(curl --silent --show-error --location \
    --connect-timeout 5 --max-time 15 \
    --output /dev/null \
    --write-out 'http=%{http_code} connect=%{time_connect}s total=%{time_total}s remote=%{remote_ip}' \
    "${url}" 2>&1)"
  status=$?
  set -e

  if [[ ${status} -eq 0 ]]; then
    log "probe ${name}: OK ${response}"
    printf '%s\tOK\t%s\n' "${name}" "${response}" >> "${output_file}"
  else
    log "probe ${name}: FAILED exit=${status} ${response}"
    printf '%s\tFAILED(exit=%s)\t%s\n' "${name}" "${status}" "${response}" >> "${output_file}"
  fi
}

print_environment() {
  local output_file="${DIAGNOSTICS_DIR}/environment.log"
  {
    echo "timestamp_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "runner_name=${RUNNER_NAME:-unknown}"
    echo "runner_os=${RUNNER_OS:-unknown}"
    echo "runner_arch=${RUNNER_ARCH:-unknown}"
    echo "workspace=${GITHUB_WORKSPACE:-$(pwd)}"
    echo "maven_verify_attempts=${MAVEN_VERIFY_ATTEMPTS}"
    echo "maven_verify_timeout_seconds=${MAVEN_VERIFY_TIMEOUT_SECONDS}"
    echo "maven_version_timeout_seconds=${MAVEN_VERSION_TIMEOUT_SECONDS}"
    echo "java_home=${JAVA_HOME:-unknown}"
    echo "maven_args=${MAVEN_ARGS:-}"
    printf 'disk='; df -h . | tail -n 1
    printf 'm2_size='; du -sh "${HOME}/.m2" 2>/dev/null | cut -f1 || echo 'absent'
    printf 'm2_repository_size='; du -sh "${HOME}/.m2/repository" 2>/dev/null | cut -f1 || echo 'absent'
    if [[ -d "${HOME}/.m2/repository" ]]; then
      printf 'last_updated_files='; find "${HOME}/.m2/repository" -name '*.lastUpdated' -type f | wc -l | tr -d ' '
      echo
    else
      echo 'last_updated_files=0'
    fi
    java -version 2>&1 || true
    timeout --signal=TERM --kill-after=10s 60s ./mvnw --version 2>&1 || true
  } | tee "${output_file}"
}

diagnose() {
  : > "${DIAGNOSTICS_DIR}/repository-probes.log"
  log "diagnostics directory: ${DIAGNOSTICS_DIR}"
  print_environment
  probe_url "maven-central" "https://repo.maven.apache.org/maven2/"
  probe_url "lavalink-releases" "https://maven.lavalink.dev/releases/"
  probe_url "lavalink-snapshots" "https://maven.lavalink.dev/snapshots/"
  log "repository probes are diagnostic only; Maven verification remains the source of truth"
}

is_network_failure() {
  local log_file="$1"
  grep -Eiq \
    'Could not transfer artifact|Could not resolve dependencies|Connection (reset|refused|timed out)|connect timed out|Read timed out|Unknown host|Temporary failure in name resolution|Name or service not known|PKIX path building failed|502 Bad Gateway|503 Service Unavailable|504 Gateway Time-out' \
    "${log_file}"
}

clear_failed_transfer_markers() {
  local count
  if [[ -d "${HOME}/.m2/repository" ]]; then
    count="$(find "${HOME}/.m2/repository" -name '*.lastUpdated' -type f | wc -l | tr -d ' ')"
    log "removing ${count:-0} Maven *.lastUpdated marker(s) before retry"
    find "${HOME}/.m2/repository" -name '*.lastUpdated' -type f -delete || true
  else
    log "Maven repository is absent; no *.lastUpdated markers to remove"
  fi
}

verify() {
  local attempt
  local status
  local attempt_log

  for ((attempt = 1; attempt <= MAVEN_VERIFY_ATTEMPTS; attempt++)); do
    attempt_log="${DIAGNOSTICS_DIR}/verify-attempt-${attempt}.log"
    log "Maven clean verify attempt ${attempt}/${MAVEN_VERIFY_ATTEMPTS}; hard timeout=${MAVEN_VERIFY_TIMEOUT_SECONDS}s"

    set +e
    timeout --signal=TERM --kill-after=30s "${MAVEN_VERIFY_TIMEOUT_SECONDS}s" \
      ./mvnw --batch-mode --show-version --errors clean verify 2>&1 | tee "${attempt_log}"
    status=${PIPESTATUS[0]}
    set -e

    if [[ ${status} -eq 0 ]]; then
      log "Maven verification succeeded on attempt ${attempt}"
      return 0
    fi

    if [[ ${status} -eq 124 || ${status} -eq 137 ]]; then
      log "Maven verification timed out on attempt ${attempt} (exit=${status})"
    elif is_network_failure "${attempt_log}"; then
      log "Maven verification failed with a network/repository signature on attempt ${attempt} (exit=${status})"
    else
      log "Maven verification failed with a non-network build/test error (exit=${status}); not retrying"
      return "${status}"
    fi

    if (( attempt >= MAVEN_VERIFY_ATTEMPTS )); then
      log "Maven verification exhausted ${MAVEN_VERIFY_ATTEMPTS} attempt(s)"
      return "${status}"
    fi

    clear_failed_transfer_markers
    log "retrying Maven verification after ${MAVEN_RETRY_DELAY_SECONDS}s"
    sleep "${MAVEN_RETRY_DELAY_SECONDS}"
  done
}

project_version() {
  local output_file="${DIAGNOSTICS_DIR}/project-version.log"
  local version

  log "resolving Maven project version with hard timeout=${MAVEN_VERSION_TIMEOUT_SECONDS}s"
  version="$(timeout --signal=TERM --kill-after=15s "${MAVEN_VERSION_TIMEOUT_SECONDS}s" \
    ./mvnw --batch-mode --errors help:evaluate \
      -Dexpression=project.version -DforceStdout -q 2> >(tee "${output_file}" >&2))"

  if [[ -z "${version}" ]]; then
    log "Maven project version is empty"
    return 1
  fi

  printf '%s\n' "${version}"
}

usage() {
  cat >&2 <<USAGE
Usage: ${SCRIPT_NAME} diagnose|verify|version
USAGE
  exit 2
}

case "${1:-}" in
  diagnose)
    diagnose
    ;;
  verify)
    verify
    ;;
  version)
    project_version
    ;;
  *)
    usage
    ;;
esac
