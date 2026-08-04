# Changelog

Все заметные изменения Baskov Discord Bot фиксируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
а версии следуют [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]


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
