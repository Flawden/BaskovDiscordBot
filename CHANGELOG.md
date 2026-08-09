# Changelog

Все заметные изменения Baskov Discord Bot фиксируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
а версии следуют [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

## [1.11.0] — 2026-08-09

### Administration & Moderation 2.0

- Добавлена persistent `moderator-role` как least-privilege уровень между обычным слушателем и manager/admin: она может модерировать waiting queue, но не получает права изменять guild settings, импортировать profiles или администрировать persistent library. DJ-role также сохраняет queue-moderation capability.
- Новый `/moderation status|remove|purge|audit`: status показывает live revision/queue/requester summary и действующие moderation roles; `remove` удаляет одну глобальную позицию, `purge` — все pending requests выбранного пользователя. Обе mutation-команды поддерживают optional queue revision и отклоняют stale позиции без изменения очереди.
- Добавлен persistent `/settings requester-limit max:<0..100>`: `0` отключает персональный cap, положительное значение ограничивает число **ожидающих** треков одного Discord requester-а. Enforcement находится внутри `TrackScheduler`, поэтому одинаково действует для `/play`, search selection, favorites/history replay и batch playlists; текущий playing track в cap не входит.
- `MusicLoadResult` получил отдельный `REQUESTER_LIMIT`, чтобы персональный cap не маскировался под общую `QUEUE_FULL`; пользователь получает понятный ответ и может освободить своё место через `/queue-manage mine`.
- Session recovery использует отдельный internal enqueue-path, который обходит только новый per-requester cap для уже сохранённой очереди, но сохраняет global queue bounds, duration и stream safety. Поэтому новый moderation limit не урезает checkpoint из `v1.10.0` при restart/redeploy.
- `/settings moderator-role` и requester limit сохраняются в существующем atomic `guild-settings.properties`; administrative/moderation audit увеличен с 10 до 25 bounded entries и теперь также фиксирует успешные `/moderation remove|purge`. Existing persistence backup автоматически покрывает новые поля.
- Portable settings profile upgraded to `BASKOV_SETTINGS_V2` с `moderatorRole` и `requesterQueueLimit`. Decoder продолжает принимать legacy `BASKOV_SETTINGS_V1`, присваивая новым полям безопасные defaults (`moderatorRole=0`, `requesterQueueLimit=0`).
- `/settings permissions` расширена отдельной queue-moderation matrix и personal pending limit. `@everyone` по-прежнему запрещён для administrative/operational roles.

## [1.10.0] — 2026-08-09

### Playback Sessions & Recovery 2.0

- Session checkpoint upgraded to `BASKOV_MUSIC_SESSIONS_V2`: in addition to current track, resume position, waiting queue, pause, volume and repeat, it now stores bounded previous-history (up to 25 tracks) so `/previous` survives restart/redeploy.
- `FileMusicSessionRepository` remains backward-compatible with `BASKOV_MUSIC_SESSIONS_V1`; legacy checkpoints load with empty previous-history and are upgraded to V2 on the next save. Atomic temp→replace and owner-only permissions remain unchanged.
- Startup restoration resolves saved previous-history separately from the active queue and injects it through `TrackScheduler.restoreHistory(...)` without invoking the persistent listening-history listener, so recovery cannot fabricate extra listening records. Individual unavailable previous tracks are skipped without failing the playable current/queue restore.
- `/session status` exposes guild-scoped checkpoint/recovery state: saved channel, checkpoint age, current+queue count, saved previous-history count, resume position, pause/volume/repeat and the latest recovery event. It is read-only.
- `/session recover` gives existing guild administrators (owner, `Manage Server`, manager-role) an explicit retry for a pending checkpoint. It refuses to layer recovery over an active/restoring session, enforces checkpoint TTL/channel existence and respects the configured human-listener requirement.
- `/status` recovery diagnostics now include previous-history restored/failed counters in addition to transport and startup restore counters.
- Downgrade warning: binaries before v1.10.0 do not understand the V2 checkpoint header. Back up `music-sessions.tsv` before rollback once V2 has been written.

## [1.9.0] — 2026-08-09

### Queue Collaboration & Social UX

- `/queue-manage mine` показывает только ожидающие треки текущего пользователя, сохраняя глобальные позиции из `/queue`, длительность его части очереди и текущую queue revision.
- `/queue-manage remove-own position:<n> [revision]` позволяет обычному requester-у удалить один собственный ожидающий трек без DJ-прав. Чужая позиция возвращает ownership error; stale revision проверяется до ownership и не меняет очередь. Старые privileged `/remove`, `/move`, `remove-range` и `dedupe` остаются под существующей control policy.
- `/queue-manage community` строит локальную requester-aware сводку: число треков, длительность и глобальные позиции каждого участника, отсортированные по вкладу в очередь. Никакой дополнительной persistence или профилирования пользователей не добавлено.
- В `/queue` появились кнопки `👤 Мои треки`, `👥 Заказчики` и `🗳️ Vote skip`; они читают live queue state и не мутируют очередь. Pagination остаётся отдельным рядом и продолжает использовать текущие позиции/revision.
- `VoteSkipService` получил read-only snapshot текущего голосования: голоса, вычисленный порог, число eligible listeners и признак, голосовал ли текущий пользователь. Просмотр статуса не добавляет голос; `/skip`, `/voteskip` и кнопка skip сохраняют прежнюю policy.
- Добавлен чистый `QueueCollaboration` projection поверх `TrackRequest`; requester identity уже существовала в очереди, поэтому storage/session форматы и recovery snapshot не меняются.

## [1.8.0] — 2026-08-09

### Listening History & Personal Discovery

- Добавлена persistent personal history до 200 записей на `guildId + requesterUserId`: каждый replayable трек, который реально попадает в guild history, атомарно записывается и в личную историю его requester-а. Это не voice-presence telemetry и не утверждение, что пользователь прослушал трек целиком.
- `/history` и `/replay` получили optional `scope:server|mine`; старые вызовы без scope сохраняют прежнее server-history поведение. `/discover history` понимает тот же scope.
- `/discover profile` локально считает top-треки, частых исполнителей, число уникальных треков и favorites; повторы не дедуплицируются, поэтому частота действительно отражает повторные запуски в retained personal history.
- `/discover for-me` выбирает deterministic seed из personal favorites + personal history и передаёт его в существующий безопасный interactive search pipeline. Никакого внешнего recommendation API, отдельного профилирования или обхода request/voice policy не добавлено.
- Autocomplete `/play` и `/search` теперь объединяет recent queries → favorites → personal history → guild history → playlists, оставаясь полностью локальным и без сетевых запросов.
- `BASKOV_MUSIC_LIBRARY_V1` получил record `U <guildId> <userId> <position> <StoredTrack...>` для personal history. При первом чтении старого v1.7 файла пользователи без `U` автоматически получают best-effort backfill из retained guild history по `requesterUserId`; затем state сохраняется обычным atomic temp→replace.
- Playlist/favorite/history mutations сохраняют personal history map; owner-only permissions и существующий backup `music-library.tsv` автоматически покрывают новый state. Downgrade ниже `v1.8.0` после появления `U` records требует backup, потому что старый binary не знает этот record type.

## [1.7.0] — 2026-08-09

### Favorites & Personal Library

- Добавлена `/favorites` с subcommands `list`, `add`, `play`, `play-all`, `remove`, `search` и `clear`; список личный для каждого Discord user внутри конкретной guild.
- `/favorites add` сохраняет текущий replayable YouTube/SoundCloud track; одинаковый `provider + playbackIdentifier` не создаёт дубликат. Новые записи идут первыми, лимит — 100 favorites на пользователя и сервер.
- `play` и `play-all` переиспользуют существующий ordered batch loader, request/DJ policy, voice-channel restriction, queue bounds и playback readiness — отдельного обходного playback path нет.
- `/favorites search` выполняет локальный case-insensitive поиск по title/author и сохраняет исходные 1-based позиции; `/favorites clear` использует двухминутное owner/guild-bound интерактивное подтверждение.
- Autocomplete `/play` и `/search` теперь приоритетно объединяет recent queries → личные favorites → persistent history → playlists без сетевых запросов.
- Favorites сериализуются в существующий `BASKOV_MUSIC_LIBRARY_V1` как `F` records; старые файлы без favorites загружаются без миграции, а playlist/history mutations сохраняют user favorite maps.
- Существующий atomic `music-library.tsv`, owner-only permissions и persistence backup автоматически покрывают новый personal library state; новый storage file, external DB, Discord permissions или secrets не требуются. Downgrade на бинарник до `v1.7.0` после появления `F` records требует backup: старый код не сохраняет неизвестные favorite records при следующей library mutation.

## [1.6.4] — 2026-08-09

### Now Playing Controls State Hotfix

- `/now` теперь сразу строит двухрядный пульт из живого `GuildMusicManager`, поэтому кнопки pause/seek/next/shuffle/repeat/stop получают корректные enabled/disabled состояния уже в первом ответе.
- Slash `/seek` после успешной перемотки также возвращает state-aware пульт вместо zero-state fallback.
- Добавлен regression contract: production interaction responses больше не могут использовать `MusicControls.nowRows()` без manager там, где состояние музыкальной сессии доступно.
- Playback engine, queue/history semantics, persistence formats, voice recovery и CI/Maven bootstrap не меняются.

## [1.6.3] — 2026-08-09

### External Maven Dependency Bootstrap

- CI/delivery Maven cache переведён с нерасширяемого `setup-java cache: maven` на явный `actions/cache/restore` + `actions/cache/save`: новый project-owned cache сохраняется только после успешного `clean verify`, поэтому красный network run больше не замораживает неполный cache под exact key.
- Первый запуск мигрирует известный зелёный Maven cache `v1.5.0` как bootstrap seed. Он содержит закреплённые `youtube-source 1.18.2` и `libDAVE ce725965e`, поэтому временная недоступность `maven.lavalink.dev` не должна блокировать уже проверенные версии.
- Добавлен bootstrap inspector для восьми обязательных POM/JAR файлов; stale `*.lastUpdated` для этих координат очищаются до Maven, а diagnostics сохраняет `external-bootstrap.log`. Если seed отсутствует, Maven сохраняет обычный online fallback.
- После первого успешного запуска seed мигрируется в стабильный `baskov-maven-*` cache, ключ которого зависит от `.github/maven-cache-key.txt`, а не от версии приложения.
- Production/CI container больше не запускает второй Maven build внутри Docker. Новый `deploy/Dockerfile.ci` упаковывает ровно `target/baskov-discord-bot.jar`, который уже прошёл host `clean verify`; это устраняет вторую независимую зависимость delivery от Maven repositories.
- Host Maven verification получает `-Dbuild.revision=${GITHUB_SHA}`, поэтому JAR, переиспользуемый Docker image, сохраняет точный Git revision для `/version`.
- Обычный корневой `Dockerfile` сохранён для standalone source-build сценария; runtime Java/Discord/music/persistence behavior не меняется.

## [1.6.2] — 2026-08-09

### Maven Repository Routing Hotfix

- Maven Resolver groupId filtering включён для CI/delivery Maven invocations: сторонние Lavalink repositories больше не опрашиваются для обычных Spring Boot/JUnit/Jetty/Jackson/Netty и других Central artifacts.
- `lavalink-releases` разрешён только для `dev.lavalink.youtube`, а `lavalink-libdave-snapshots` — только для `moe.kyokobot.libdave`; Maven Central намеренно остаётся без groupId filter.
- Фильтры хранятся как project-owned `.mvn/rrf/groupId-<repository-id>.txt` и применяются как к `clean verify`, так и к `help:evaluate`, чтобы version resolution не возвращал прежний repository fan-out.
- Maven diagnostics теперь записывает активный filter type, basedir и routing-файлы в `environment.log`.
- Helper diagnostics перенесены в stderr, поэтому `maven-ci.sh version` отдаёт в stdout только чистую application version и безопасно используется через `GITHUB_OUTPUT`/Docker build-arg.
- Добавлен contract test, закрепляющий repository IDs, разрешённые groupId и отсутствие фильтра для Central.
- Runtime Java/Discord/music/persistence code не меняется; релиз устраняет подтверждённую по CI logs архитектурную причину многоминутного dependency resolution.

## [1.6.1] — 2026-08-09

### Maven Delivery Diagnostics & Resilience

- GitHub-hosted Java setup переведён с deprecated `actions/setup-java@v4` на `@v5`; Maven transfer progress явно включён через `show-download-progress: true`.
- Dependency cache больше не инвалидируется обычным bump версии приложения: `.github/maven-cache-key.txt` содержит отдельный fingerprint закреплённых runtime/build dependencies, а contract test сверяет его с `pom.xml` и Maven Wrapper.
- Добавлен `.github/scripts/maven-ci.sh`: bounded probes Maven Central/Lavalink, environment snapshot, два Maven attempts максимум по 420 секунд, retry только для timeout/network failures и очистка `*.lastUpdated` перед повтором.
- Compile/test failure не ретраится: настоящий красный код остаётся быстрым и однозначным failure.
- `SEGMENT_DOWNLOAD_TIMEOUT_MINS=2` ограничивает зависший restore cache segment; CI job timeout поднят до 30 минут как внешний last-resort guard.
- CI и delivery всегда сохраняют `maven-diagnostics-<run-id>` artifact, поэтому сетевой stall больше не превращается в 30 минут без данных.
- Runtime Java/Discord/music/persistence code не меняется; релиз касается только CI/CD delivery tooling, документации и contract coverage.

## [1.6.0] — 2026-08-09

### Discord Experience

- `/help` превращён в компактную интерактивную справку с разделами `overview`, `playback`, `queue`, `library`, `admin` и кнопочным переключением без повторной slash-команды.
- `/status` получил read-only кнопку `↻ Обновить статус`: live storage probe, backup/recovery/gateway/command metrics пересчитываются в том же ephemeral-сообщении.
- `/stop`, непустой `/clear`, `/playlist delete` и `/settings reset` теперь используют одноразовые двухминутные confirmation sessions с кнопками `Подтвердить` / `Отмена`.
- Confirmation token привязан к Discord guild + user, потребляется атомарно и повторно проверяет права непосредственно перед destructive mutation; повторный/просроченный клик ничего не выполняет.
- Stop-кнопка под `/now` использует ту же confirmation-модель, что и slash `/stop`.
- `/settings reset` больше не требует текстового `confirm:true`: подтверждение перенесено в Discord component UI.

### Safety & compatibility

- Подтверждение не хранит secrets и не переживает restart процесса; его назначение — защита от случайного клика, а не persistent workflow.
- Playback, queue, library, guild-settings и backup форматы не меняются. Старые prefix-команды остаются compatibility layer.
- Повторная авторизация перед подтверждённым действием защищает от изменения DJ/manager permissions между slash-командой и нажатием кнопки.

## [1.5.0] — 2026-08-08

### Permissions & Guild Administration

- Добавлена `manager-role`: владелец, `Manage Server` и участники этой роли могут администрировать guild settings и выполнять административные операции библиотеки.
- Playback-control и добавление музыки разделены на две независимые политики: существующий `access` (`open|dj|vote`) и новый `request-access` (`open|dj`).
- Добавлена `/settings voice-channel`: новые `/play`, `/search`, discovery/replay и playlist playback можно ограничить одним voice/stage каналом.
- `/settings permissions` показывает итоговую матрицу доступа, DJ/manager роли, voice restriction и vote-skip threshold.
- `/settings export` / `/settings import` получили переносимый `BASKOV_SETTINGS_V1` профиль; импорт проверяет роли/канал целевой guild и записывает профиль одной atomic persistence mutation.
- `/settings audit` показывает последние 10 сохранённых изменений с actor Discord user ID и временем; audit хранится в том же `guild-settings.properties` и автоматически попадает в существующие persistence backups.
- `/settings reset` теперь требует явный `confirm:true`, чтобы исключить случайный полный сброс.
- Legacy `guild-settings.properties` остаются совместимыми: отсутствующие поля получают `request-access=open`, manager-role/music-channel = `0`.

### Safety & compatibility

- `@everyone` нельзя назначить ни DJ-, ни manager-role. Import отклоняет отсутствующие роли, несуществующий/non-audio канал, повреждённый profile и громкость выше configured max.
- Ограничение music-channel применяется даже к привилегированному enqueue: оно описывает разрешённое место музыкальной сессии, а не пользовательскую роль.
- Форматы music library/session checkpoints, queue revisions, backups, voice recovery и deployment topology не меняются; расширяется только backwards-compatible guild settings file.

## [1.4.0] — 2026-08-08

### Search & Discovery 2.0

- Добавлена `/discover` с режимами `recent`, `again`, `related` и `history`.
- `/discover again` повторяет последний текстовый интерактивный поиск без копирования команды вручную.
- `/discover related` строит новый безопасный YouTube-поиск из исполнителя и названия текущего трека; `/discover history` делает то же для позиции из `/history`. Это контекстный поиск, а не персональная рекомендационная модель.
- `/discover recent` показывает до 10 последних запросов пользователя; история остаётся in-memory и очищается при restart процесса.
- Autocomplete `/play` и `/search` теперь объединяет недавние запросы пользователя с треками из persistent history и плейлистов сервера, дедуплицирует их и остаётся полностью локальным — без сетевых запросов во время набора.
- Общий `startInteractiveSearch` переиспользует прежние ограничения `MediaQueryResolver`, пятиминутные одноразовые search sessions и максимум пять кандидатов.
- Добавлены unit/contract tests для discovery query derivation, merged autocomplete, recent/last history и каталога slash-команд.

## [1.3.0] — 2026-08-08

### Library & Playlists 2.0
- `/playlist` получил lifecycle-операции `rename`, `copy`, `move` и `dedupe` без изменения существующего TSV-формата `BASKOV_MUSIC_LIBRARY_V1`.
- Добавлен `/playlist capture-queue`: текущий трек и ожидающая очередь преобразуются в replayable `StoredTrack` и добавляются в целевой плейлист одной atomic persistence mutation; `include-current:false` сохраняет только ожидающие треки.
- Добавлен `/playlist add-history`: любой replayable трек из постоянной `/history` можно повторно сохранить в плейлист по его позиции.
- Добавлен `/playlist search`: поиск без учёта регистра по названию плейлиста, названию трека и исполнителю; результат показывает совпавшие позиции внутри плейлистов.
- `copy` создаёт независимую копию с новым владельцем — автором команды; исходный плейлист не изменяется.
- `dedupe` сохраняет первую копию каждого `provider + playbackIdentifier`, удаляет последующие и сообщает число удалённых повторов.

### Безопасность и совместимость
- Изменяющие операции (`rename`, `move`, `dedupe`, `capture-queue`, `add-history`) сохраняют существующую модель owner-or-`Manage Server`; `copy` не требует права изменения исходника, потому что создаёт новый объект пользователя.
- Bulk capture заранее проверяет лимит 50 треков и либо сохраняет весь набор, либо ничего не меняет; частичных плейлистов после переполнения нет.
- Форматы `music-library.tsv`, history, guild settings, session checkpoints, backup ZIP, queue revision, voice recovery и deployment topology не меняются.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, `youtube-source 1.18.2` и native libDAVE `ce725965e` остаются закреплены.

## [1.2.0] — 2026-08-08

### Queue Management 2.0
- `/queue` показывает session-local ревизию ожидающей очереди, число уникальных заказчиков, число повторов и суммарную длительность ожидающих треков.
- Добавлена группа `/queue-manage`: `stats`, `remove-range`, `dedupe` и `remove-mine`. Старые `/remove`, `/move`, `/shuffle` и `/clear` сохранены без переименования.
- `remove-range` удаляет непрерывный диапазон позиций атомарно; `dedupe` сохраняет первую копию каждого трека и удаляет последующие; `remove-mine` удаляет только ожидающие треки самого автора команды.
- Mutating subcommands принимают необязательный `revision`; если очередь успела измениться, операция отклоняется как stale вместо применения к уже другим позициям.
- `TrackScheduler` получил monotonic queue revision, immutable queue stats и атомарные batch-mutation результаты с количеством удалённых треков, освобождённой длительностью и новой ревизией.

### Безопасность и совместимость
- `remove-range` и `dedupe` проходят существующую `MusicControlPolicy`; `remove-mine` не даёт доступа к чужим позициям и работает только по Discord user ID requester-а.
- Текущий трек batch-операции не затрагивают: меняется только bounded waiting queue.
- Форматы persistence, session checkpoint, guild settings, playlists/history, voice recovery, DAVE/YouTube stack и deployment topology не меняются.

## [1.1.0] — 2026-08-08

### Operations & Reliability
- Добавлен `PersistenceBackupService`: после startup preflight создаётся atomic ZIP snapshot `guild-settings.properties`, `music-library.tsv` и `music-sessions.tsv`, затем backup повторяется по расписанию.
- Backup хранится внутри persistent `/app/data` volume, использует формат `BASKOV_PERSISTENCE_BACKUP_V1`, temp + atomic move, POSIX `0700/0600` и bounded retention.
- Новые параметры: `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED`, `..._INTERVAL` и `..._RETENTION`; каталог production backup закреплён как `/app/data/backups`.
- `/status` получил секции `Persistence backups` и `Reliability`, live storage probe, gateway transition/disconnected counters, command invocation total, failure rate и время последней ошибки.
- Runtime heartbeat теперь сохраняет `gatewayTransitions` и `disconnectedSamples`, а монитор помнит последнее CONNECTED и последнее изменение gateway status.
- `PersistenceReadiness.probe()` повторно проверяет storage во время `/status` без остановки процесса: деградация отображается сразу, а следующий успешный probe возвращает READY.
- Application file logs переведены на size+time rolling: 25 MB на файл, 14 дней, общий cap 512 MB; Docker json logs остаются ограничены 3 × 10 MB.

### Delivery hardening
- GitHub delivery передаёт backup policy в защищённый deployment input и валидирует boolean/interval/retention до записи `.env`.
- Post-deploy runtime verification требует startup marker успешного persistence backup или явного `Persistence backup disabled`.
- GitHub-hosted `ubuntu-latest`, immutable SHA image, OCI digest verification, health heartbeat и rollback topology сохранены.

### Совместимость
- Форматы `guild-settings.properties`, `music-library.tsv` и `music-sessions.tsv` не меняются; backup — дополнительный read-only snapshot этих файлов.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, voice recovery, DJ/vote, playlists/history и Discord UX остаются без изменений.
- Новых Discord permissions, внешней БД или Secrets не требуется.


## [1.0.1] — 2026-08-08

### Fixed
- Исправлена Spring wiring-ошибка `PersistenceReadiness`: production-конструктор явно помечен `@Autowired`, поэтому `@SpringBootTest(discordBot.enabled=false)` снова поднимает ApplicationContext при наличии тестового package-private конструктора.
- Обновлён `NativeDaveIntegrationContractTest` под digest-aware сигнатуру `verify_runtime <image> <digest> <require_native_dave>`; контракт теперь проверяет и основной deployment, и rollback-вызов без устаревшей двухаргументной формы.

### Scope
- Runtime-поведение Discord, libDAVE, YouTube source, voice recovery, persistence formats и deployment topology не менялись.
- `v1.0.0` остаётся нетегированным красным кандидатом; тег ставится только после зелёного CI/CD и runtime smoke-test `v1.0.1`.

## [1.0.0] — 2026-08-08

### Добавлено

- Production storage preflight для `guild-settings.properties`, `music-library.tsv` и `music-sessions.tsv`: три разных пути, обычные файлы без symlink и реальная write/delete probe перед подключением к Discord.
- Startup marker `Persistence readiness: READY`; `/status` получил безопасную секцию `Storage readiness` без раскрытия абсолютных путей.
- Git revision в Spring Boot build-info; `/version` показывает короткий commit рядом с версией и Java.
- Remote verification опубликованного OCI digest поверх уже существующего immutable `sha-<commit>` tag.

### Hardening

- `actions/checkout` больше не сохраняет GitHub credential в local git config (`persist-credentials: false`) в CI и delivery jobs.
- Docker build получает `APP_REVISION=${github.sha}` и встраивает revision в packaged build metadata.
- Deploy передаёт digest из `docker/build-push-action` на VPS и отклоняет image, если локальный `RepoDigest` не совпадает с опубликованным digest.
- Post-deploy runtime gate дополнительно требует `Persistence readiness: READY`; при провале сохраняется существующий rollback path.
- Основной pipeline закреплён обратно на `ubuntu-latest`; self-hosted runner документирован только как резервный режим.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, Docker bridge default и формат persistent-файлов не меняются.
- Voice recovery, restart restoration, playlists/history, DJ/vote и Discord UX v0.16 остаются совместимыми.
- Новых внешних сервисов, БД, Discord permissions или Secrets не требуется.

## [0.16.0] — 2026-08-08

### Добавлено

- `/now` получил кнопку `↻ Обновить`, которая перерисовывает текущий progress/state без повторного ввода slash-команды.
- Пульт `/now` теперь state-aware: показывает `Пауза` или `Продолжить`, текущий repeat mode и disabled-состояния для недоступных previous/seek/shuffle/stop/skip действий.
- Добавлен архитектурный контракт delivery runner-а и временных SSH credentials.
- В delivery context выводятся имя, OS/arch и workspace фактического runner-а для диагностики исполнения.

### Изменено

- После практического теста домашнего self-hosted runner все три delivery jobs (`context`, `publish`, `deploy`) возвращены на стандартный `ubuntu-latest`: медленный обязательный VPN делал Docker/BuildKit downloads неприемлемо долгими.
- Deployment SSH credentials создаются через `mktemp` внутри `${RUNNER_TEMP}`, явно передаются в `ssh/scp` и удаляются шагом `always()`.
- Обработка ошибок component buttons теперь возвращает ephemeral failure message даже после уже выполненного interaction acknowledgement.

### Сохранено

- Build/publish/deploy topology, immutable GHCR SHA image, Maven `clean verify`, Docker Buildx, healthcheck, rollback и protected deployment input сохраняются.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, voice recovery, checkpoint format и DJ/vote runtime не меняются.


## [0.15.1] — 2026-08-06

### Исправлено

- Обновлён устаревший `VoiceRootCauseDiagnosticsContractTest`: после `v0.15.0` watchdog больше не обязан содержать старую ветку `if (!properties.isVoiceWatchdogEnforce())`, потому что при включённом session recovery transport failure передаётся в bounded `recoverVoiceSession(...)`.
- Контракт теперь проверяет фактическую семантику: безопасный default `DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=false`, наличие recovery-path, вызов `recoverVoiceSession(guild, reason)` и расположение legacy `stopAndRelease` enforcement только после recovery-ветки.
- Документация root-cause diagnostics синхронизирована с новым разделением: `voice-recovery-enabled` управляет восстановлением, а `voice-watchdog-enforce` остаётся аварийным legacy fallback при отключённом recovery.

### Сохранено

- Production runtime, checkpoint format `BASKOV_MUSIC_SESSIONS_V1`, startup/pending restore, bounded reconnect, slash-команды и deployment pipeline не меняются.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e` и Docker bridge остаются закреплены.
- Тестовый baseline остаётся 66 test source files / 233 `@Test` methods.


## [0.15.0] — 2026-08-06

### Добавлено

- Atomic checkpoint активной music/voice-сессии в `music-sessions.tsv` с форматом `BASKOV_MUSIC_SESSIONS_V1`, Base64-safe полями и POSIX `0600`.
- Startup restoration после `JDA.awaitReady()`: voice channel, текущий трек и позиция, очередь, pause, volume и repeat загружаются после restart/redeploy.
- Pending restore: бот не входит в пустой voice channel; первый вернувшийся человек запускает восстановление свежего checkpoint автоматически.
- Bounded runtime voice recovery для неожиданного self `LEAVE` и подтверждённого отсутствия frame polling: до трёх попыток с линейным backoff.
- Секция `Voice recovery` в `/status` со счётчиками checkpoint, in-progress, transport attempts/success/fail, startup restored/failed и последним operational event.
- Конфигурация `DISCORD_BOT_MUSIC_SESSION_*` для пути, интервала checkpoint, TTL, startup restore, human-listener gate и recovery attempts/backoff.
- Документ `docs/VOICE-RECOVERY-SESSION-RESTORATION.md`.

### Изменено

- JDA auto-reconnect остаётся выключенным, но каждая bounded попытка `VoiceConnectionCoordinator` теперь может быть повторно вызвана recovery coordinator-ом с контролируемым обходом cooldown.
- При transport failure текущий `AudioPlayer` временно ставится на pause; после успешного reconnect продолжается тот же in-memory трек и возвращается исходный pause state.
- При исчерпании recovery-попыток runtime-сессия освобождается, но checkpoint не удаляется и остаётся доступным для следующего restart/return listener.
- Graceful shutdown сначала сохраняет актуальное состояние всех активных сессий и только затем уничтожает players и voice transport.
- Watchdog при включённом session recovery больше не уничтожает очередь сразу, а передаёт управление bounded recovery path.

### Безопасность и отказоустойчивость

- В checkpoint не сериализуются `AudioTrack`, decoder state, Discord token, DAVE keys, cookies, OAuth, poToken или голосовые пакеты — только публичные replayable identifiers и ограниченные метаданные.
- При загрузке файла строго проверяются boolean-поля и соответствие сохранённого provider публичному YouTube/SoundCloud URL; повреждённая строка игнорируется.
- На сервер хранится максимум один checkpoint на guild; очередь дополнительно ограничена runtime `maxQueueSize` и абсолютной границей 1000 записей.
- Старые, удалённые guild/channel и checkpoint старше TTL очищаются; пустой канал оставляет checkpoint pending без фонового JOIN/LEAVE-цикла.
- Удалённый или недоступный трек пропускается при restore, а остальные элементы загружаются последовательно в сохранённом порядке.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer core 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, Docker bridge, DJ/vote, плейлисты и история не меняются.
- Новых slash-команд, зависимостей и Secrets нет.
- Тестовый baseline повышен до 66 test source files / 233 `@Test` methods.


## [0.14.0] — 2026-08-06

### Добавлено

- Три постоянных режима управления: `open`, `dj` и `vote`, настраиваемые через `/settings access`.
- Назначаемая DJ-роль через `/settings dj-role`; владелец сервера и `Manage Server` сохраняют административный override.
- Slash-команда `/voteskip`; в режиме `vote` обычные слушатели также голосуют через `/skip` и кнопку `Следующий` под `/now`.
- Настраиваемый порог `/settings vote-threshold` от 25 до 100 процентов, по умолчанию 50%, с округлением требуемых голосов вверх.
- Потокобезопасный `VoteSkipService`: уникальный голос на Discord user, одна bounded session на guild, привязка к конкретному playback и шестичасовой safety TTL.
- Секция `DJ & voting` в `/status` и расширенное отображение `/settings show`.
- Документ `docs/DJ-ROLES-AND-VOTING.md`.

### Изменено

- В `open` сохранено прежнее правило общего voice channel.
- В `dj` прямое управление доступно администраторам и настроенной DJ-роли; обычные слушатели по-прежнему могут добавлять треки.
- В `vote` DJ управляет напрямую, а обычные слушатели голосуют только за пропуск; другие playback-мутации остаются DJ-only.
- Кнопка `Следующий` использует тот же policy path, что `/skip` и `/voteskip`, без обхода access mode.
- `guild-settings.properties` расширен ключами `access`, `dj-role` и `vote-skip-percent`, сохраняя чтение старых файлов с одними `volume`/`repeat`.

### Безопасность и отказоустойчивость

- `@everyone` нельзя назначить DJ-ролью; очистка роли выполняется `/settings dj-role` без параметра.
- DJ без `Manage Server` обязан находиться в voice channel бота; административный cross-channel override не изменён.
- Боты не входят в число голосующих слушателей, повторный голос одного пользователя дедуплицируется.
- Голоса не сохраняются на диск и сбрасываются после успешного пропуска, stop, смены access-настроек или reset.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer core 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, Docker bridge, плейлисты и история не меняются.
- `/play`, `/search`, `/playlist play` и `/replay` остаются доступны обычным участникам в общем voice channel во всех режимах.
- Тестовый baseline повышен до 62 test source files / 220 `@Test` methods.


## [0.13.0] — 2026-08-05

### Добавлено

- Постоянная серверная история воспроизведения с командами `/history [page]` и `/replay position`; хранится до 50 последних воспроизводимых записей на Discord-сервер.
- Серверные плейлисты: `/playlist list|create|show|add|play|remove|delete`, autocomplete имени и постраничный просмотр.
- Owner-bound изменения плейлиста с административным override для `Manage Server`; лимиты 20 плейлистов на сервер и 50 треков на плейлист.
- Ordered batch loading сохранённых URL: плейлист повторно загружается в исходном порядке и возвращает единый итог started/queued/rejected.
- Отдельный atomic TSV-файл `music-library.tsv` с форматом `BASKOV_MUSIC_LIBRARY_V1`, Base64-кодированием пользовательских строк и POSIX `0600`.
- Конфигурация `DISCORD_BOT_MUSIC_LIBRARY_FILE` и постоянный Docker-путь `/app/data/music-library.tsv`.
- Поле `Persistent library` в `/status`, показывающее количество плейлистов и глубину истории текущего сервера.
- Документ `docs/PLAYLISTS-HISTORY-REPLAY.md`.

### Изменено

- Завершённый или вручную пропущенный трек публикуется из `TrackScheduler` в постоянную историю через отдельный single-thread daemon executor, поэтому файловый I/O не выполняется на LavaPlayer/JDA audio callback thread.
- `/previous` сохраняет быстрый in-memory clone-путь текущей сессии, а `/replay` загружает публичный URL из постоянной истории после restart/redeploy.
- Source failures, premature preview, `404`, stuck/cleanup recovery и неуспешные fallback-кандидаты не загрязняют постоянную историю.
- `/help`, `/status`, README и каталог slash-команд синхронизированы с библиотекой.

### Безопасность и отказоустойчивость

- В хранилище не сериализуются `AudioTrack`, Discord token, cookies, OAuth или другие секреты — только ограниченные метаданные и повторно загружаемый YouTube/SoundCloud URL.
- Имена плейлистов нормализуются, сравниваются без учёта регистра, ограничены 40 символами и не принимают управляющие символы.
- Файловая мутация откатывает in-memory состояние при ошибке atomic persistence.
- Shutdown recorder не может сорвать переход к следующему треку: новые history tasks после закрытия executor тихо отбрасываются.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer core 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e`, Docker bridge и voice foundation не меняются.
- `/play`, `/search`, очередь, previous, seek, repeat, shuffle, source recovery и real frame polling остаются совместимыми.
- Тестовый baseline повышен до 59 test source files / 206 `@Test` methods.



## [0.12.2] — 2026-08-05

### Исправлено

- Исправлен второй compile blocker миграции `/search`: reflection-helper `legacyYoutubeSourceClass()` теперь возвращает `Class<? extends AudioSourceManager>`, совместимый с varargs-сигнатурой `AudioSourceManagers.registerRemoteSources(...)`.
- Разрешённый через `Class.forName(...)` legacy YouTube-класс приводится безопасно через `Class#asSubclass(AudioSourceManager.class)`, поэтому deprecated class literal по-прежнему не используется.
- README синхронизирован с фактической стабильной версией.

### Сохранено

- Modern `youtube-source 1.18.2` остаётся единственным YouTube-движком; встроенный extractor LavaPlayer исключается из auto-registration.
- `/search`, JDA 6.5.0, LavaPlayer core 2.2.3, native libDAVE `ce725965e`, Docker bridge и runtime-логика воспроизведения не меняются.
- Тестовый baseline повышен до 188 `@Test` methods.


## [0.12.1] — 2026-08-05

### Исправлено

- Исправлена несовместимость JDA 6 в `/search`: `ActionRow.of(...)` теперь получает `Collection<Button>` вместо массива `Button[]`, который не соответствует сигнатуре JDA 6.
- Удалена compile-time ссылка на deprecated встроенный `YoutubeAudioSourceManager` LavaPlayer. Legacy extractor по-прежнему исключается из автоматической регистрации через безопасное разрешение класса по имени.
- Добавлены статические контракты, запрещающие возврат к массивному overload `ActionRow.of(...)` и прямому deprecated class literal.

### Сохранено

- Логика `/search`, owner-bound одноразовые сессии, modern `youtube-source 1.18.2`, JDA 6.5.0, native libDAVE и Docker bridge не меняются.
- Тестовый baseline повышен до 187 `@Test` methods.


## [0.12.0] — 2026-08-05

### Добавлено

- Новая slash-команда `/search query`, показывающая до пяти результатов modern YouTube source до подключения к voice.
- Ephemeral-карточка выбора с названием, автором, длительностью, провайдером и кнопками `1–5`.
- Короткоживущая owner-bound search session: Discord guild ID + user ID, пятиминутный TTL, максимум пять кандидатов и одноразовый atomic claim.
- Кнопка отмены, закрывающая результаты без создания музыкальной сессии.
- Autocomplete недавних запросов теперь работает и для `/search`, и для `/play`.

### Изменено

- `/play` остаётся быстрым one-tap сценарием с первым результатом, а `/search` позволяет выбрать официальную запись, live, кавер или нужную длительность.
- Выбранный `AudioTrack` передаётся в `TrackScheduler` через `queueLoadedTrack(...)` без второго YouTube lookup, поэтому показанный и добавленный результаты совпадают.
- Voice connection и `MusicControlPolicy` проверяются только после выбора; сам поиск не подключает бота к каналу.
- Search component ID содержит только случайный token и номер позиции — URL, название и внутренний identifier в Discord payload не попадают.

### Безопасность и отказоустойчивость

- Чужой пользователь не может выбрать или отменить результаты поиска.
- Повторное или конкурентное нажатие одной кнопки не добавляет трек дважды.
- Истёкшие, повреждённые и уже использованные component ID очищают кнопки и предлагают повторить `/search`.
- Активные search sessions ограничены и очищаются opportunistic cleanup; они намеренно не переживают restart контейнера.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer core 2.2.3, `youtube-source 1.18.2`, native libDAVE `ce725965e` и Docker bridge не меняются.
- `/play`, очередь, previous, seek, repeat, shuffle, source recovery и real frame polling остаются совместимыми.

### Тестирование

- Добавлены unit-тесты component ID, owner binding, single-use claim, cancel, bounded candidate list и immutable search result.
- Добавлен architecture contract `SearchTrackSelectionContractTest`.
- Тестовый baseline повышен до 54 test source files / 186 `@Test` methods.


## [0.11.4] — 2026-08-05

### Исправлено

- Устранён production blocker `v0.11.3`: встроенный deprecated `YoutubeSearchProvider` LavaPlayer `2.2.3` отвечал `Invalid status code for search response: 400` ещё до выбора трека.
- Подключён отдельный modern YouTube engine `dev.lavalink.youtube:v2:1.18.2` из release-репозитория Lavalink.
- Legacy `com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager` исключён из автоматической регистрации remote sources.
- Ошибка Discord autocomplete `10062 Unknown interaction` обрабатывается явным failure callback и больше не создаёт большой production stack trace.

### Добавлено

- Startup marker с engine, version, default multi-client order и подтверждением отключённого legacy extractor.
- Deployment gate требует modern YouTube marker вместе с native libDAVE marker.
- `/status` показывает `YouTube engine: youtube-source 1.18.2`.
- Документ `docs/MODERN-YOUTUBE-SOURCE.md`.

### Сохранено

- Plain text по-прежнему маршрутизируется через `ytsearch:`, SoundCloud доступен только по прямым ссылкам.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer core 2.2.3, native libDAVE `ce725965e` и Docker bridge не меняются.
- OAuth, poToken и новые секреты не добавляются.

### Тестирование

- Добавлены unit-тесты runtime identity и architecture contract регистрации/версии/deployment/autocomplete.
- Тестовый baseline повышен до 51 test source files / 175 `@Test` methods.


## [0.11.3] — 2026-08-05

### Изменено

- Обычные текстовые запросы `/play` и legacy `!search` теперь преобразуются в `ytsearch:`; YouTube снова является основным музыкальным провайдером.
- Прямые ссылки YouTube и SoundCloud по-прежнему принимаются без сетевого запроса на этапе разбора команды.
- Скрытый дедуплицированный fallback pool до девяти кандидатов применяется к YouTube и legacy SoundCloud search results.
- В подтверждении загрузки, `/now` и `/queue` показывается фактический источник трека: YouTube, SoundCloud, HTTP или неизвестный.
- Startup-лог и `/status` явно фиксируют YouTube как основной search provider.

### Сохранено

- SoundCloud preview/404 diagnostics и premature-finish recovery остаются для прямых SoundCloud ссылок и старых совместимых запросов.
- Java 17, Spring Boot 3.4.3, JDA 6.5.0, LavaPlayer 2.2.3, native libDAVE `ce725965e` и Docker bridge не меняются.

### Тестирование

- Добавлены unit-тесты маршрутизации YouTube/SoundCloud и распознавания provider по identifier/URI.
- Добавлен architecture contract, запрещающий возврат plain-text поиска на `scsearch:`.
- Тестовый baseline повышен до 49 test source files / 168 `@Test` methods.


## [0.11.2] — 2026-08-05

### Исправлено

- Найден production root cause короткого воспроизведения: SoundCloud-трек с заявленной длительностью `03:13` завершался через 30 секунд с reason `FINISHED`, а другие HLS media URL возвращали `404`.
- `TrackScheduler` теперь распознаёт преждевременный `FINISHED` как обрезанный preview/source failure и пробует следующий скрытый fallback вместо ложного успешного завершения.
- Преждевременно завершённый результат не попадает в history и не активирует repeat mode.
- Глубинный `IOException`/HTTP-код больше не теряется под общим `FriendlyException: Something broke when playing the track`.
- Fallback и stale callback больше не затирают последнюю реальную source-ошибку в `/status`.

### Изменено

- SoundCloud search хранит до девяти дедуплицированных скрытых fallback-кандидатов вместо четырёх; пользовательская очередь и её лимит не меняются.
- `/status` отдельно показывает `Last source error`, `Last recovery` и `Last stale callback`.

### Сохранено

- JDA `6.5.0`, native libDAVE `ce725965e`, Docker bridge, real frame polling, playback controls и bounded deque не менялись.

### Тестирование

- Добавлены unit-тесты preview detection, early-finish recovery, deepest-cause diagnostics и защиты root source error.
- Добавлен architecture contract `SourceStreamingStabilityContractTest`.


## [0.11.1] — 2026-08-05

### Исправлено

- Обновлены два устаревших architecture source-contract после перехода `TrackScheduler` с `LinkedBlockingQueue` на bounded `LinkedBlockingDeque` в `v0.11.0`.
- `QueueExperienceContractTest` теперь закрепляет фактическую deque-модель, отдельный bounded history reserve и неизменный пользовательский лимит очереди.
- `MusicSessionSafetyContractTest` проверяет реальную защиту `queue.size() >= maxQueueSize`, а не удалённую строку конструктора старой очереди.

### Сохранено

- Runtime playback-код, `/previous`, история, расширенный `/now`, JDA `6.5.0`, native libDAVE `ce725965e` и Docker bridge не менялись.

### Тестирование

- Baseline остаётся 44 test source files / 145 tests.
- Hotfix устраняет только два ложных красных architecture-contract результата после успешных `compile`, `testCompile`, `NativeDaveRuntimeTest` и всех runtime unit-тестов.


## [0.11.0] — 2026-08-05

### Добавлено

- Команда `/previous`, возвращающая последний завершённый или вручную пропущенный трек текущей guild-сессии.
- Ограниченная история до 25 треков с сохранением requester и безопасным клонированием LavaPlayer track instances.
- Двухрядный пульт `/now`: previous, −15 секунд, pause/resume, +15 секунд, next, queue, shuffle, repeat и stop.
- Быстрые seek-кнопки с clamp к началу и концу трека.
- Секция `Playback modes` в `/status` со состоянием PLAYING/PAUSED/IDLE, repeat, volume, history depth и seek availability.

### Изменено

- При возврате назад прерванный текущий трек ставится первым в очередь, поэтому пользователь может продолжить прежнюю последовательность.
- История не запоминает cleanup, stuck и playback-exception источники, чтобы `/previous` не возвращал заведомо сломанный media result.
- Внутренняя очередь переведена на bounded deque; пользовательский лимит очереди остаётся прежним, а history navigation имеет отдельный безопасный резерв.
- `/seek` после выполнения показывает расширенный пульт `/now`.

### Тестирование

- Добавлены unit-тесты previous/history, расширенного пульта и playback status formatter.
- Добавлен architecture contract на slash catalog, bounded history, seek buttons и live playback modes.
- Тестовый baseline повышен до 43 test classes / 145 `@Test` methods.


## [0.10.0] — 2026-08-05

### Добавлено

- Опциональный параметр `/queue page` для прямого перехода к нужной странице очереди.
- Кнопки `◀ Назад` и `Вперёд ▶`, которые редактируют существующее сообщение очереди вместо создания новых ответов.
- Неизменяемая модель `QueuePage` с 10 позициями на страницу, глобальными номерами и безопасным clamp для устаревших или слишком больших номеров страниц.
- Визуальная шкала прогресса и оставшееся время в `/now` и в блоке текущего трека `/queue`.
- ETA старта для каждой отображаемой позиции очереди.

### Изменено

- `/queue`, кнопка `Очередь` и legacy `!TrackList` используют одну модель пагинации и одинаковые номера для `/remove` и `/move`.
- Read-only навигация по страницам не требует нахождения в voice channel и не проходит через mutation-policy.
- Заголовок и footer очереди показывают текущую страницу, диапазон и общее число треков.
- README исправлен на фактическую стабильную версию и документирует post-DAVE пользовательский интерфейс.

### Тестирование

- Добавлены unit-тесты `QueuePage`, динамических component id и progress bar.
- Добавлен architecture contract на slash option, read-only button flow, глобальные позиции, ETA и legacy parity.
- Тестовый baseline повышен до 42 test classes / 137 `@Test` methods.


## [0.9.5] — 2026-08-04

### Исправлено

- Устранён dependency-resolution blocker `v0.9.4`: артефакты `adapter-jda:0.1.3` и `impl-jni:0.1.3` отсутствуют и в Maven Central, и в ошибочно подключённом JitPack.
- libdave-jvm переведён на документированную commit-snapshot поставку `ce725965e` из `https://maven.lavalink.dev/snapshots`. Это первые 9 символов commit SHA тега `0.1.3`.
- Удалён бесполезный репозиторий JitPack; Java adapter, JNI implementation и platform natives используют один и тот же immutable commit pin.

### Сохранено

- Native libDAVE bootstrap, fail-fast protocol gate, JDA `6.5.0`, Java 17, Spring Boot `3.4.3` и LavaPlayer `2.2.3`.
- Production Docker `bridge`, playback readiness по первому Discord audio-frame poll и rollback deployment.

### Тестирование

- Architecture contract проверяет Lavalink snapshot repository, commit pin `ce725965e`, отсутствие JitPack и отсутствие возврата к неразрешимому `0.1.3`.
- Тестовый baseline повышен до 120 `@Test` methods.


## [0.9.4] — 2026-08-04

### Исправлено

- Устранена подтверждённая причина Discord close code `4017`: JDA 6 больше не использует `PassthroughDaveSessionFactory` с maximum protocol version `0`.
- Подключена настоящая JNI-реализация Discord DAVE/E2EE через `libdave-jvm 0.1.3`.
- Startup становится fail-fast, если native-библиотека не загрузилась или объявила неположительную DAVE protocol version.

### Добавлено

- Зависимости `adapter-jda`, `impl-jni`, production native `natives-linux-x86-64` и developer native `natives-win-x86-64`.
- `NativeDaveBootstrap`, передающий `LDJDADaveSessionFactory` в `AudioModuleConfig`.
- `/status` показывает DAVE implementation, version, maximum protocol и native platform.
- Реальный JNI smoke-test на Linux/Windows x86-64 и source-contract, запрещающий возврат passthrough DAVE.
- Документ `docs/NATIVE-DAVE.md`.

### Сохранено

- Java 17, Spring Boot 3.4.3, JDA 6.5.0 и LavaPlayer 2.2.3.
- Docker bridge production mode, playback readiness по первому media frame poll и весь voice diagnostics flow.

### Тестирование

- Тестовый baseline повышен до 38 test classes / 119 `@Test` methods.
- `NativeDaveRuntimeTest` реально загружает JNI runtime на Linux/Windows x86-64 и проверяет положительную protocol version.
- Deployment gate требует startup marker native libDAVE, но rollback сохраняет совместимость с предыдущим зелёным образом.


## [0.9.3] — 2026-08-04

### Исправлено

- `VoiceDiagnosticsTest` использует фактический JDA 6 тип `SelfMember`, возвращаемый `Guild#getSelfMember()`, вместо общего `Member`.
- Устранён второй обнаруженный `testCompile` blocker JDA 6 migration после успешного исправления `VoiceConnectionCoordinatorTest`.
- `JdaSixSourceMigrationContractTest` теперь сканирует все Mockito-fixture с `Guild#getSelfMember()` и запрещает несовместимый `mock(Member.class)` во всём тестовом дереве.

### Изменено

- Maven-версия приложения повышена до `0.9.3`.
- Runtime DAVE/E2EE migration, JDA `6.5.0`, Spring Boot `3.4.3`, Java 17, LavaPlayer `2.2.3`, Docker и deployment не менялись.

### Тестирование

- Тестовый baseline остаётся 35 test classes / 109 `@Test` methods.
- Hotfix предназначен для продолжения полного `clean verify` после успешной компиляции production-кода и первого исправленного JDA 6 fixture.

## [0.9.2] — 2026-08-04

### Исправлено

- `VoiceConnectionCoordinatorTest` использует фактический JDA 6 тип `SelfMember`, возвращаемый `Guild#getSelfMember()`, вместо общего `Member`.
- Устранён единственный обнаруженный `testCompile` blocker миграции JDA `6.5.0`.
- `JdaSixSourceMigrationContractTest` защищает fixture от возврата к несовместимому типу `Member`.

### Изменено

- Maven-версия приложения повышена до `0.9.2`.
- Runtime DAVE/E2EE migration, JDA `6.5.0`, Spring Boot `3.4.3`, Java 17, LavaPlayer `2.2.3`, Docker и deployment не менялись.

### Тестирование

- Тестовый baseline составляет 35 test classes / 109 `@Test` methods.
- Hotfix предназначен для продолжения полного `clean verify` после успешной компиляции production-кода JDA 6.

## [0.9.1] — 2026-08-04

### Исправлено

- `MusicControls` использует фактический пакет JDA 6 `net.dv8tion.jda.api.components.actionrow.ActionRow` вместо несуществующего `net.dv8tion.jda.api.components.ActionRow`.
- Удалена ссылка на отсутствующий в JDA 6 тип `LayoutComponent`; метод `rows()` теперь возвращает конкретный `List<ActionRow>`.
- `JdaSixSourceMigrationContractTest` проверяет реальную структуру component API JDA 6 и запрещает возврат ошибочных импортов.

### Изменено

- Maven-версия приложения повышена до `0.9.1`.
- JDA остаётся `6.5.0`; DAVE readiness gate, Spring Boot `3.4.3`, Java 17, LavaPlayer `2.2.3`, Docker и deployment не менялись.

### Тестирование

- Тестовый baseline остаётся 36 test classes / 108 `@Test` methods.
- Hotfix устраняет единственный обнаруженный production compile blocker перед первым полноценным JDA 6 migration gate.

## [0.9.0] — 2026-08-04

### Исправлено

- Выполнена полноценная source-миграция JDA `5.6.1 → 6.5.0` после production close code `4017 E2EE/DAVE protocol required` и повторного `VOICE_LEFT` на всей линии JDA 5.
- Музыкальные кнопки переведены с удалённых пакетов JDA 5 `net.dv8tion.jda.api.interactions.components.*` на JDA 6 `net.dv8tion.jda.api.components.*`.
- Сообщение transport failure больше не называет текущую JDA «старой» и направляет диагностику к фактическому DAVE/E2EE close code и runtime-версии.

### Сохранено

- Spring Boot остаётся `3.4.3`, Java — `17`, LavaPlayer — `2.2.3`, Lombok — `1.18.36`, Maven Compiler Plugin — `3.13.0`.
- Playback success по-прежнему подтверждается только после нового вызова `AudioSendHandler#canProvide()`, а не по факту локального старта декодера.
- `/status`, persistent voice diagnostics, observe-only watchdog, stale callback protection и `bridge|host` A/B path сохранены.

### Тестирование

- Добавлен `JdaSixSourceMigrationContractTest` на новые component packages, отсутствие framework drift и сохранение frame-polling readiness gate.
- Dependency compatibility contract теперь фиксирует JDA `6.5.0` как единственную разрешённую production-линию.
- Тестовый baseline повышен до 36 test classes / 108 `@Test` methods.

## [0.8.1] — 2026-08-04

### Исправлено

- `VoiceConnectionStabilityContractTest` больше не зависит от `LF`/`CRLF` и форматирования конструктора: проверка `@Autowired` выполняется по нормализованному Java source.
- `DaveVoiceMigrationContractTest` теперь проверяет сравнение `currentFrameRequests > baselineFrameRequests` в реальном владельце этой политики — `PlaybackReadinessPolicy`, а не ошибочно в `PlayerManager`.
- Source-contract DAVE migration дополнительно подтверждает одинаковый playback-ready flow для slash `/play` и legacy `!search`.

### Изменено

- Maven-версия приложения повышена до `0.8.1`.
- Runtime-код, JDA `5.6.1`, Spring Boot `3.4.3`, LavaPlayer `2.2.3`, Docker и deployment не менялись.

### Тестирование

- Тестовый baseline остаётся 34 test classes / 105 `@Test` methods.
- Контракты стали переносимыми между Windows и Linux и больше не дают ложный красный результат из-за окончания строк или расположения выражения в соседнем классе.


## [0.8.0] — 2026-08-04

### Исправлено

- Корневая причина voice-disconnect подтверждена production-логом: Discord закрывал Audio WebSocket с close code `4017` и причиной `E2EE/DAVE protocol required`, после чего старая JDA пыталась resume и получала `4006 Session is no longer valid`.
- JDA обновлена с `5.3.0` до зафиксированной линии `5.6.1`, предложенной Dependabot в репозитории; Spring Boot `3.4.3`, Java 17 и LavaPlayer `2.2.3` не менялись.
- `/play` и legacy `!search` больше не показывают ложное «Воспроизведение началось» только по факту запуска декодера LavaPlayer.
- Успех воспроизведения подтверждается только после первого нового вызова `AudioSendHandler#canProvide()` — фактического запроса аудиофрейма Discord media transport.
- При выходе из voice до первого фрейма или timeout polling пользователь получает отдельный диагноз, загруженная сессия закрывается и не остаётся «играющей» только локально.

### Добавлено

- `PlaybackReadinessPolicy` и асинхронный readiness gate с отдельным timeout `DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT` (по умолчанию `10s`).
- `/status` и startup-log теперь показывают фактически загруженную версию JDA.
- Отдельные состояния подтверждения: `READY`, `VOICE_LEFT`, `FRAME_TIMEOUT`, `SESSION_CLOSED`, `TRACK_REPLACED`.
- Документ `docs/DAVE-VOICE-MIGRATION.md` с production evidence, границами миграции и smoke-проверкой.

### Изменено

- Maven-версия приложения повышена до `0.8.0`.
- Production network mode возвращается к `bridge`; host-network остаётся только диагностическим override.
- Сообщение о загруженном треке сначала показывает проверку DAVE/media transport, а затем обновляется на подтверждённый успех или точную ошибку.

### Тестирование

- Добавлены unit-тесты readiness policy: первый frame poll, ранний LEAVE, timeout, закрытая сессия и replacement track.
- Добавлен architecture contract на JDA `5.6.1`, отсутствие framework drift и запрет ложного playback success.



## [0.7.7] — 2026-08-04

### Добавлено

- `/status` теперь показывает отдельный voice transport snapshot: Docker network mode, control state, self voice channel, `AudioManager`, frame polling, текущий трек и watchdog mode.
- Состояние последних voice/source ошибок сохраняется после уничтожения музыкальной сессии и остаётся доступным для диагностики.
- Добавлен listener self voice join/move/leave событий и счётчики connection attempts, source failures, `CLEANUP`, fallback, stale callbacks и watchdog warnings.
- Добавлен диагностический Docker override `deploy/docker-compose.host-network.yml` и environment variable `BOT_NETWORK_MODE=bridge|host` для воспроизводимого A/B-теста bridge против host network.
- Добавлено узкое DEBUG-логирование `net.dv8tion.jda.internal.audio` с настраиваемым `DISCORD_BOT_VOICE_LOG_LEVEL`.
- Добавлен документ `docs/VOICE-ROOT-CAUSE-DIAGNOSTICS.md`.

### Исправлено

- Watchdog по умолчанию переведён в observe-only режим и больше не закрывает voice-сессию во время диагностики. Принудительное поведение включается только через `DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=true`.
- Scheduler игнорирует поздние callbacks старого трека после запуска fallback, поэтому stale `onTrackEnd`, `onTrackException` или `onTrackStuck` больше не могут убить текущий replacement track.
- Delivery проверяет фактический network mode контейнера вместе с immutable image, restart count и heartbeat.

### Изменено

- Maven-версия приложения повышена до `0.7.7`.
- Bridge остаётся production default; host network доступен только как явный диагностический режим.
- Voice transport anomaly теперь сначала оставляет доказательства в `/status` и логах, а не автоматически уничтожает сессию.

### Тестирование

- Добавлены unit-тесты persistent voice diagnostics, frame telemetry age/count, stale callback protection и format `/status`.
- Добавлен architecture contract на observe-only watchdog и bridge/host deployment path.
- Тестовый baseline повышен до 33 test classes / 97 `@Test` methods.



## [0.7.6] — 2026-08-04

### Исправлено

- Удалён второй, обходной путь мгновенного отключения: `AudioTrackEndReason.CLEANUP` больше не вызывает немедленный `stopAndRelease`, минуя startup-grace и bounded voice watchdog.
- При `CLEANUP` scheduler сначала пытается запустить резервный результат поиска; при отсутствии fallback продолжает обычную очередь или запускает стандартный idle-disconnect.
- Удалён устаревший callback `onPlaybackCleanup`, который закрывал рабочую voice-сессию раньше, чем watchdog мог подтвердить реальный обрыв транспорта.

### Диагностика

- Зелёный `v0.7.5` подтвердил, что проблема оставалась не в CI и не в reconnect, а в отдельном runtime-path LavaPlayer cleanup.
- Единственным компонентом, который теперь может аварийно закрыть voice transport, остаётся bounded watchdog после startup-grace и disconnect-grace.

### Изменено

- Maven-версия приложения повышена до `0.7.6`.
- Connect timeout, cooldown, frame-demand telemetry, SoundCloud fallback, Docker healthcheck и rollback semantics сохранены.

### Тестирование

- Тесты защищают два сценария `CLEANUP`: переход на fallback без отключения и обычный переход к очереди/idle без аварийного закрытия voice-сессии.
- Тестовый baseline повышен до 31 test classes / 90 `@Test` methods.


## [0.7.5] — 2026-08-04

### Исправлено

- Исправлен ложный voice watchdog из `v0.7.4`: сразу после успешного входа `AudioManager.isConnected()` кратковременно возвращал `false`, из-за чего бот сам закрывал рабочую сессию через 5 секунд.
- Watchdog больше не использует нестабильный boolean JDA как единственный источник истины; он отслеживает реальные вызовы `AudioSendHandler#canProvide()` — то есть запрос Discord на очередной 20-мс аудиофрейм.
- После подтверждённого подключения действует startup-grace, равный voice connect timeout, поэтому transport успевает завершить внутренний handshake до начала аварийного контроля.
- При `404`/playback exception первого SoundCloud search result scheduler автоматически пробует до четырёх следующих результатов того же запроса, не добавляя их в видимую очередь.

### Диагностика

- Production evidence `baskov-v074-voice-exit.log` разделил две независимые причины: ложное принудительное закрытие watchdog и настоящий SoundCloud stream `404`.
- Новые логи различают отсутствие audio frame demand и ошибку конкретного media source.

### Изменено

- Maven-версия приложения повышена до `0.7.5`.
- Ограниченный connect/cooldown, отключённый JDA auto-reconnect, Docker healthcheck и rollback semantics сохранены.

### Тестирование

- Добавлены unit-тесты frame-demand telemetry, startup-grace/watchdog policy и fallback после playback exception.
- Тестовый baseline повышен до 31 test classes / 89 `@Test` methods.


## [0.7.4] — 2026-08-04

### Исправлено

- Исправлен ложный отказ `VoiceConnectionStabilityContractTest`: контракт теперь проверяет реальные вызовы `MusicProperties#getVoiceConnectTimeout()` и `MusicProperties#getVoiceFailureCooldown()`, а не несуществующие имена полей в `VoiceConnectionCoordinator`.
- Исправлен запуск Spring test context: production-конструктор `VoiceConnectionCoordinator(MusicProperties)` явно отмечен `@Autowired`, поэтому Spring однозначно выбирает его при наличии package-private конструктора с `Clock` для unit-тестов.

### Изменено

- Maven-версия приложения повышена до `0.7.4`.
- Voice state machine, timeout, cooldown, watchdog, Docker healthcheck и аварийный rollback не менялись.

### Тестирование

- Сохраняется baseline из 28 test classes / 84 `@Test` methods.
- Контракт дополнительно защищает однозначный Spring constructor selection.


## [0.7.3] — 2026-08-04

### Исправлено

- Исправлена компиляция `VoiceConnectionCoordinatorTest` с JDA 5.3.0: `GuildVoiceState#getChannel()` возвращает `AudioChannelUnion`, поэтому Mockito fixture теперь использует тот же union-тип вместо несовместимого базового `AudioChannel`.
- Maven снова проходит фазу `testCompile`, не меняя runtime-реализацию voice connection state machine.

### Изменено

- Maven-версия приложения повышена до `0.7.3`.
- Runtime-код, voice timeout/cooldown/watchdog, Docker healthcheck и deployment rollback не менялись.

### Тестирование

- Сохраняется baseline из 28 test classes / 84 `@Test` methods; исправлен единственный compile-time mismatch в Mockito fixture.


## [0.7.2] — 2026-08-04

### Исправлено

- Отключён бесконечный автоматический reconnect JDA: voice-подключение теперь выполняется одной ограниченной попыткой на гильдию.
- Трек больше не загружается и не запускается до подтверждённого подключения бота к ожидаемому голосовому каналу.
- `AudioTrackEndReason.CLEANUP` теперь считается потерей Discord audio transport и закрывает повреждённую музыкальную сессию вместо продолжения цикла reconnect.
- Shutdown-path безопасно переносит уже остановленный JDA executor и больше не выбрасывает `RejectedExecutionException` при ручной остановке контейнера.

### Добавлено

- `VoiceConnectionCoordinator` со статусами `CONNECTED`, `TIMEOUT`, `COOLDOWN`, `BUSY`, `FAILED` и `SHUTTING_DOWN`.
- Один общий connection future для параллельных запросов к одному guild/channel и защита от второго подключения к другому каналу.
- Voice watchdog: при ожидаемом воспроизведении разрыв дольше grace-периода завершает сессию один раз.
- Настройки `DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT`, `DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN` и `DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE`.
- Документ `docs/VOICE-CONNECTIONS.md` с incident evidence, state machine и production diagnostics.

### Изменено

- Maven-версия приложения повышена до `0.7.2`.
- Legacy `!search` и slash `/play` используют один voice readiness gate.
- Deployment передаёт и валидирует новые voice timeout/cooldown/grace параметры.

### Тестирование

- Добавлены unit-тесты timeout, shared attempt и already-connected fast path.
- Добавлен architecture contract на отключённый reconnect, порядок `connect -> load`, обработку `CLEANUP`, безопасный shutdown и delivery новых настроек.
- Тестовый baseline повышен до 28 test classes / 84 `@Test` methods.


## [0.7.1] — 2026-08-04

### Исправлено

- Исправлена компиляция `/status`: физические переводы строк больше не попадают внутрь обычных Java string literals.
- Формирование трёх секций статуса вынесено в `StatusMessageFormatter` и использует `String.join("\n", ...)`.

### Изменено

- Maven-версия приложения повышена до `0.7.1`.
- Runtime health, Docker limits, deployment verification и пользовательская семантика `/status` не менялись.

### Тестирование

- Добавлен unit-тест точного многострочного формата Discord, Music и command metrics секций.
- Тестовый baseline повышен до 26 test classes / 75 `@Test` methods.


## [0.7.0] — 2026-08-04

### Добавлено

- Slash-команда `/status` с uptime, Discord gateway, количеством серверов, музыкальных сессий, ожидающих треков и агрегированными счётчиками команд.
- `OperationalMetrics` с отдельными success/failure-счётчиками prefix-команд, slash-команд и component buttons.
- Динамический `RuntimeHealthMonitor`, обновляющий readiness heartbeat каждые 10 секунд только при подключённом JDA gateway.
- Контейнерный `/app/healthcheck.sh`, проверяющий одновременно статус `CONNECTED` и свежесть heartbeat.
- Документ `docs/OPERATIONS.md` с operational-моделью, лимитами и post-deploy проверками.

### Изменено

- Docker healthcheck больше не доверяет бессрочному startup-файлу.
- Production и local Compose получили лимиты `768 MiB`, `1 CPU`, `256 PIDs` и ротацию Docker-логов `3 × 10 MiB`.
- Серверный deploy после состояния `healthy` сверяет immutable image, нулевой `RestartCount` и внутренний heartbeat.
- `/help` теперь показывает `/status` среди сервисных команд.
- Maven-версия приложения повышена до `0.7.0`.

### Надёжность

- Потерявший Discord gateway или переставший обновлять heartbeat процесс автоматически становится `unhealthy`.
- Неудачная post-deploy проверка использует существующий rollback на предыдущий `.env` и образ.
- Runtime status не раскрывает токены, пользовательские запросы, имена участников или содержимое очереди.

### Тестирование

- Добавлены unit-тесты operational counters и runtime heartbeat lifecycle.
- Добавлен architecture contract для `/status`, свежего healthcheck, ресурсных границ, log rotation и post-deploy verification.


## [0.6.0] — 2026-08-04

### Добавлено

- Постоянные настройки громкости и режима повтора отдельно для каждой Discord-гильдии.
- Slash-команды `/settings show`, `/settings volume`, `/settings repeat` и `/settings reset`.
- Атомарное файловое хранилище `guild-settings.properties` без новой внешней зависимости или отдельной СУБД.
- Именованный Docker volume для сохранения настроек между пересозданиями контейнера.
- Полная инструкция релиза с телефона через Termux: storage setup, SHA-256, `git apply --check`, Maven gates, commit, push, tag и rollback.

### Изменено

- Новая музыкальная сессия получает сохранённую громкость и repeat mode сервера.
- Изменения `/settings` немедленно применяются и к уже активной сессии.
- Обычные `/volume` и `/repeat` остаются session-only, поэтому участник голосового канала не может изменить постоянные настройки сервера.
- Изменять persistent settings может только владелец сервера или участник с permission `Manage Server`.
- Maven-версия приложения повышена до `0.6.0`.

### Безопасность и надёжность

- Файл настроек записывается через временный файл и `ATOMIC_MOVE` с безопасным fallback.
- На POSIX-файловых системах файл получает права только для владельца.
- В хранилище не записываются Discord token, имена пользователей и история прослушивания.

### Тестирование

- Добавлены unit-тесты перезапуска и reset файлового repository.
- Добавлены contracts для slash-команд, admin permission, atomic persistence, Docker volume и Termux release gates.


## [0.5.2] — 2026-08-04

### Исправлено

- Исправлен ложный отказ `DependencyHygieneContractTest`: контракт больше не ожидает жёстко зашитую версию приложения `0.5.0` после каждого patch-релиза.
- Версия проекта теперь проверяется как release SemVer формата `X.Y.Z`, поэтому последующие релизы не требуют ручного изменения dependency-контракта.

### Изменено

- Maven-версия приложения повышена до `0.5.2`.
- Runtime-код, Queue Experience, slash-команды, музыкальное ядро, Docker и deployment не менялись.

### Тестирование

- Сохраняется baseline из 56 тестов; исправлен единственный ложный отказ после успешной компиляции production-кода и test sources.

## [0.5.1] — 2026-08-04

### Исправлено

- Исправлена компиляция slash-команд `/remove` и `/move` с JDA 5.3.0: значения integer options теперь преобразуются из `Long` через `Math.toIntExact(...)` вместо недопустимого прямого cast `Long` в `int`.
- GitHub Actions снова проходит фазу Maven `compile` для релиза Queue Experience.

### Изменено

- Maven-версия приложения повышена до `0.5.1`.
- Runtime-семантика очереди, requester, ETA, repeat, shuffle, remove/move/clear и deployment не менялись.

### Тестирование

- `QueueExperienceContractTest` защищает три integer option conversion от возврата прямого `(int) event.getOption(...)`.

## [0.5.0] — 2026-08-04

### Добавлено

- Requester metadata для текущего и каждого ожидающего трека.
- ETA старта добавленного трека на основе остатка текущей песни и длительности очереди.
- Slash-команды `/volume`, `/repeat`, `/shuffle`, `/remove`, `/move` и `/clear`.
- Режимы повтора `off`, `track` и `queue`.
- Кнопка `Повтор`, циклически переключающая режимы музыкальной сессии.
- Настройки `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` и `DISCORD_BOT_MUSIC_MAX_VOLUME`.
- Документ `docs/QUEUE-EXPERIENCE.md` с семантикой очереди и ограничениями.

### Изменено

- Очередь хранит `TrackRequest`, объединяющий LavaPlayer track, requester и время заказа.
- `/queue` показывает requester, длительность, громкость, repeat mode и приблизительное время до конца очереди.
- `/now` показывает requester, громкость и режим повтора.
- `/play` и legacy `!search` передают Discord requester в музыкальное ядро.
- `TrackScheduler` поддерживает атомарные remove/move/shuffle/clear операции без остановки текущего трека.
- Новая guild-сессия получает безопасную громкость по умолчанию.
- Maven-версия приложения повышена до `0.5.0`.

### Совместимость

- Релиз включает возврат на подтверждённую зелёную линию Spring Boot 3.4.3 / JDA 5.3.0 после пользовательского пакетного major-обновления.
- Patch рассчитан непосредственно на присланное состояние репозитория с Boot 4 / JDA 6, поэтому промежуточный `v0.4.4` применять отдельно не требуется.

### Тестирование

- Расширен `TrackSchedulerTest`: requester, ETA, move/remove/clear и repeat track.
- Добавлен `QueueExperienceContractTest`.
- Каталог slash-команд и deployment-контракт обновлены для новых функций и volume variables.

## [0.4.3] — 2026-08-04

### Исправлено

- Устранено рассогласование `logback-classic` 1.5.18 и `logback-core` 1.5.16: логирующий стек снова полностью управляется Spring Boot BOM.
- Исключён тестовый `android-json`, дублировавший `org.json.JSONObject` на classpath и создававший предупреждение Spring Boot.
- Убрано unchecked-предупреждение в `VersionEventTest` через локализованный типобезопасный helper с явным suppression.

### Изменено

- Удалены прямые версии `slf4j-api` и `logback-classic`; совместимые версии выбираются родительским `spring-boot-starter-parent`.
- Удалено неиспользуемое свойство `discord4j-core.version`.
- Тесты используют отдельный `logback-test.xml` без файлового appender и не создают `logs/bot.log` во время Maven verification.
- Добавлен XML-контракт `DependencyHygieneContractTest`, защищающий правила dependency management от регрессии.
- Добавлен Dependabot для ежемесячных Maven- и GitHub Actions-обновлений.
- Maven-версия приложения повышена до `0.4.3`.

### Документация

- Добавлен `docs/DEPENDENCY-HYGIENE.md` с правилами управления logging- и test-зависимостями.

## [0.4.2] — 2026-08-03

### Исправлено

- Исправлен ложный отказ `ModernInteractionsContractTest`: проверка legacy prefix-команд теперь нормализует пробелы и переносы строк перед поиском вызова `CommandInvocation.parse(...)`.
- Архитектурный контракт больше не зависит от того, записана ли цепочка вызова в одну строку или отформатирована переносом перед `.parse(...)`.

### Изменено

- Maven-версия приложения повышена до `0.4.2`.
- Runtime-код, slash-команды, кнопки, autocomplete, музыкальное поведение и deployment-контракт не менялись.

## [0.4.1] — 2026-08-03

### Исправлено

- Исправлена компиляция `ModernCommandCatalogTest` с JDA 5.3.0: проверка options выполняется через конкретный `SlashCommandData`, а не базовый `CommandData`.
- GitHub Actions снова проходит фазу Maven `testCompile` для релиза современных Discord-команд.

### Изменено

- Maven-версия приложения повышена до `0.4.1`.
- Runtime-код, slash-команды, музыкальное поведение и deployment-контракт не менялись.

## [0.4.0] — 2026-08-03

### Добавлено

- Десять глобальных slash-команд: `/help`, `/version`, `/play`, `/pause`, `/resume`, `/skip`, `/stop`, `/queue`, `/now` и `/seek`.
- Autocomplete параметра `/play query` на основе последних поисковых запросов пользователя.
- Интерактивные кнопки `Пауза / играть`, `Пропустить`, `Очередь` и `Стоп` под музыкальными сообщениями.
- Поддержка позиции `SS`, `MM:SS` и `HH:MM:SS` для `/seek`.
- Единый каталог `ModernCommandCatalog`, регистрируемый через JDA при старте приложения.
- Документ `docs/MODERN-COMMANDS.md` с описанием slash-команд, autocomplete, кнопок и compatibility layer.
- Unit- и contract-тесты для каталога slash-команд, position parser, истории поиска и interaction wiring.

### Изменено

- Slash-команды объявлены основным пользовательским интерфейсом бота.
- Старые `!`-команды сохранены как compatibility layer и продолжают работать.
- `PlayerManager` больше не зависит от `TextChannel`: асинхронная загрузка возвращает transport-independent `MusicLoadResult`.
- Prefix- и slash-команды используют единое представление музыкальных результатов через `MusicEmbeds`.
- `!SongName` и `!TrackList` получили те же кнопки управления, что и slash-интерфейс.
- `MusicControlPolicy` теперь может проверять как legacy command context, так и Discord interactions.
- Maven-версия приложения повышена до `0.4.0`.

### Безопасность

- Кнопки, меняющие состояние плеера, проходят ту же проверку голосового канала и административных прав, что и команды.
- Read-only кнопка очереди не создаёт пустую музыкальную сессию.
- Slash-команды вне Discord-сервера отклоняются до обращения к guild- и voice-состоянию.

### Исправлено

- Музыкальный загрузчик больше не привязан к конкретному текстовому каналу и может безопасно отвечать через Discord interaction hook.
- Ошибки slash-команд централизованно перехватываются и возвращают пользователю ephemeral-ответ.
- Повторный поисковый запрос не создаёт дубликаты в autocomplete-истории.

## [0.3.0] — 2026-08-03

### Добавлено

- Единая `MusicControlPolicy` для всех команд, изменяющих состояние воспроизведения.
- Администратор сервера и его владелец могут управлять активной музыкальной сессией из любого голосового канала.
- Обычный участник может управлять музыкой только из того голосового канала, где находится бот.
- Ограничение очереди: по умолчанию не более 100 ожидающих треков на сервер.
- Ограничение длительности трека: по умолчанию не более 4 часов.
- Автоматическое отключение пустой музыкальной сессии через 5 минут.
- Конфигурация музыкальных лимитов через переменные окружения и GitHub Environment variables.
- Unit- и contract-тесты для voice policy, музыкальных ограничений, bounded queue, deployment settings и lifecycle-контрактов.
- Документ `docs/MUSIC-SESSIONS.md` с правилами музыкальной сессии и настройками.

### Изменено

- `PlayerManager` стал обычным Spring bean вместо глобального singleton.
- Все музыкальные команды получают `PlayerManager` через constructor injection.
- `GuildMusicManager` получил явный lifecycle и защиту от повторного уничтожения.
- `TrackScheduler` использует bounded `LinkedBlockingQueue` и возвращает структурированный результат добавления трека.
- `!stop` полностью освобождает guild-сессию, очищает очередь, уничтожает плеер и отключает голосовое соединение.
- `!SongName` и `!TrackList` больше не создают пустую музыкальную сессию только ради чтения состояния.
- Поиск сообщает позицию в очереди и отдельно объясняет отказ из-за лимита, длительности или live-потока.
- Maven-версия приложения повышена до `0.3.0`.

### Безопасность

- Live-потоки отклоняются, чтобы сессия не могла зависнуть навсегда без естественного завершения трека.
- Поздний callback загрузки игнорируется, если пользователь уже остановил и закрыл музыкальную сессию.
- Пустые и завершённые guild-сессии удаляются из памяти вместо накопления до перезапуска приложения.

### Исправлено

- Пользователь из другого голосового канала больше не может остановить, пропустить, поставить на паузу или перемотать чужую сессию.
- Команды чтения очереди больше не оставляют неиспользуемый `AudioPlayer` и sending handler.
- Завершение последнего трека планирует корректное автоотключение вместо бессрочного присутствия бота в канале.
- Остановка Spring-контекста закрывает все голосовые соединения, плееры, idle-задачи и общий LavaPlayer manager.

## [0.2.0] — 2026-08-03

### Добавлено

- Неизменяемый case-insensitive `CommandRegistry` с проверкой имён и обнаружением дубликатов при старте.
- Отдельный `CommandInvocation` для предсказуемого разбора команды, аргументов и исходной строки аргументов.
- Потокобезопасный per-guild/per-user cooldown-механизм.
- Cooldown 3 секунды для `!search` и 30 секунд для owner-команды `!spam`.
- Конфигурируемый префикс через `DISCORD_BOT_PREFIX`.
- Централизованный перехват необработанных исключений команд с безопасным ответом пользователю.
- Unit- и contract-тесты для parser, registry, cooldowns, media URL policy и архитектурных ограничений.

### Изменено

- `BotEvents` стал stateless и больше не хранит общий `MessageReceivedEvent` между вызовами.
- Сообщения ботов, webhook, DM и неподдерживаемых каналов отбрасываются до создания command context.
- Команды ищутся за O(1) вместо линейного перебора списка.
- `EventArgs` стал неизменяемым и предоставляет `getArguments()` и `getRawArguments()`, сохраняя совместимость с `getArgs()`.
- JDA запрашивает только необходимые intents: guild messages, message content и voice states.
- `HelpEvent` получает неизменяемый каталог команд вместо публичного изменяемого поля.
- `PlayerManager` использует thread-safe holder singleton и `ConcurrentHashMap` для менеджеров серверов.
- Уровень логирования приложения по умолчанию снижен с DEBUG до INFO.
- Maven-версия приложения повышена до `0.2.0`.

### Безопасность

- Удалена URL-проверка через `URL.openStream()`, выполнявшая сетевой запрос с VPS по пользовательскому адресу.
- Ссылки разрешены только для HTTP/HTTPS SoundCloud и YouTube; локальные, loopback и произвольные URL отклоняются.
- Локальный файловый источник LavaPlayer отключён.
- Удалено логирование каждого 20-мс аудиофрейма, способное бесконтрольно раздувать логи.

### Исправлено

- Устранена гонка, при которой параллельные Discord-сообщения могли перезаписать контекст выполняемой команды.
- Реальный Spring context test теперь использует JUnit assertion вместо отключённого Java `assert`.
- JDA корректно завершает работу через bean destroy method.

## [0.1.0] — 2026-08-03

### Добавлено

- Первая зафиксированная production-версия проекта.
- Команда `!version`, показывающая версию, Java runtime и время сборки.
- Maven build metadata (`META-INF/build-info.properties`).
- Стабильное имя JAR-файла `baskov-discord-bot.jar`.
- OCI metadata для Docker-образа.
- Документированный процесс выпуска релизов.

### Изменено

- Maven-версия изменена с `0.0.1-SNAPSHOT` на `0.1.0`.
- Название Maven-проекта приведено к `BaskovDiscordBot` без изменения существующих Java package names.
- Delivery summary теперь содержит продуктовую версию приложения.

### Исправлено

- Runtime-образ заранее создаёт `/app/logs` и выдаёт права непривилегированному пользователю `app`, поэтому Logback может запуститься в контейнере.

### Инфраструктура

- Java 17, Spring Boot, JDA и LavaPlayer.
- Maven verification в GitHub Actions.
- Immutable Docker images в GHCR.
- Автоматический production-деплой на VPS с healthcheck и rollback.

[Unreleased]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.4.3...v0.5.0
[0.4.3]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Flawden/BaskovDiscordBot/releases/tag/v0.1.0
