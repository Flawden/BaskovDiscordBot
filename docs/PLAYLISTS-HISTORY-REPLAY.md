# Persistent Playlists, History & Replay

Начиная с `v0.13.0`, Baskov Discord Bot хранит серверные плейлисты и историю воспроизведения отдельно от короткой in-memory истории команды `/previous`.

## Команды

### История

```text
/history [page]
/replay position
```

`/history` показывает до 10 записей на страницу. Номер слева подходит для `/replay position:<номер>`.

В постоянную историю попадают треки, которые:

- нормально завершились;
- были вручную пропущены через `/skip` или переход к следующему треку;
- имеют повторно загружаемую публичную YouTube- или SoundCloud-ссылку.

Source failures, premature preview, `404`, `stuck`, cleanup recovery и неуспешные fallback-кандидаты в историю не записываются. На сервер хранится не более 50 последних записей, новые находятся сверху.

### Плейлисты

```text
/playlist list
/playlist create name
/playlist show name [page]
/playlist search query
/playlist add name
/playlist add-history name position
/playlist capture-queue name [include-current]
/playlist play name
/playlist remove name position
/playlist move name from to
/playlist rename name new-name
/playlist copy name new-name
/playlist dedupe name
/playlist delete name
```

`/playlist add` сохраняет текущий воспроизводимый трек. `add-history` берёт запись из постоянной `/history`, а `capture-queue` атомарно добавляет текущую музыкальную сессию в уже существующий плейлист. По умолчанию в capture включается текущий трек; `include-current:false` сохраняет только ожидающую очередь.

`rename`, `move`, `dedupe`, `capture-queue`, `add-history` и старые mutating-команды доступны владельцу плейлиста или участнику с `Manage Server`. `copy` не изменяет исходник: создаётся независимая копия, владельцем которой становится автор команды.

`/playlist search` ищет без учёта регистра по названию плейлиста, названию трека и исполнителю и показывает позиции совпавших треков. Все subcommands с параметром `name` поддерживают autocomplete имени плейлиста.

Ограничения:

- до 20 плейлистов на Discord-сервер;
- до 50 треков в одном плейлисте;
- название — до 40 символов после нормализации пробелов;
- имена сравниваются без учёта регистра;
- изменять плейлист может создатель либо участник с `Manage Server`;
- смотреть и запускать плейлист могут участники сервера, имеющие доступ к музыкальному управлению.

`/playlist play` повторно загружает сохранённые URL в исходном порядке. Недоступная запись считается отклонённой, но не мешает загрузке остальных треков. Итоговое сообщение показывает количество запущенных, поставленных в очередь и отклонённых записей.

`capture-queue` сначала преобразует все доступные TrackRequest в replayable `StoredTrack` и проверяет общий лимит. Если целиком набор не помещается в 50 треков, операция отклоняется без частичной записи. `dedupe` определяет повтор как одинаковую пару provider + playback URL/identifier и сохраняет первую копию.

## Хранилище

По умолчанию используется:

```text
data/music-library.tsv
```

В Docker:

```text
/app/data/music-library.tsv
```

Путь переопределяется переменной:

```text
DISCORD_BOT_MUSIC_LIBRARY_FILE
```

Формат имеет заголовок:

```text
BASKOV_MUSIC_LIBRARY_V1
```

Пользовательские строки кодируются URL-safe Base64, поэтому табы, переводы строк и Unicode не могут нарушить TSV-структуру. Запись выполняется через временный файл и atomic move; на POSIX-файловой системе устанавливаются права владельца `0600`.

Файл хранит только безопасные метаданные и публичный playback URL. Начиная с `v1.7.0`, тот же `BASKOV_MUSIC_LIBRARY_V1` содержит личные favorite records `F`; старые файлы без них совместимы без миграции. Подробности — в [`FAVORITES-PERSONAL-LIBRARY.md`](FAVORITES-PERSONAL-LIBRARY.md). `AudioTrack`, токены Discord, cookies, OAuth и другие секреты не сериализуются.

## Потоки и lifecycle

LavaPlayer вызывает завершение трека на audio callback thread. Чтобы файловый I/O не задерживал выдачу аудиофреймов, запись постоянной истории передаётся одному daemon executor `baskov-playback-history`.

Во время shutdown новые записи тихо отбрасываются после закрытия executor, а уже принятые получают до двух секунд на завершение.

## Отличие от `/previous`

`/previous` использует короткую in-memory историю текущей активной музыкальной сессии и мгновенный clone уже загруженного `AudioTrack`.

`/history` и `/replay` используют постоянный файл и переживают:

- отключение бота от voice;
- закрытие музыкальной сессии;
- restart контейнера;
- redeploy новой версии.

После `/replay` источник загружается заново, поэтому удалённое или ограниченное видео может стать недоступным позже.

## Проверка после рестарта

```bash
docker exec baskov-discord-bot sh -lc \
  'test -f /app/data/music-library.tsv && head -n 1 /app/data/music-library.tsv'

docker restart baskov-discord-bot
```

После восстановления healthcheck команды `/history`, `/playlist list` и `/favorites list` должны показывать те же данные.
