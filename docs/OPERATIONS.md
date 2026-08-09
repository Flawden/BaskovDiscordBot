# Operations & Reliability

Baskov Discord Bot v1.1.0 расширяет production observability: живой Discord heartbeat, runtime gateway lifecycle, fail-fast и live storage probes, bounded persistence backups и ограниченную ротацию application/Docker logs.

## `/status`

Команда остаётся ephemeral и показывает только агрегированные operational-данные. Она не выводит Discord token, абсолютные storage paths, содержимое guild settings, поисковые запросы или queue identifiers.

Основные секции:

- `Discord gateway` — статус JDA, версия JDA, количество guild/slash commands, число gateway transitions, disconnected heartbeat samples и последнее CONNECTED;
- `DAVE / E2EE` — native runtime и protocol version;
- `Music` / `Playback modes` — active/playing sessions, queued tracks, repeat/volume/history/seek;
- `Voice transport` / `Voice history` / `Voice recovery` — transport state, frame polling, root-cause counters и bounded recovery;
- `Storage readiness` — live probe трёх persistent stores;
- `Persistence backups` — состояние backup scheduler, success/fail counters, retention и последний успешный snapshot;
- `Reliability` — агрегированный READY/DEGRADED state для gateway + storage + backups и число recovery failures;
- `Команды с запуска` — success/fail/total, failure rate, распределение Prefix/Slash/Buttons и время последней ошибки.

## Persistence preflight и live probe

До подключения JDA проверяются четыре долговременных storage path:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv
```

Пути обязаны быть различными. Существующий объект должен быть обычным readable/writable file и не symlink. Рядом с каждым storage выполняется реальная create/write/delete probe.

Startup остаётся fail-fast:

```text
Persistence readiness: READY
```

При ошибке приложение завершается до подключения к Discord.

После startup `/status` вызывает не разовый startup snapshot, а `PersistenceReadiness.probe()`. Поэтому если volume позднее стал read-only, storage path заменён directory/symlink или write probe перестал проходить, `/status` показывает `FAILED`, не убивая процесс. Следующий успешный probe снова возвращает `READY`.

## Persistence backups

После успешного startup preflight `PersistenceBackupService` немедленно создаёт backup, а затем повторяет его с fixed delay.

Production defaults:

```text
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED=true
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_DIRECTORY=/app/data/backups
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL=6h
DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION=14
```

Backup — обычный ZIP с именем:

```text
baskov-persistence-YYYYMMDD-HHmmss-SSS.zip
```

Внутри находятся только существующие на момент snapshot файлы:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv
manifest.properties
```

`manifest.properties` содержит format marker `BASKOV_PERSISTENCE_BACKUP_V1`, UTC timestamp и presence flags для каждого store. Абсолютные host paths в archive не пишутся.

Безопасность записи:

1. backup directory создаётся внутри того же persistent `/app/data` volume;
2. symlink directory запрещён;
3. source symlink/non-regular file отклоняется;
4. archive сначала пишется в `.tmp`;
5. публикация выполняется через atomic move, где filesystem это поддерживает;
6. на POSIX directory получает `0700`, ZIP — `0600`;
7. после успешного snapshot старые ZIP удаляются до configured retention.

Ошибка periodic backup **не выключает Discord bot**, но snapshot становится `FAILED`, `/status` — `DEGRADED`, а deployment нового контейнера не считается успешным, если первый startup backup не создался (или backup не был явно disabled).

### Получить backup с VPS

Сначала посмотреть последние файлы внутри container volume:

```bash
docker exec baskov-discord-bot sh -c 'ls -lah /app/data/backups | tail -n 20'
```

Скопировать конкретный archive на host:

```bash
docker cp \
  baskov-discord-bot:/app/data/backups/baskov-persistence-YYYYMMDD-HHmmss-SSS.zip \
  ./
```

Restore намеренно не выполняется автоматически: перед восстановлением production нужно остановить контейнер, сохранить текущие файлы отдельно, проверить archive и только затем заменить нужные stores. Это исключает случайный overwrite работающего checkpoint/library/settings.

## Runtime heartbeat и gateway lifecycle

После успешного подключения JDA создаётся файл:

```text
/tmp/baskov-discord-bot.ready
```

