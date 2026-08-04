# Voice Connection Stability

Начиная с `v0.7.2`, голосовое подключение Baskov Discord Bot выполняется как ограниченная state machine, а не как бессрочный JDA reconnect.

## Инцидент `v0.7.1`

В production-логе зафиксированы две разные неисправности:

1. один результат SoundCloud вернул HTTP `404` при загрузке потока;
2. следующий трек успешно стартовал, но примерно через минуту завершился с `AudioTrackEndReason.CLEANUP` — Discord перестал запрашивать аудиофреймы.

При ручной остановке контейнера JDA уже завершила внутренний executor, после чего старый shutdown-path попытался закрыть voice connection и получил `RejectedExecutionException`.

Лог не доказывает конкретную внешнюю причину потери Discord voice transport. Поэтому релиз исправляет контролируемое поведение приложения: ограничивает попытку, отключает бесконечный reconnect, не запускает трек до готовности канала и закрывает сорванную сессию один раз.

## State machine

Для каждой гильдии одновременно разрешена только одна попытка подключения:

```text
IDLE -> CONNECTING -> CONNECTED
                   -> TIMEOUT -> COOLDOWN
                   -> FAILED  -> COOLDOWN
```

Правила:

- `AudioManager.setAutoReconnect(false)` устанавливается до открытия канала;
- повторный запрос к тому же каналу разделяет уже существующий `CompletableFuture`;
- запрос к другому каналу во время подключения получает `BUSY`;
- трек загружается только после подтверждения `AudioManager.isConnected()` и voice state самого бота в ожидаемом канале;
- при таймауте соединение закрывается, sending handler снимается, сессия уничтожается;
- во время cooldown новая попытка не открывает канал;
- `CLEANUP` LavaPlayer считается потерей audio transport и закрывает сессию;
- watchdog закрывает сессию, если при ожидаемом воспроизведении voice connection отсутствует дольше grace-периода.

## Настройки

| Переменная | По умолчанию | Назначение |
|---|---:|---|
| `DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT` | `15s` | максимальное время одной попытки подключения |
| `DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN` | `30s` | пауза после timeout/transport failure |
| `DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE` | `5s` | сколько ждать краткого разрыва во время воспроизведения |

Допустимые пределы проверяются при Spring binding и серверном deployment:

- connect timeout: больше нуля и не более `2m`;
- failure cooldown: от `0` до `10m`;
- disconnect grace: больше нуля и не более `1m`.

## Диагностика production

```bash
docker logs --since 15m --timestamps baskov-discord-bot \
  | grep -Ei 'Voice connection|Voice transport|CLEANUP|autoReconnect|Track ended'
```

Нормальный запуск содержит последовательность:

```text
Voice connection requested ... autoReconnect=false
Voice connection state ... state=CONNECTING
Voice connection state ... state=CONNECTED
Voice connection ready ...
```

При проблеме допустима одна ограниченная последовательность `CONNECTING -> TIMEOUT/FAILED -> close`. Повторяющийся бесконечный join/leave больше не является допустимым поведением.

## Emergency deployment

Если production-контейнер был вручную остановлен из-за voice-loop, deployment запоминает это состояние. При неудаче нового образа rollback восстанавливает предыдущий `.env`, но не запускает известный проблемный контейнер снова.
