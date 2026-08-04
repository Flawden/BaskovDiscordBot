# Operations & Observability

Начиная с `v0.7.0`, Baskov Discord Bot публикует живой runtime heartbeat, показывает безопасный диагностический статус в Discord и ограничивает ресурсы контейнера.

## `/status`

Команда доступна участникам сервера и показывает только агрегированные operational-данные:

- состояние Discord gateway;
- количество Discord-серверов и зарегистрированных slash-команд;
- uptime процесса;
- число активных и реально воспроизводящих музыкальных сессий;
- суммарное количество ожидающих треков;
- успешные и упавшие prefix/slash/button interactions с момента запуска.

Команда не выводит Discord token, имена пользователей, поисковые запросы, содержимое очереди или guild settings.

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
- `RestartCount` равен нулю;
- внутренний `/app/healthcheck.sh` проходит;
- heartbeat можно прочитать внутри контейнера.

При провале любой проверки запускается rollback на предыдущий `.env` и образ. Когда контейнер до начала deployment был намеренно остановлен, rollback восстанавливает окружение, но оставляет бота остановленным вместо запуска известного проблемного образа.

## Voice transport

Voice-подключение имеет отдельный bounded lifecycle: автоматический reconnect JDA отключён, одна попытка ограничена timeout, а потерянное во время воспроизведения соединение закрывается после grace-периода. Подробная state machine и команды диагностики находятся в [`VOICE-CONNECTIONS.md`](VOICE-CONNECTIONS.md).
