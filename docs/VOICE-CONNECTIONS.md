# Voice Connection Stability

Начиная с `v0.7.2`, голосовое подключение Baskov Discord Bot выполняется как ограниченная state machine, а не как бессрочный JDA reconnect.

## Production incidents `v0.7.1` и `v0.7.4`

Первый production-лог помог отключить бесконечный JDA auto-reconnect. Второй лог `baskov-v074-voice-exit.log` показал уже точную причину немедленного выхода:

1. coordinator подтвердил `CONNECTED`;
2. трек стартовал;
3. через 90 мс `AudioManager.isConnected()` кратковременно стал `false`;
4. старый watchdog начал grace и через 5 секунд сам закрыл рабочую сессию.

Это был ложный transport failure внутри приложения, а не команда Discord на выход. Поэтому начиная с `v0.7.5` watchdog использует реальный сигнал audio frame demand: JDA вызывает `AudioSendHandler#canProvide()` каждые 20 мс, когда transport действительно запрашивает звук.

В том же логе отдельный SoundCloud result вернул HTTP `404`. Для поискового запроса scheduler теперь пробует до четырёх следующих результатов, не смешивая source failure с voice failure.

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
- после подключения watchdog не вооружается до окончания startup-grace; затем закрывает сессию только при отсутствии реального запроса аудиофреймов дольше grace-периода.

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
