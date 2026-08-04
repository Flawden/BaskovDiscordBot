# 🎤 Baskov Discord Bot

Текущая стабильная версия: **v0.7.1**.

Музыкальный Discord-бот на Java 17, Spring Boot, JDA и LavaPlayer.

## Возможности

- slash-команды воспроизведения и полноценного управления очередью;
- autocomplete последних поисковых запросов и интерактивные кнопки управления;
- legacy `!`-команды как compatibility layer;
- воспроизведение музыки, пауза, остановка и пропуск треков;
- requester, ETA, repeat mode, shuffle, remove/move/clear и управление громкостью;
- постоянные настройки громкости и повтора отдельно для каждого Discord-сервера;
- команда `/status` с uptime, Discord gateway, музыкальными сессиями и агрегированными счётчиками команд;
- динамический Docker heartbeat, который подтверждает свежее подключение к Discord, а не только факт старта;
- ограничения CPU, памяти, PID и ротация Docker-логов;
- управление музыкой только из общего voice channel с административным override;
- bounded queue, лимит длительности и автоматическое отключение пустых сессий;
- stateless-ядро команд с case-insensitive реестром и защитой от дубликатов;
- конфигурируемый префикс и cooldown для шумных команд;
- команда `!version` с версией и метаданными сборки;
- безопасная обработка ссылок SoundCloud/YouTube без сетевой проверки пользовательского URL;
- согласованные версии SLF4J/Logback под управлением Spring Boot BOM;
- автоматические ежемесячные проверки обновлений Maven и GitHub Actions;
- major-обновления Spring Boot, JDA и GitHub Actions блокируются Dependabot и выполняются только отдельными migration-релизами;
- контейнерный запуск;
- CI, публикация immutable-образов в GHCR и автоматический деплой на VPS.

## Локальный запуск

Требования: Java 17 и Discord bot token.

### Через Maven

```bash
export DISCORD_BOT_TOKEN='your-token'
export DISCORD_BOT_PREFIX='!'
./mvnw clean verify
./mvnw spring-boot:run
```

### Через Docker Compose

```bash
export DISCORD_BOT_TOKEN='your-token'
docker compose up -d --build
docker compose logs -f bot
```

Боту не нужен входящий HTTP-порт. После подключения к Discord приложение обновляет readiness heartbeat каждые 10 секунд; Docker считает контейнер healthy только при свежем `CONNECTED`-сигнале.

### Настройки музыкальной сессии

| Переменная | По умолчанию | Назначение |
|---|---:|---|
| `DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE` | `100` | максимум ожидающих треков на сервер |
| `DISCORD_BOT_MUSIC_MAX_TRACK_DURATION` | `4h` | максимальная длительность одного трека |
| `DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT` | `5m` | отключение после опустошения очереди |
| `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` | `100` | громкость новой guild-сессии |
| `DISCORD_BOT_MUSIC_MAX_VOLUME` | `150` | верхняя граница команды `/volume` |
| `DISCORD_BOT_PERSISTENCE_FILE` | `data/guild-settings.properties` | файл постоянных guild-настроек; в Docker используется `/app/data/...` |

Live-потоки отключены. Подробные правила voice-доступа и lifecycle находятся в [`docs/MUSIC-SESSIONS.md`](docs/MUSIC-SESSIONS.md).
Современный Discord-интерфейс описан в [`docs/MODERN-COMMANDS.md`](docs/MODERN-COMMANDS.md).
Очередь и новые команды управления описаны в [`docs/QUEUE-EXPERIENCE.md`](docs/QUEUE-EXPERIENCE.md).
Постоянные guild-настройки описаны в [`docs/GUILD-SETTINGS.md`](docs/GUILD-SETTINGS.md).
Operations и health-модель описаны в [`docs/OPERATIONS.md`](docs/OPERATIONS.md).
Релиз с Android описан в [`docs/TERMUX-RELEASE.md`](docs/TERMUX-RELEASE.md).

## CI/CD

### CI

`.github/workflows/ci.yml` запускается для pull request и вручную. Он:

1. поднимает Java 17;
2. выполняет `./mvnw clean verify` без настоящего Discord-подключения;
3. проверяет сборку Docker-образа;
4. сохраняет Surefire reports как artifact.

