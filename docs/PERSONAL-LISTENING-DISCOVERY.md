# Personal Listening History & Discovery

`v1.8.0` добавляет персональный слой поверх существующих favorites и guild history без новой БД и внешнего recommendation service.

## Что считается personal history

Бот не ведёт скрытую телеметрию присутствия пользователей в voice. Поэтому «личная история» означает **треки, заказанные этим Discord user и реально дошедшие до существующей playback history**. Запись появляется при том же событии, что и guild history (завершение/переход через существующий history path), и не доказывает полное прослушивание трека пользователем.

Лимиты:

- guild history — 50 записей;
- personal history — 200 записей на `guildId + userId`;
- favorites — 100 на `guildId + userId`.

## Команды

```text
/history page:<n> scope:server|mine
/replay position:<n> scope:server|mine
/discover history position:<n> scope:server|mine
/discover profile
/discover for-me
```

`scope` optional; без него `/history`, `/replay` и `/discover history` полностью сохраняют прежнее server-history поведение.

`/discover profile` показывает retained statistics: top tracks, artists, unique tracks и favorites count.

`/discover for-me` не является ML/recommendation engine. Он детерминированно выбирает seed из explicit favorites и повторов personal history, затем передаёт `author + title` в тот же interactive YouTube search pipeline. Все обычные request/DJ/voice/queue ограничения сохраняются.

## Autocomplete

Локальные suggestions идут в порядке:

1. recent in-memory queries;
2. personal favorites;
3. personal persistent history;
4. guild persistent history;
5. playlists.

Сетевых запросов autocomplete не делает.

## Persistence

Новый файл не создаётся. `music-library.tsv` / `BASKOV_MUSIC_LIBRARY_V1` получает строки:

```text
U <guildId> <userId> <position> <StoredTrack...>
```

При загрузке старого файла без `U` для пользователя выполняется best-effort backfill из ещё сохранённых 50 guild-history entries по `requesterUserId`. После следующей mutation state сериализуется обычным atomic temp→replace вместе с playlists/history/favorites.

### Downgrade

Binary ниже `v1.8.0` не знает `U`. После фактического накопления personal history перед rollback на `v1.7.x` или ниже обязательно сделайте backup `music-library.tsv`: старый код может проигнорировать `U` при load и удалить эти строки при следующей library mutation.
