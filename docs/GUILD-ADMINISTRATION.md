# Permissions & Guild Administration

`v1.5.0` разделил права Баскова на администрирование, добавление музыки и управление playback. `v1.11.0` добавляет четвёртый, least-privilege слой: moderation waiting queue.

## Полное администрирование Баскова

Guild settings могут менять:

- владелец Discord-сервера;
- участник с Discord permission `Manage Server`;
- участник настроенной `manager-role`.

`manager-role` также считается административной для persistent library operations.

```text
/settings manager-role role:<@role>
/settings manager-role
```

Пустая роль очищает настройку. `@everyone` запрещён.

## Queue moderator

`v1.11.0` позволяет выдать права только на порядок в waiting queue без полного доступа к settings:

```text
/settings moderator-role role:<@role>
/settings moderator-role
```

Queue moderation разрешена owner / `Manage Server` / manager-role / moderator-role / DJ-role.

Moderator-role сама по себе **не** может менять guild settings, import/export profiles или администрировать persistent playlists.

Команды:

```text
/moderation status
/moderation remove position:<n> [revision:<r>]
/moderation purge user:<@user> [revision:<r>]
/moderation audit
```

Mutation-команды revision-safe. Подробности — в [`ADMINISTRATION-MODERATION-2.md`](ADMINISTRATION-MODERATION-2.md).

## Раздельные права requests и playback

`/settings access` отвечает за прямые playback controls:

```text
open  — слушатели в voice-канале могут управлять напрямую
dj    — прямое управление только DJ/администрации
vote  — DJ/администрация управляют напрямую, обычные слушатели голосуют за skip
```

`/settings request-access` отдельно отвечает за добавление музыки:

```text
open  — любой допустимый слушатель может /play, /search, discovery/replay и playlist playback
dj    — новые треки может добавлять только DJ или администрация
```

## Per-requester queue limit

```text
/settings requester-limit max:<0..100>
```

`0` выключает limit. Положительное значение ограничивает только pending tracks одного requester-а; current playing track не учитывается. Deployment `maxQueueSize` остаётся верхней границей.

Enforcement находится в `TrackScheduler`, поэтому одинаково применяется ко всем queueing paths.

## Voice/stage restriction

```text
/settings voice-channel channel:<voice-or-stage>
/settings voice-channel
```

Если канал задан, новые music requests разрешены только для сессии в этом канале. Ограничение нельзя обойти owner/manager-role: оно задаёт место музыкальной сессии, а не пользовательское право.

## Матрица прав

```text
/settings permissions
```

Команда показывает manager/moderator/DJ roles, request/playback access, queue moderation, personal pending limit, разрешённый voice/stage канал и vote-skip threshold.

## Export / import

```text
/settings export
/settings import profile:<BASKOV_SETTINGS_V2...>
```

V2 содержит volume/repeat/access policies, DJ/manager/moderator roles, music channel, vote threshold и requester queue limit. Discord token/secrets не экспортируются.

Decoder остаётся совместимым с `BASKOV_SETTINGS_V1`: новые поля получают defaults `moderatorRole=0` и `requesterQueueLimit=0`.

Import выполняется атомарно. Перед записью Басков проверяет:

- максимальную громкость deployment;
- существование DJ/manager/moderator roles;
- существование и audio-тип voice/stage channel;
- requester limit против локального `maxQueueSize`;
- версию и целостность profile payload.

Role/channel IDs специфичны для Discord-сервера.

## Audit

Успешные settings и queue moderation mutations добавляют bounded audit entry:

```text
/settings audit
/moderation audit
```

Хранятся последние 25 событий: timestamp, Discord user ID автора и краткое действие. Audit лежит внутри `guild-settings.properties`, поэтому persistence backup включает его автоматически.

## Reset

```text
/settings reset
→ [Подтвердить] [Отмена]
```

Confirmation живёт две минуты, привязана к guild + пользователю и перед mutation повторно проверяет административные права. Reset очищает также moderator-role и requester queue limit.
