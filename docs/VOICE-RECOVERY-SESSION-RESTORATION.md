# Voice Recovery & Session Restoration

Начиная с `v0.15.0`, активная музыкальная сессия не считается одноразовой памятью процесса. Бот сохраняет bounded checkpoint и может восстановить playback после неожиданного voice-разрыва, restart контейнера или обычного redeploy.

## Что сохраняется

Для каждой Discord-гильдии хранится не более одного checkpoint:

- ID голосового канала;
- текущий YouTube/SoundCloud трек;
- позиция текущего трека;
- очередь в исходном порядке;
- pause state;
- volume;
- repeat mode;
- bounded previous-history (до 25 треков, начиная с `v1.10.0`);
- время снимка.

Файл по умолчанию:

```text
data/music-sessions.tsv
```

В Docker:

```text
/app/data/music-sessions.tsv
```

Он использует существующий persistent volume `bot-data` и заголовок:

```text
BASKOV_MUSIC_SESSIONS_V2
```

`v1.10.0` читает также legacy `BASKOV_MUSIC_SESSIONS_V1`: такие записи получают пустую previous-history и автоматически переходят в V2 при следующем save.

Checkpoint обновляется каждые пять секунд и повторно перед graceful shutdown. Запись выполняется через временный файл и atomic move; на POSIX применяется `0600`.

## Что не сохраняется

В checkpoint не сериализуются:

- `AudioTrack` и decoder state;
- Discord token;
- cookies, OAuth и poToken;
- DAVE keys;
- голосовые пакеты;
- список голосовавших пользователей.

Хранятся только ограниченные метаданные и публичный URL, который modern source может загрузить повторно.

## Startup restoration

После `JDA.awaitReady()` бот читает checkpoints и проверяет:

1. checkpoint не старше `DISCORD_BOT_MUSIC_SESSION_MAX_AGE`;
2. бот всё ещё состоит на сервере;
3. voice channel существует;
4. в канале остаётся хотя бы один человек, когда включён `REQUIRE_HUMAN_LISTENER`.

При успешной проверке бот:

1. подключается к сохранённому каналу;
2. загружает текущий трек по публичному identifier;
3. восстанавливает позицию с защитным отступом от самого конца;
4. загружает очередь последовательно;
5. отдельно резолвит bounded previous-history и восстанавливает её без вызова persistent listening-history listener;
6. возвращает volume, repeat и pause state.

Если канал пуст, checkpoint не удаляется. Первый человек, вернувшийся в этот канал в пределах TTL, запускает восстановление автоматически.

Удалённый ролик не ломает всю сессию: такой элемент пропускается, остальные записи продолжают загружаться. Если не удалось загрузить ни одного трека, runtime-сессия освобождается, а checkpoint сохраняется до истечения TTL для диагностики или следующего старта.

## Runtime voice recovery

JDA auto-reconnect остаётся выключенным. Вместо бесконечного reconnect применяется bounded coordinator:

```text
unexpected self LEAVE / frame polling timeout
→ pause текущего AudioPlayer
→ reconnect attempt 1
→ backoff 2s
→ reconnect attempt 2
→ backoff 4s
→ reconnect attempt 3
```

После успеха бот продолжает тот же in-memory `AudioTrack` с сохранённой позиции и возвращает прежний pause state.

После исчерпания попыток бот:

- сохраняет актуальный checkpoint;
- закрывает повреждённый voice transport;
- освобождает LavaPlayer runtime-сессию;
- не удаляет checkpoint.

Это предотвращает бесконечные циклы JOIN/LEAVE и одновременно не теряет очередь.

## `/status`

Новая секция `Voice recovery` показывает:

```text
Checkpoint-сессий
Восстановлений сейчас
Transport A/S/F
Startup restored/failed
Previous restored/failed
Последнее событие
```

Пользовательские названия и URL в эту секцию не выводятся.

## Конфигурация

| Переменная | Значение по умолчанию |
|---|---:|
| `DISCORD_BOT_MUSIC_SESSION_FILE` | `data/music-sessions.tsv` |
| `DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL` | `5s` |
| `DISCORD_BOT_MUSIC_SESSION_MAX_AGE` | `6h` |
| `DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP` | `true` |
| `DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER` | `true` |
| `DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED` | `true` |
| `DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS` | `3` |
| `DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF` | `2s` |

Защитные границы:

- checkpoint interval: больше нуля и не больше одной минуты;
- max age: больше нуля и не больше семи дней;
- recovery attempts: от 1 до 10;
- recovery backoff: от 0 до одной минуты;
- очередь checkpoint: не более 1000 треков, дополнительно ограничивается runtime `maxQueueSize`.


## Ручная диагностика и retry (`v1.10.0`)

`/session status` показывает состояние конкретной guild без mutation: `NONE`, `SAVED`, `RESTORING` или `ACTIVE`, сохранённый voice channel, возраст checkpoint, число current+queue и previous-history треков, resume position, pause/volume/repeat и последнее recovery-событие.

`/session recover` доступен только владельцу сервера, участникам с `Manage Server` или configured manager-role. Перед запуском он проверяет, что нет активной/уже восстанавливающейся сессии, checkpoint не истёк, сохранённый voice channel существует и при `REQUIRE_HUMAN_LISTENER=true` в нём есть человек. Сам restore остаётся тем же bounded startup recovery path.

### Downgrade

После первой записи `BASKOV_MUSIC_SESSIONS_V2` бинарники до `v1.10.0` не смогут прочитать header. Перед rollback ниже `v1.10.0` сохраните backup `music-sessions.tsv`.