Он обновляется каждые 10 секунд только при `JDA.Status.CONNECTED`.

Payload v1.1.0:

```text
status=CONNECTED
timestamp=<UTC instant>
guilds=<count>
slashCommands=<count>
gatewayTransitions=<count>
disconnectedSamples=<count>
```

`gatewayTransitions` увеличивается при фактическом переходе между наблюдаемыми JDA statuses после первого CONNECTED. `disconnectedSamples` считает heartbeat cycles, в которых gateway был не CONNECTED.

Docker healthcheck требует одновременно:

1. существующий непустой heartbeat;
2. `status=CONNECTED`;
3. возраст файла не более 45 секунд.

При disconnect старый heartbeat удаляется, поэтому зависший или потерявший gateway процесс перестаёт считаться healthy.

## Command reliability metrics

`OperationalMetrics` не хранит payload команд или user IDs. Он считает только aggregate success/failure по трём каналам:

```text
PREFIX
SLASH
BUTTON
```

Дополнительно сохраняются timestamp последнего success/failure, total invocations и failure rate. Эти данные живут только в памяти текущего процесса и обнуляются после restart.

## Log retention

Application file logger ограничен независимо от Docker json-file driver.

Logback:

```text
25 MB max per rolled file
14 days history
512 MB total size cap
```

Docker Compose:

```text
json-file
max-size: 10m
max-file: 3
```

Таким образом долгоживущий bot не должен бесконтрольно заполнять диск ни application logs, ни Docker stdout/stderr history.

## Контейнерные границы

Production Compose ограничивает один экземпляр:

```text
memory: 768 MiB
CPU: 1 core
PIDs: 256
```

Persistent data и backups находятся в named volume:

```text
<container-name>-data -> /app/data
```

## Post-deploy verification

После Docker `healthy` серверный deploy-скрипт дополнительно проверяет:

- контейнер запущен именно из ожидаемого immutable SHA-image;
- pulled image имеет OCI RepoDigest, совпадающий с digest build job;
- `RestartCount == 0`;
- внутренний `/app/healthcheck.sh` проходит;
- heartbeat читается внутри контейнера;
- присутствуют startup markers `Native libDAVE ready:`, `Modern YouTube source ready:`, `Voice recovery initialized:`, `Persistence readiness: READY`;
- persistence backup либо успешно создан (`Persistence backup created`), либо явно отключён (`Persistence backup disabled`).

При провале любой проверки сохраняется существующий rollback path на предыдущий `.env` и image.

## Voice transport

Voice lifecycle остаётся bounded: JDA auto-reconnect выключен, каждая попытка ограничена timeout, а потерянное во время воспроизведения соединение передаётся recovery coordinator-у. Подробнее:

- [`VOICE-CONNECTIONS.md`](VOICE-CONNECTIONS.md)
- [`VOICE-RECOVERY-SESSION-RESTORATION.md`](VOICE-RECOVERY-SESSION-RESTORATION.md)
- [`VOICE-ROOT-CAUSE-DIAGNOSTICS.md`](VOICE-ROOT-CAUSE-DIAGNOSTICS.md)

## Maven external dependency bootstrap (v1.6.3)

`maven.lavalink.dev` остаётся внешним источником для `dev.lavalink.youtube` и pinned `moe.kyokobot.libdave`, но CI больше не обязан повторно скачивать уже проверенные версии при каждом чистом hosted runner. Workflow сначала восстанавливает стабильный `baskov-maven-*` cache; при первом переходе fallback указывает на exact Maven cache зелёного `v1.5.0`.

После restore `maven-ci.sh bootstrap` проверяет POM/JAR для `youtube-source 1.18.2` и Linux libDAVE `ce725965e`, удаляет только их stale `*.lastUpdated` markers и пишет `external-bootstrap.log`. Отсутствующий bootstrap не маскирует ошибку: Maven продолжает обычный online resolution и diagnostics/retry policy.

Cache сохраняется отдельным `actions/cache/save` только после успешного `clean verify`. Это принципиально: failed network build не может создать immutable incomplete exact-key cache. CI/delivery Docker image затем использует `deploy/Dockerfile.ci` и уже проверенный `target/baskov-discord-bot.jar`; исходный root `Dockerfile` остаётся standalone source-build вариантом.
