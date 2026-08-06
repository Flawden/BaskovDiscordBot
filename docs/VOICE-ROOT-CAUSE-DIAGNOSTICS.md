# Voice Transport Root Cause Diagnostics

`v0.7.7` разделяет музыкальную проблему на независимые уровни вместо того,
чтобы считать основной Discord gateway доказательством готовности аудио.

## Наблюдаемые уровни

Команда `/status` теперь показывает отдельно:

- режим Docker-сети (`bridge` или `host`);
- control state voice-подключения;
- voice channel самого бота;
- `AudioManager.isConnected()`;
- число вызовов `AudioSendHandler#canProvide()` и возраст последнего вызова;
- текущий трек;
- последние self join/move/leave события;
- последнюю voice-ошибку;
- последнюю ошибку источника;
- счётчики `TrackException`, `CLEANUP`, fallback и stale callbacks;
- режим watchdog (`OBSERVE` или `ENFORCE`).

Состояние диагностики не удаляется вместе с музыкальной сессией. Поэтому
`/status` остаётся полезным сразу после того, как бот уже вышел из канала.

## Watchdog observe-only

По умолчанию:

```text
DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=false
```

Watchdog записывает отсутствие frame polling, но не вызывает
`stopAndRelease()`. Это исключает ситуацию, когда диагностический механизм сам
создаёт исследуемое отключение.

Начиная с `v0.15.0`, значение `false` означает отсутствие destructive legacy enforcement,
но не отключает recovery. При стандартном:

```text
DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED=true
```

подтверждённый transport failure сначала передаётся в bounded
`recoverVoiceSession(...)`. Только когда session recovery явно отключён, параметр
`DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=true` разрешает старый аварийный путь
`stopAndRelease()`. Поэтому диагностика остаётся observe-only относительно
немедленного уничтожения очереди, а восстановление выполняется отдельным
ограниченным coordinator-ом.

После подтверждения корректного порога режим можно включить явно:

```text
DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE=true
```

## Защита от stale callbacks

LavaPlayer может доставить callback старого трека после запуска fallback.
Scheduler теперь проверяет identity текущего `AudioTrack` и игнорирует поздние:

- `onTrackEnd`;
- `onTrackException`;
- `onTrackStuck`.

Старый callback больше не может остановить новый fallback или очистить его
состояние.

## A/B-тест Docker-сети

Обычный deployment использует bridge-сеть:

```text
BOT_NETWORK_MODE=bridge
```

Для разового диагностического запуска открой workflow `Build, Publish and Deploy`,
выбери target `production` и input `network_mode=host`. Постоянную Environment
Variable менять не требуется. Для обычного возврата запусти тот же workflow с
`network_mode=bridge` либо `environment`.

Delivery применит `deploy/docker-compose.host-network.yml`, а post-deploy
verification подтвердит фактический `HostConfig.NetworkMode`.

Интерпретация:

| Bridge | Host | Вывод |
|---|---|---|
| не работает | работает | проблема Docker bridge/NAT/conntrack |
| не работает | не работает | проблема выше Docker: JDA, Discord voice или upstream UDP |
| работает | работает | проблема была нестабильной или устранена другим изменением |

После теста переменную нужно вернуть в `bridge` и повторить deployment.

## Narrow JDA voice logging

В диагностическом релизе включён:

```text
DISCORD_BOT_VOICE_LOG_LEVEL=DEBUG
```

Он применяется только к `net.dv8tion.jda.internal.audio`. Общий root logger
остаётся на `INFO`, а Docker ограничивает журналы `3 × 10 MiB`.

## Безопасность

`/status` не выводит:

- Discord token;
- пользовательские поисковые запросы;
- содержимое `.env`;
- IP voice endpoint;
- приватные ключи.

Текущий track title и channel ID видит только вызвавший `/status` пользователь,
поскольку ответ остаётся ephemeral.
