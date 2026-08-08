# Delivery runners

Начиная с `v1.0.0` основной production delivery снова выполняется стандартным Linux GitHub-hosted runner-ом `ubuntu-latest`. Self-hosted runner остаётся резервным вариантом и не является обязательной частью production topology.

## Primary runner

Все обычные CI/delivery jobs используют:

```yaml
runs-on: ubuntu-latest
```

GitHub-hosted машина одноразовая, поэтому workflow не рассчитывает на локальные Docker/Maven caches между разными VM и не хранит состояние runner-а.

## Credential hygiene

`actions/checkout` запускается с `persist-credentials: false`. Deployment SSH key создаётся только под `${RUNNER_TEMP}` через `mktemp`, передаётся `ssh`/`scp` явно и удаляется в `always()` step.

## Immutable image verification

Delivery публикует одновременно SHA-tag и OCI digest. На VPS `remote-deploy.sh` проверяет:

1. фактический `Config.Image` контейнера равен ожидаемому `sha-<git-sha>` tag;
2. локально pulled image имеет `RepoDigest`, совпадающий с digest из `docker/build-push-action`;
3. контейнер не перезапускался;
4. healthcheck и обязательные startup markers прошли.

## Self-hosted fallback

Резервный runner можно вернуть отдельным maintenance patch, заменив `runs-on`. Для Linux/x64 машины нужны Git, Docker Engine/Buildx, исходящий HTTPS к GitHub/GHCR/Maven repositories и SSH к deployment VPS. Persistent runner нельзя считать доверенным для untrusted fork PRs, а Docker group фактически даёт процессу runner-а root-equivalent доступ к хосту.
