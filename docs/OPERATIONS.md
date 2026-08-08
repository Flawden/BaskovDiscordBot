# Operations & Observability

Baskov Discord Bot публикует живой runtime heartbeat, выполняет fail-fast storage preflight, показывает безопасный диагностический статус в Discord и ограничивает ресурсы контейнера.

## `/status`

Команда доступна участникам сервера и показывает только агрегированные operational-данные:

- состояние Discord gateway;
- количество Discord-серверов и зарегистрированных slash-команд;
- uptime процесса;
- число активных и реально воспроизводящих музыкальных сессий;
- суммарное количество ожидающих треков;
- количество session checkpoints, текущих recovery operations и transport attempts/success/fail и startup restored/failed;
- успешные и упавшие prefix/slash/button interactions с момента запуска;
- storage readiness трёх постоянных файлов без публикации их абсолютных путей.

Команда не выводит Discord token, имена пользователей, поисковые запросы, содержимое очереди или guild settings.

## Persistence preflight

До подключения JDA проверяются три долговременных storage path: guild settings, music library и music-session checkpoints. Пути обязаны быть различными; существующий объект должен быть обычным читаемым/записываемым файлом и не symlink; рядом с каждым storage выполняется реальная create/write/delete probe.

Успешный preflight пишет startup marker:

```text
Persistence readiness: READY
```

При ошибке приложение fail-fast завершает startup до подключения к Discord. `/status` показывает только агрегированный статус, число storage и число файлов, существовавших при старте.

## Runtime heartbeat

После успешного подключения JDA создаётся файл:

```text
/tmp/baskov-discord-bot.ready
```

Он обновляется каждые 10 секунд только при `JDA.Status.CONNECTED`. Внутри находятся:

```text
status=CONNECTED
timestamp=<UTC instant>
guilds=<count>
slashCommands=<count>
```

Docker healthcheck требует одновременно:

1. существующий непустой файл;
2. строку `status=CONNECTED`;
3. возраст heartbeat не более 45 секунд.

Поэтому зависший или потерявший Discord gateway процесс перестаёт считаться healthy, даже если старый startup-файл сохранился.

## Контейнерные границы

Production Compose ограничивает один экземпляр:

```text
memory: 768 MiB
CPU: 1 core
PIDs: 256
Docker logs: 3 × 10 MiB
```

Ограничения не меняют музыкальные лимиты и могут быть пересмотрены отдельным релизом после наблюдения за реальным потреблением.

## Post-deploy verification

После Docker `healthy` серверный deploy-скрипт дополнительно проверяет:

- контейнер запущен именно из ожидаемого immutable SHA-image;
- pulled image имеет OCI RepoDigest, совпадающий с digest, опубликованным build job;
- `RestartCount` равен нулю;
- внутренний `/app/healthcheck.sh` проходит;
- heartbeat можно прочитать внутри контейнера;
- в логах присутствует `Persistence readiness: READY`.

При провале любой проверки запускается rollback на предыдущий `.env` и образ. Когда контейнер до начала deployment был намеренно остановлен, rollback восстанавливает окружение, но оставляет бота остановленным вместо запуска известного проблемного образа.

## Voice transport

Voice-подключение имеет отдельный bounded lifecycle: автоматический reconnect JDA отключён, каждая попытка ограничена timeout, а потерянное во время воспроизведения соединение передаётся recovery coordinator-у. Подробная state machine находится в [`VOICE-CONNECTIONS.md`](VOICE-CONNECTIONS.md), а checkpoint/restart restoration — в [`VOICE-RECOVERY-SESSION-RESTORATION.md`](VOICE-RECOVERY-SESSION-RESTORATION.md).
