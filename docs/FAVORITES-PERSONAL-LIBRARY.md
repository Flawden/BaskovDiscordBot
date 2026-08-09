# Favorites & Personal Library

`v1.7.0` добавляет личное избранное поверх уже существующего persistent `music-library.tsv`.

## Команды

```text
/favorites list [page]
/favorites add
/favorites play position:<n>
/favorites play-all
/favorites remove position:<n>
/favorites search query:<text>
/favorites clear
```

`/favorites add` сохраняет текущий повторно загружаемый YouTube/SoundCloud-трек. Избранное изолировано по `guildId + Discord user ID`: участники одного сервера не видят и не изменяют личные списки друг друга.

Новые записи идут первыми. Один и тот же `provider + playbackIdentifier` не сохраняется повторно; команда возвращает `Уже в избранном`, не создавая дубликат.

## Лимиты и воспроизведение

На пользователя хранится до 100 избранных треков на одном сервере.

`play` и `play-all` проходят существующий request/voice policy и используют тот же ordered batch loader, что `/replay` и `/playlist play`. Поэтому favorites не обходят DJ/request access, voice-channel restriction, queue bounds или playback readiness.

`/favorites clear` является destructive action и использует одноразовое двухминутное confirmation UI. Confirmation привязан к guild + user и не затрагивает серверные плейлисты или общую playback history.

## Поиск и autocomplete

`/favorites search` выполняет локальный case-insensitive поиск по title и author и возвращает исходные 1-based позиции из `/favorites list`.

Autocomplete `/play` и `/search` теперь объединяет источники в таком порядке:

1. недавние in-memory запросы пользователя;
2. личное persistent избранное;
3. persistent playback history сервера;
4. треки persistent playlists сервера.

Autocomplete не выполняет сетевых запросов.

## Persistence

Новый отдельный storage-файл не создаётся. Избранное хранится в существующем `BASKOV_MUSIC_LIBRARY_V1` как запись типа `F`:

```text
F <guildId> <userId> <position> <StoredTrack...>
```

Старые файлы без `F` полностью совместимы. Atomic temp + replace, owner-only permissions и существующие backups `music-library.tsv` автоматически покрывают favorites. Важная downgrade-оговорка: бинарник до `v1.7.0` не знает record `F`; при откате он проигнорирует favorites при чтении и может удалить их при следующей записи `music-library.tsv`. Поэтому rollback после фактического использования favorites должен начинаться с backup этого файла.

Плейлистовые и history mutations сохраняют favorite map неизменным; отдельные user favorites также не затрагивают другие guild/user списки.
