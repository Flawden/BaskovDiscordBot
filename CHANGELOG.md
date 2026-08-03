# Changelog

Все заметные изменения Baskov Discord Bot фиксируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
а версии следуют [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

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

[Unreleased]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Flawden/BaskovDiscordBot/releases/tag/v0.1.0
