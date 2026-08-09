# Modern Discord Commands

Начиная с `v0.4.0`, основной пользовательский интерфейс Baskov Discord Bot — глобальные slash-команды Discord.
Старые prefix-команды сохраняются как compatibility layer и продолжают работать.

## Slash-команды

| Команда | Назначение |
|---|---|
| `/play query` | Сразу добавляет первый результат YouTube или принимает прямую ссылку SoundCloud/YouTube |
| `/search query` | Показывает до пяти результатов YouTube и кнопки выбора |
| `/discover recent|again|related|history|profile|for-me` | Продолжает поиск и добавляет локальный personal discovery |
| `/history [page] [scope:server|mine]` | Серверная история до 50 или личная requester-history до 200 записей |
| `/replay position` | Повторно загружает трек по номеру из постоянной истории |
| `/favorites ...` | Личное persistent избранное: list/add/play/play-all/remove/search/clear |
| `/playlist ...` | Управляет постоянной библиотекой: create/show/search/add/add-history/capture-queue/play/remove/move/rename/copy/dedupe/delete |
| `/pause` | Ставит текущий трек на паузу |
| `/resume` | Продолжает воспроизведение |
| `/previous` | Возвращает предыдущий трек текущей сессии |
| `/skip` | Пропускает трек напрямую либо голосует в режиме `vote` |
| `/voteskip` | Явно голосует за пропуск; DJ и администратор пропускают напрямую |
| `/stop` | Останавливает музыку, очищает очередь и отключает бота |
| `/queue [page]` | Показывает текущий трек и выбранную страницу очереди; страницы переключаются кнопками |
| `/now` | Показывает текущий трек и расширенный пульт управления |
| `/seek position` | Перематывает на `SS`, `MM:SS` или `HH:MM:SS` |
| `/volume level` | Изменяет громкость текущей guild-сессии |
| `/repeat mode` | Выбирает `off`, `track` или `queue` |
| `/shuffle` | Перемешивает ожидающие треки |
| `/remove position` | Удаляет трек по номеру |
| `/move from to` | Перемещает трек в очереди |
| `/clear` | Очищает ожидание, не останавливая текущий трек |
| `/queue-manage ...` | Сводка ревизии, batch remove-range, dedupe и удаление собственных ожидающих треков |
| `/settings ...` | Guild administration: playback/request access, DJ/manager roles, voice restriction, profiles, audit и reset |
| `/help` | Показывает краткую помощь |
| `/version` | Показывает версию production-сборки |
| `/status` | Показывает uptime, Discord gateway, музыкальные сессии и агрегированные счётчики |

Команды регистрируются глобально при успешном запуске JDA. Discord может обновлять глобальный список команд не мгновенно.

## Autocomplete

Параметр `query` команд `/play` и `/search` объединяет локальную in-memory историю последних запросов пользователя с личным persistent избранным, persistent playback history и треками плейлистов текущего сервера. Autocomplete не делает сетевых запросов. Параметр `name` операций `/playlist show|add|play|remove|move|rename|copy|dedupe|capture-queue|add-history|delete` предлагает имена постоянных плейлистов текущего сервера.
История:

- не сохраняется между перезапусками контейнера;
- изолирована по Discord user ID;
- хранит не более 20 уникальных запросов на пользователя;
- возвращает не более 25 вариантов, как требует Discord API.

## Кнопки управления

Под обычными музыкальными сообщениями остаётся компактный пульт: пауза/играть, пропустить, очередь, repeat и stop.

Под `/now` выводится расширенный двухрядный пульт:

- предыдущий трек;
- −15 и +15 секунд;
- пауза/продолжить;
- следующий трек;
- очередь, shuffle, repeat и stop.

Кнопки, меняющие состояние плеера, используют ту же `MusicControlPolicy`, что и текстовые команды. В режиме `vote` кнопка `Следующий` создаёт голос обычного слушателя, а для DJ остаётся прямым skip.

Результаты `/search` показываются ephemeral-сообщением. Кнопки 1–5 привязаны к автору и серверу, живут пять минут и после выбора становятся недействительными. Выбранный уже загруженный `AudioTrack` ставится в очередь без второго запроса к YouTube.
Кнопка очереди является read-only и доступна всем участникам сервера. `/queue-manage stats` также read-only; mutating операции Queue Manager поддерживают optional revision guard от устаревших позиций.

## Совместимость

Legacy prefix-команды не удалены. Это позволяет перейти на slash-интерфейс без резкой поломки привычных сценариев.
Удаление или переименование старых команд должно происходить только в отдельном major-релизе либо после периода deprecation.

Подробная семантика очереди, requester, ETA, repeat mode и volume описана в [`QUEUE-EXPERIENCE.md`](QUEUE-EXPERIENCE.md). История и расширенный пульт описаны в [`ADVANCED-PLAYBACK-CONTROLS.md`](ADVANCED-PLAYBACK-CONTROLS.md).


Подробности интерактивного поиска описаны в [`SEARCH-TRACK-SELECTION.md`](SEARCH-TRACK-SELECTION.md).
Постоянные плейлисты, история и replay описаны в [`PLAYLISTS-HISTORY-REPLAY.md`](PLAYLISTS-HISTORY-REPLAY.md), а личное избранное — в [`FAVORITES-PERSONAL-LIBRARY.md`](FAVORITES-PERSONAL-LIBRARY.md).

DJ-роли и голосование описаны в [`DJ-ROLES-AND-VOTING.md`](DJ-ROLES-AND-VOTING.md), а административная матрица — в [`GUILD-ADMINISTRATION.md`](GUILD-ADMINISTRATION.md).


## Discovery

`/discover recent` показывает локальную историю поиска пользователя, `/discover again` повторяет последний запрос, а `related` и `history` строят новый текстовый запрос из уже известных `author + title`. `profile` агрегирует retained personal history, а `for-me` выбирает seed из favorites + personal history. Все поисковые режимы используют тот же интерактивный `/search` pipeline и не обходят существующие ограничения загрузки и voice-policy.

## Discord Experience 1.6

`v1.6.0` добавляет интерактивный слой поверх существующих slash-команд без изменения music/session persistence.

`/help [section]` поддерживает разделы `overview`, `playback`, `queue`, `library`, `admin`. Ephemeral help-сообщение содержит кнопки разделов; переключение редактирует то же сообщение и не создаёт новый command invocation.

`/status` содержит кнопку `↻ Обновить статус`. Она заново строит live snapshot, включая storage probe, backup health, gateway, voice recovery и command metrics. Кнопка read-only и не меняет music state.

Опасные действия `/stop`, непустой `/clear`, `/playlist delete` и `/settings reset` создают одноразовую confirmation session на две минуты. Token привязан к guild и пользователю, а перед выполнением права проверяются повторно. Stop-кнопка под `/now` использует ту же модель.

После `Подтвердить` token потребляется до mutation; повторный клик не может выполнить действие второй раз. `Отмена` также потребляет token. Confirmation sessions in-memory и намеренно не переживают restart процесса.


## Favorites & Personal Library 1.7

`/favorites` хранит до 100 личных треков на пользователя и сервер в существующем `music-library.tsv`. `add` сохраняет текущий replayable track, `play`/`play-all` используют обычную voice/request policy, `search` возвращает исходные позиции, а `clear` защищён одноразовым confirmation UI. Favorites также участвуют в локальном autocomplete `/play` и `/search` перед общей history/playlists.
