# Permissions & Guild Administration

`v1.5.0` разделяет права Баскова на три независимых уровня: администрирование, добавление музыки и управление активным playback.

## Кто администрирует Баскова

Административные guild settings могут менять:

- владелец Discord-сервера;
- участник с Discord permission `Manage Server`;
- участник настроенной `manager-role`.

`manager-role` также считается административной ролью для операций постоянной библиотеки, где раньше требовался owner/`Manage Server`.

```text
/settings manager-role role:<@role>
/settings manager-role
```

Пустая роль очищает настройку. `@everyone` запрещён.

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

Это позволяет, например, оставить vote-skip для слушателей, но запретить им самостоятельно менять очередь новыми запросами.

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

Команда показывает manager-role, DJ-role, request access, playback access, разрешённый voice/stage канал и vote-skip threshold.

## Export / import

```text
/settings export
/settings import profile:<BASKOV_SETTINGS_V1...>
```

Export выдаёт ephemeral URL-safe профиль без Discord token или других secrets. Профиль содержит только guild music settings и Discord IDs ролей/канала.

Import выполняется атомарно. Перед записью Басков проверяет:

- максимальную громкость текущего deployment;
- существование DJ-role и manager-role в целевой guild;
- существование и audio-тип voice/stage channel;
- версию и целостность profile payload.

Role/channel IDs специфичны для Discord-сервера, поэтому profile с одного сервера может потребовать очистки или перенастройки ролей перед переносом на другой.

## Audit

Каждое успешное изменение через `/settings` добавляет bounded audit entry:

```text
/settings audit
```

Хранятся последние 10 записей: timestamp, Discord user ID автора и краткое действие. Audit лежит внутри `guild-settings.properties`, поэтому уже существующий persistence backup автоматически включает его.

## Reset

Полный сброс требует явного подтверждения:

```text
/settings reset confirm:true
```

`confirm:false` ничего не меняет.