### Delivery

`.github/workflows/delivery.yml` запускается:

- при push в `dev` — environment `development`, channel tag `dev`;
- при push в `master` — environment `production`, channel tag `latest`;
- вручную через `workflow_dispatch`.

В текущем режиме разработки релизы отправляются напрямую в `master`; ветка `dev` остаётся технически поддерживаемой, но не используется.

Каждая доставка сначала выполняет Maven verification, затем публикует в GHCR:

```text
ghcr.io/<owner>/<repository>:sha-<full-git-sha>
ghcr.io/<owner>/<repository>:dev|latest
```

На VPS всегда разворачивается immutable SHA-тег. После Docker healthcheck workflow дополнительно сверяет фактический image, `RestartCount` и внутренний heartbeat. При любой неудаче автоматически восстанавливаются предыдущий `.env` и предыдущий образ.

## Настройка GitHub

Создайте GitHub Environments:

- `development`;
- `production`.

Для production можно включить Required reviewers.

Добавьте repository variable:

| Variable | Значение | Назначение |
|---|---:|---|
| `BOT_DEPLOY_ENABLED` | `true` | включает SSH-деплой после публикации образа |

`BOT_DEPLOY_DIR` и `BOT_CONTAINER_NAME` задаются как environment variables соответствующего GitHub Environment, если нужны нестандартные пути или имена контейнеров.

Необязательные environment variables музыкальной сессии:

| Variable | По умолчанию | Назначение |
|---|---:|---|
| `DISCORD_BOT_PREFIX` | `!` | префикс legacy-команд |
| `DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE` | `100` | размер очереди от 1 до 1000 |
| `DISCORD_BOT_MUSIC_MAX_TRACK_DURATION` | `4h` | максимальная длительность трека |
| `DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT` | `5m` | таймаут отключения пустой сессии |
| `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` | `100` | начальная громкость |
| `DISCORD_BOT_MUSIC_MAX_VOLUME` | `150` | максимальная громкость |

Workflow кодирует эти значения перед SSH-передачей, а серверный deploy-скрипт проверяет формат до перезаписи защищённого `.env`.

В каждый environment добавьте secrets с одинаковыми именами:

| Secret | Назначение |
|---|---|
| `DISCORD_BOT_TOKEN` | токен соответствующего Discord-бота |
| `BOT_DEPLOY_HOST` | адрес VPS |
| `BOT_DEPLOY_PORT` | SSH-порт, обычно `22` |
| `BOT_DEPLOY_USER` | SSH-пользователь с доступом к Docker |
| `BOT_DEPLOY_SSH_KEY` | приватный SSH-ключ |
| `BOT_DEPLOY_KNOWN_HOSTS` | доверенная строка host key сервера |

`BOT_DEPLOY_KNOWN_HOSTS` можно получить локально:

```bash
ssh-keyscan -p 22 your-server.example.com
```

Пользователь на VPS должен иметь доступ к `docker` и `docker compose`, а также право записи в родительский каталог деплоя. Каталог деплоя создаётся workflow автоматически. Для одновременно работающих development и production используйте разные Discord applications/tokens, иначе оба экземпляра будут отвечать на одни и те же события.

## Режим тестирования

В обычном запуске `DISCORD_BOT_ENABLED=true` по умолчанию. Префикс задаётся через `DISCORD_BOT_PREFIX` и по умолчанию равен `!`. Тестовый Spring context запускается с `discordBot.enabled=false`, поэтому CI не требует токен и не подключается к Discord.

## Версии и релизы

Проект использует Semantic Versioning. История изменений находится в [`CHANGELOG.md`](CHANGELOG.md), а пошаговый процесс выпуска — в [`docs/RELEASING.md`](docs/RELEASING.md). Основная ветка `master` одновременно является production-веткой.

## Документация

- [О проекте](https://github.com/Flawden/BaskovDiscordBot/wiki)
- [Развёртывание и запуск](https://github.com/Flawden/BaskovDiscordBot/wiki/Развертывание-приложения)
- [Гигиена зависимостей](docs/DEPENDENCY-HYGIENE.md)

## Лицензия

MIT.
