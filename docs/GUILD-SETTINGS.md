# Постоянные настройки Discord-серверов

Начиная с `v0.6.0`, бот сохраняет музыкальные предпочтения отдельно для каждой Discord-гильдии. `v1.11.0` расширяет тот же backwards-compatible properties-файл least-privilege moderation role и per-requester pending queue limit.

## Сохраняемые значения

- громкость новых музыкальных сессий;
- repeat mode: `off`, `track` или `queue`;
- playback access: `open`, `dj` или `vote`;
- request access: `open` или `dj`;
- DJ role ID либо `0`;
- manager role ID либо `0`;
- moderator role ID либо `0`;
- разрешённый voice/stage channel ID либо `0`;
- vote-skip threshold 25..100%;
- per-requester pending queue limit `0..100`, где `0` отключает cap;
- последние 25 administrative/moderation audit entries.

Громкость, repeat и requester limit обновляют активную сессию. Access/role/channel policies действуют сразу для следующих команд.

## Slash-команды

```text
/settings show
/settings permissions
/settings volume level:<0..max>
/settings repeat mode:<off|track|queue>
/settings access mode:<open|dj|vote>
/settings request-access mode:<open|dj>
/settings dj-role role:<@role>
/settings manager-role role:<@role>
/settings moderator-role role:<@role>
/settings requester-limit max:<0..100>
/settings voice-channel channel:<voice-or-stage>
/settings vote-threshold percent:<25..100>
/settings export
/settings import profile:<BASKOV_SETTINGS_V1-or-V2...>
/settings audit
/settings reset
```

`show` можно просматривать всем. Изменение settings разрешено owner, `Manage Server` или manager-role. Moderator-role не получает этих прав.

## Хранилище

Используется один properties-файл без внешней БД. Запись выполняется через temporary file + atomic replace.

Локально:

```text
data/guild-settings.properties
```

Docker:

```text
/app/data/guild-settings.properties
```

Новые keys `moderator-role` и `requester-queue-limit` лежат в том же файле; backup снимает snapshot целиком.

Старые файлы читаются без миграции: отсутствующие поля получают безопасные defaults (`request-access=open`, роли/канал = `0`, requester limit = `0`, audit пустой).

Старый binary при downgrade может проигнорировать новые keys и удалить их при следующей записи settings, поэтому после фактической настройки `v1.11.0` перед rollback сохраните backup `guild-settings.properties`.
