# 🎤 Baskov Discord Bot

Музыкальный Discord-бот на Java 17, Spring Boot, JDA и LavaPlayer.

## Возможности

- воспроизведение музыки, пауза, остановка и пропуск треков;
- очередь воспроизведения и поиск;
- расширяемая система команд на Spring-компонентах;
- контейнерный запуск;
- CI, публикация immutable-образов в GHCR и автоматический деплой на VPS.

## Локальный запуск

Требования: Java 17 и Discord bot token.

### Через Maven

```bash
export DISCORD_BOT_TOKEN='your-token'
./mvnw clean verify
./mvnw spring-boot:run
```

### Через Docker Compose

```bash
export DISCORD_BOT_TOKEN='your-token'
docker compose up -d --build
docker compose logs -f bot
```

Боту не нужен входящий HTTP-порт. После успешного подключения к Discord приложение создаёт readiness-маркер, который используется Docker healthcheck.

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

Каждая доставка сначала выполняет Maven verification, затем публикует в GHCR:

```text
ghcr.io/<owner>/<repository>:sha-<full-git-sha>
ghcr.io/<owner>/<repository>:dev|latest
```

На VPS всегда разворачивается immutable SHA-тег. После запуска workflow ждёт Docker healthcheck. При неуспешном старте автоматически восстанавливается предыдущий `.env` и предыдущий образ.

## Настройка GitHub

Создайте GitHub Environments:

- `development`;
- `production`.

Для production можно включить Required reviewers.

Добавьте repository variable:

| Variable | Значение | Назначение |
|---|---:|---|
| `BOT_DEPLOY_ENABLED` | `true` | включает SSH-деплой после публикации образа |
| `BOT_DEPLOY_DIR` | путь на VPS | необязательно; по умолчанию `/opt/baskov-discord-bot-dev` для development и `/opt/baskov-discord-bot` для production |
| `BOT_CONTAINER_NAME` | имя контейнера | необязательно; по умолчанию `baskov-discord-bot-dev` и `baskov-discord-bot` соответственно |

`BOT_DEPLOY_DIR` и `BOT_CONTAINER_NAME` удобно задавать как environment variables, если dev и production должны работать на одном VPS с нестандартными именами.

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

В обычном запуске `DISCORD_BOT_ENABLED=true` по умолчанию. Тестовый Spring context запускается с `discordBot.enabled=false`, поэтому CI не требует токен и не подключается к Discord.

## Документация

- [О проекте](https://github.com/Flawden/BaskovDiscordBot/wiki)
- [Развёртывание и запуск](https://github.com/Flawden/BaskovDiscordBot/wiki/Развертывание-приложения)

## Лицензия

MIT.
