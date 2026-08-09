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
| `/queue-manage ...` | `stats`, `mine`, `community`, ownership-safe `remove-own`, batch remove-range/dedupe и `remove-mine` |
| `/session status|recover` | Показывает playback checkpoint и позволяет manager/admin повторить pending recovery |
| `/moderation status|remove|purge|audit` | Least-privilege queue moderation с revision guards и audit |
| `/settings ...` | Guild administration: playback/request access, DJ/manager/moderator roles, requester limit, profiles, audit и reset |
| `/help` | Показывает краткую помощь |
| `/version` | Показывает версию production-сборки |
| `/status` | Показывает uptime, Discord gateway, музыкальные сессии и агрегированные счётчики |
| `/doctor summary|gateway|voice|storage|session|source|failures` | Actionable self-diagnostics по существующим runtime-сигналам без внешних network probes |

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
Кнопка очереди является read-only и доступна всем участникам сервера. В `v1.9.0` очередь также даёт кнопки `👤 Мои треки`, `👥 Заказчики` и `🗳️ Vote skip`: первые две читают requester-aware projection текущей waiting queue, третья показывает состояние голосования без добавления голоса. `/queue-manage stats|mine|community` read-only; mutating операции Queue Manager поддерживают optional revision guard от устаревших позиций.

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


## Queue Collaboration & Social UX 1.9

`/queue-manage mine` показывает собственные ожидающие треки с глобальными позициями, а `remove-own` удаляет только позицию, requester user ID которой совпадает с автором команды. Чужой трек этим путём удалить нельзя. `/queue-manage community` агрегирует только live waiting queue и не создаёт persistent social profile. Подробности — в [`QUEUE-COLLABORATION.md`](QUEUE-COLLABORATION.md).


## Playback Sessions & Recovery 2.0

`/session status` — read-only guild-scoped диагностика текущего checkpoint/recovery. `/session recover` не создаёт новую очередь поверх активной сессии и доступен только через существующую `GuildAdministrationPolicy`. V2 checkpoint сохраняет bounded previous-history, поэтому кнопка/команда `/previous` может пережить restart/redeploy. Подробности — в [`VOICE-RECOVERY-SESSION-RESTORATION.md`](VOICE-RECOVERY-SESSION-RESTORATION.md).


## Administration & Moderation 2.0

`v1.11.0` добавляет `moderator-role` без полного доступа к guild settings и `/moderation status|remove|purge|audit`. `remove` и `purge` используют optional queue revision. `/settings requester-limit` вводит cap только для pending requests одного пользователя; enforcement находится в `TrackScheduler`, поэтому одинаков для play/search/favorites/history/playlist paths. Settings export теперь V2, а import продолжает принимать V1. Подробности — в [`ADMINISTRATION-MODERATION-2.md`](ADMINISTRATION-MODERATION-2.md).


## Observability & Self-Diagnostics 1.12

`/doctor` не заменяет `/status`: status показывает широкий raw snapshot, doctor превращает те же безопасные runtime-сигналы в severity (`OK/WARN/FAIL`), краткий диагноз и рекомендуемое следующее действие.

- `summary` — gateway, DAVE, voice, source, storage, backups, recovery и command runtime;
- `gateway` — Discord heartbeat + DAVE;
- `voice` — AudioManager, frame demand и DAVE;
- `storage` — live persistence probe + backup scheduler;
- `session` — playback checkpoint/recovery;
- `source` — modern YouTube source identity + свежие source runtime errors/fallbacks;
- `failures` — до 10 последних записей из bounded in-memory журнала (сам журнал хранит максимум 25).

Doctor намеренно не делает внешние HTTP/Maven/YouTube probes из Discord-команды: зависший upstream не должен подвешивать саму диагностику. Failure journal не хранит Discord user IDs, stack traces или secrets.

## Smart Radio (v1.13.0)

```text
/radio start [mode:personal|server]
/radio status
/radio stop
```

Radio продолжает только действительно пустую очередь и добавляет по одному кандидату. `personal` строит seed из favorites/personal history владельца, `server` — из guild history. Режим ephemeral и после restart/deploy остаётся выключенным.
