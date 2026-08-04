# Changelog

Все заметные изменения Baskov Discord Bot фиксируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
а версии следуют [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]


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
