# Постоянные настройки Discord-серверов

Начиная с `v0.6.0`, бот сохраняет музыкальные предпочтения отдельно для каждой Discord-гильдии. `v0.14.0` добавил DJ/vote-skip, а `v1.5.0` расширяет тот же backwards-compatible properties-файл административными ролями, request policy, voice restriction и bounded audit.

## Сохраняемые значения

- громкость новых музыкальных сессий;
- repeat mode: `off`, `track` или `queue`;
- playback access: `open`, `dj` или `vote`;
- request access: `open` или `dj`;
- DJ role ID либо `0`;
- manager role ID либо `0`;
- разрешённый voice/stage channel ID либо `0`;
- vote-skip threshold 25..100%;
- последние 10 audit entries изменений guild settings.

Громкость и repeat применяются к новой сессии и обновляют активную. Access/role/channel политики действуют сразу для следующих команд.

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
/settings voice-channel channel:<voice-or-stage>
/settings vote-threshold percent:<25..100>
/settings export
/settings import profile:<BASKOV_SETTINGS_V1...>
/settings audit
/settings reset confirm:<true|false>
```

`show` можно просматривать всем. Изменение/administration разрешено owner, `Manage Server` или настроенной manager-role.

Подробная матрица прав, export/import и audit описаны в [`GUILD-ADMINISTRATION.md`](GUILD-ADMINISTRATION.md).

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

`/app/data` находится в named Docker volume. Existing persistence backup снимает snapshot этого файла целиком, поэтому новые административные поля и audit автоматически резервируются.

Старые файлы читаются без миграции: отсутствующие administrative поля получают безопасные defaults (`request-access=open`, роли/канал = `0`, audit пустой).
