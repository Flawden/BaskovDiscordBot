# Native libDAVE integration

## Зачем это нужно

Discord voice требует DAVE/E2EE. JDA 6 содержит только контракт `DaveSessionFactory` и по умолчанию использует `PassthroughDaveSessionFactory`, которая объявляет максимальную версию протокола `0`. Discord отклоняет такую voice-сессию close code `4017`.

Baskov Discord Bot использует `libdave-jvm 0.1.3`:

- `moe.kyokobot.libdave:adapter-jda` — адаптер к JDA `DaveSessionFactory`;
- `moe.kyokobot.libdave:impl-jni` — JNI-реализация поверх официальной C++ `libdave`;
- `natives-linux-x86-64` — production runtime для Ubuntu/Docker;
- `natives-win-x86-64` — runtime для локальной Windows-проверки.

JDAVE не используется, потому что его текущая линия требует Java 25, а production-baseline проекта остаётся Java 17.

## Startup flow

При старте бота:

1. `NativeDaveFactory.ensureAvailable()` загружает platform-native библиотеку.
2. `maxSupportedProtocolVersion()` обязан вернуть значение больше `0`.
3. `LDJDADaveSessionFactory` передаётся в `AudioModuleConfig.withDaveSessionFactory(...)`.
4. Конфигурация передаётся в `JDABuilder#setAudioModuleConfig(...)` до `build()`.
5. Только после этого запускаются JDA gateway и voice module.

Если native-библиотека отсутствует, не подходит платформе или возвращает protocol version `0`, приложение завершает startup. Docker healthcheck не пропускает такой образ в production, а deployment выполняет rollback.

## Maven platform profiles

Профили активируются автоматически:

- `libdave-linux-x86-64` — Linux `amd64`;
- `libdave-windows-x86-64` — Windows `amd64`.

Termux может выполнять обычные unit/source-contract тесты, но Android native runtime в текущем релизе не поставляется. Production image всегда собирается на Linux x86-64 в GitHub Actions.

## Runtime diagnostics

`/status` показывает:

- статус DAVE runtime;
- реализацию и версию;
- максимальную поддерживаемую protocol version;
- платформу native runtime;
- безопасную причину startup failure, если она возникла.

Секреты, ключевые пакеты MLS, криптографические состояния и содержимое аудиокадров не выводятся.

## Проверка

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

На Linux/Windows x86-64 тест `NativeDaveRuntimeTest` реально загружает JNI-библиотеку и требует положительную DAVE protocol version.

После deployment:

```bash
docker logs --since 15m baskov-discord-bot \
  | grep -Ei 'Native libDAVE|4017|4006|DAVE|E2EE|Playback confirmed'
```

Нормальный startup содержит `Native libDAVE ready` и не содержит passthrough-warning или close code `4017`.
