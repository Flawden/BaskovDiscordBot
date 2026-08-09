# Выпуск релизов

Основная ветка `master` одновременно является production-веткой. Каждый релиз получает Semantic Version, запись в `CHANGELOG.md`, Git tag и immutable Docker image, связанный с Git SHA.

## Подготовка

1. Обновить `<version>` в `pom.xml`.
2. Перенести завершённые изменения из `Unreleased` в новый раздел `CHANGELOG.md`.
3. Выполнить проверки:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd dependency:tree `
  -Dincludes=org.slf4j:*,ch.qos.logback:*,org.json:*,com.vaadin.external.google:*
git diff --check
git status
```

Logging-зависимости должны оставаться согласованными через Spring Boot BOM, а `android-json` — отсутствовать в итоговом test classpath. Подробности: [`DEPENDENCY-HYGIENE.md`](DEPENDENCY-HYGIENE.md).

## Commit и production push

```powershell
git add -A
git commit -m "Release vX.Y.Z: <release name>"
git push origin master
```

После зелёного production deployment создать аннотированный tag:

```powershell
git tag -a vX.Y.Z -m "Baskov Discord Bot vX.Y.Z"
git push origin vX.Y.Z
```

## Проверка VPS

```bash
docker ps --filter "name=baskov-discord-bot"
docker inspect \
  --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
  baskov-discord-bot
docker logs --tail 200 baskov-discord-bot
```

Ожидаемый health status: `healthy`.

Для релизов с operations/reliability дополнительно проверить startup markers и persistence backup:

```bash
docker logs --tail 300 baskov-discord-bot | grep -E \
  'Native libDAVE ready:|Modern YouTube source ready:|Voice recovery initialized:|Persistence readiness: READY|Persistence backup (created|disabled)'

docker exec baskov-discord-bot sh -c 'ls -lah /app/data/backups 2>/dev/null || true'
```

Если backups включены, должен существовать хотя бы один `baskov-persistence-*.zip`. `/status` должен показывать `Storage readiness: READY`, `Persistence backups: READY` и агрегированный `Reliability: READY`.

## Выпуск с Android

Полный эквивалент PowerShell-процесса для Termux находится в [`TERMUX-RELEASE.md`](TERMUX-RELEASE.md). Он включает `/storage/emulated/0/Download/`, SHA-256, `git apply --check`, Maven verification, commit, push, tag и rollback.

## Maven delivery diagnostics

Начиная с `v1.6.1`, GitHub-hosted workflows не вызывают `clean verify` напрямую. Используется общий helper:

```bash
./.github/scripts/maven-ci.sh diagnose
./.github/scripts/maven-ci.sh verify
```

Если Maven завис на dependency resolution, одна попытка завершается через 420 секунд, затем разрешён один network-only retry. Compile/test failures не ретраятся. Transfer progress должен оставаться видимым (`show-download-progress: true`).

При failure скачайте artifact `maven-diagnostics-<run-id>` и проверьте `repository-probes.log`, `environment.log` и `verify-attempt-*.log` до изменения application code.

Начиная с `v1.6.2`, helper дополнительно включает Maven Resolver groupId repository filtering из `.mvn/rrf`: `lavalink-releases` обслуживает только `dev.lavalink.youtube`, `lavalink-libdave-snapshots` — только `moe.kyokobot.libdave`, Central остаётся unrestricted. При добавлении нового custom-repository dependency обновить соответствующий routing-файл и [`MAVEN-REPOSITORY-ROUTING.md`](MAVEN-REPOSITORY-ROUTING.md).

Если изменяются закреплённые dependency/build-tool версии, одновременно обновить `.github/maven-cache-key.txt`; contract test не даст случайно оставить старый fingerprint.
