# Observability & Self-Diagnostics 1.12

`v1.12.0` добавляет read-only `/doctor`, который агрегирует уже существующие безопасные runtime-сигналы и отвечает не только **что** видно, но и **что делать дальше**.

## Команды

```text
/doctor summary
/doctor gateway
/doctor voice
/doctor storage
/doctor session
/doctor source
/doctor failures
```

Все ответы ephemeral и доступны обычному участнику сервера, потому что не содержат токены, абсолютные storage paths, user IDs или stack traces.

## Severity

Каждая проверка имеет один из уровней:

- `OK` — подсистема выглядит здоровой либо штатно простаивает;
- `WARN` — есть свежий сигнал проблемы, recovery в процессе или health signal устарел;
- `FAIL` — текущее состояние прямо противоречит работоспособности подсистемы, например disconnected gateway, failed storage probe, DAVE failure или ожидаемый playback без Discord AudioManager connection.

`summary` показывает восемь независимых checks:

1. `gateway` — JDA status и свежесть 10-секундного runtime heartbeat;
2. `dave` — native libDAVE readiness и protocol version;
3. `voice` — active session, AudioManager connection и audio-frame demand;
4. `source` — `youtube-source 1.18.2` и свежие runtime source failures;
5. `storage` — live probe трёх persistent stores;
6. `backups` — состояние periodic persistence backup;
7. `session` — checkpoint/recovery counters и последнее recovery event;
8. `commands` — command failure rate и свежая внутренняя ошибка.

## Почему Doctor не делает сетевые probes

`/doctor` намеренно не выполняет внешний HTTP-запрос к Discord, YouTube, Maven Central или `maven.lavalink.dev`. Диагностическая команда должна отвечать даже тогда, когда внешний upstream завис или недоступен.

Source-health поэтому является runtime diagnosis: движок известен из pinned configuration, а деградация определяется по реальным track exceptions/fallback events, которые уже увидел процесс.

## Recent failure journal

`OperationalMetrics` сохраняет до `25` последних внутренних command failures в памяти процесса, newest-first.

Для каждого события сохраняются только:

- timestamp;
- канал `PREFIX` / `SLASH` / `BUTTON`;
- безопасное имя операции;
- тип исключения;
- санитизированное короткое сообщение.

Не сохраняются:

- Discord user ID;
- guild ID;
- stack trace;
- confirmation token/component ID;
- secrets и environment values.

`/doctor failures` показывает максимум 10 последних записей. Journal намеренно не persistent и очищается при restart.

## Разделение `/status`, `/session` и `/doctor`

- `/status` — широкий raw operational snapshot;
- `/session status` — глубокое состояние конкретного playback checkpoint/recovery;
- `/doctor` — приоритизированная интерпретация runtime-сигналов + следующий шаг.

Ни одна doctor-команда не мутирует music/session/settings state.
