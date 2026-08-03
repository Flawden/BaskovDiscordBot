# Выпуск релизов

Основная ветка `master` одновременно является production-веткой. Каждый релиз получает Semantic Version, запись в `CHANGELOG.md`, Git tag и immutable Docker image, связанный с Git SHA.

## Подготовка

1. Обновить `<version>` в `pom.xml`.
2. Перенести завершённые изменения из `Unreleased` в новый раздел `CHANGELOG.md`.
3. Выполнить проверки:

```powershell
.\mvnw.cmd clean verify
git diff --check
git status
```

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
