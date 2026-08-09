# Administration & Moderation 2.0

`v1.11.0` добавляет отдельный least-privilege слой для управления общей музыкальной очередью. Цель — не выдавать полный `manager-role` человеку, которому нужно только поддерживать порядок в waiting queue.

## Уровни доступа

### Полное администрирование

Guild settings и административные операции по-прежнему доступны:

- владельцу сервера;
- участнику с Discord `Manage Server`;
- настроенной `manager-role`.

`moderator-role` **не** входит в `GuildAdministrationPolicy`, поэтому сама по себе не позволяет менять `/settings`, импортировать профиль или администрировать persistent playlists.

### Queue moderation

Waiting queue могут модерировать:

- владелец;
- `Manage Server`;
- `manager-role`;
- `moderator-role`;
- `DJ-role`.

Настройка:

```text
/settings moderator-role role:<@role>
/settings moderator-role
```

Пустое значение очищает роль. `@everyone` запрещён так же, как для DJ/manager roles.

## Команды `/moderation`

```text
/moderation status
/moderation remove position:<n> [revision:<r>]
/moderation purge user:<@user> [revision:<r>]
/moderation audit
```

### `status`

Read-only snapshot:

- moderator/DJ/manager roles;
- personal requester queue limit;
- текущий размер waiting queue;
- число requesters;
- текущая queue revision.

### `remove`

Удаляет ровно одну waiting position. Текущий playing track не затрагивается.

Если передан `revision`, scheduler сначала сравнивает его с текущей revision. Stale значение возвращает отказ и **не интерпретирует старую позицию в уже изменённой очереди**.

### `purge`

Удаляет все pending tracks выбранного Discord user ID. Другие requesters и текущий playing track остаются без изменений.

Mutation также поддерживает optional revision guard.

### `audit`

Показывает общий bounded administrative/moderation audit. Успешные moderation mutations записывают actor Discord user ID и краткое действие. Хранятся последние 25 событий в существующем `guild-settings.properties`.

## Per-requester pending limit

```text
/settings requester-limit max:<n>
```

- `0` — персональный лимит выключен;
- `1..100` — максимум pending tracks одного requester-а, но фактический верхний предел также ограничен deployment `maxQueueSize`.

Лимит считается только для waiting queue. Текущий playing track не входит в него.

Enforcement находится внутри `TrackScheduler.queue(...)`, поэтому нельзя обойти cap через другой UI path: `/play`, `/search`, favorites, history/replay и playlist batch в итоге проходят через один scheduler.

При достижении cap возвращается отдельный `REQUESTER_LIMIT`, а не общий `QUEUE_FULL`.

## Settings profile V2

`/settings export` с `v1.11.0` выдаёт:

```text
BASKOV_SETTINGS_V2.<base64url>
```

V2 добавляет:

- `moderatorRole`;
- `requesterQueueLimit`.

`/settings import` продолжает читать `BASKOV_SETTINGS_V1`; отсутствующие новые поля получают безопасные defaults:

```text
moderatorRole=0
requesterQueueLimit=0
```

Перед V2 import проверяются существование moderator-role и соответствие requester limit локальному `maxQueueSize`.

## Persistence

Нового файла нет. Новые properties:

```text
guild.<guildId>.moderator-role=<discordRoleId>
guild.<guildId>.requester-queue-limit=<0..100>
```

Запись использует тот же temporary file → atomic replace, owner-only permissions и существующий backup `guild-settings.properties`.

Downgrade не требует миграции storage: старые binaries проигнорируют неизвестные keys, но могут удалить их при следующей записи settings. Перед rollback ниже `v1.11.0`, если moderator-role/requester-limit уже настроены, сохраните backup `guild-settings.properties`.
## Session recovery

Per-requester limit применяется к новым пользовательским запросам, но не обрезает уже сохранённый session checkpoint. Internal recovery enqueue обходит только requester cap; global queue size, max track duration и stream restrictions продолжают действовать. Это сохраняет exact recovery semantics `v1.10.0` при включении moderation limits.
