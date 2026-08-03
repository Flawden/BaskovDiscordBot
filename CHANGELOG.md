# Changelog

Все заметные изменения Baskov Discord Bot фиксируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
а версии следуют [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

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

[Unreleased]: https://github.com/Flawden/BaskovDiscordBot/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Flawden/BaskovDiscordBot/releases/tag/v0.1.0
