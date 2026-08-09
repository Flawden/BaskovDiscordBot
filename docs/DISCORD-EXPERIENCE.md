# Discord Experience

`v1.6.0` делает существующие slash-команды менее шумными и защищает destructive actions от случайного клика без изменения music persistence.

## Интерактивная помощь

```text
/help
/help section:playback
/help section:queue
/help section:library
/help section:admin
```

Help-сообщение ephemeral. Пять кнопок переключают разделы через edit того же сообщения, поэтому навигация не создаёт новые slash invocations и не засоряет канал.

Раздел overview показывает текущие request/playback policies и сообщает, есть ли у пользователя административный доступ Баскова на этом сервере.

## Live refresh `/status`

`/status` остаётся read-only и ephemeral, но теперь содержит кнопку `↻ Обновить статус`.

Refresh заново строит snapshot и повторяет live storage probe. Он не меняет queue/playback, не создаёт новую music session и не обходит существующие health checks.

## Одноразовые подтверждения

Интерактивное подтверждение требуется для:

```text
/stop
/clear                # только если очередь непуста
/playlist delete
/settings reset
```

Stop-кнопка под `/now` использует тот же pipeline.

Каждая confirmation session:

- существует не более двух минут;
- привязана одновременно к Discord guild и user ID;
- хранится только in-memory;
- потребляется атомарно до destructive mutation;
- не может быть повторно использована;
- повторно проверяет актуальные permissions перед выполнением.

`Отмена` тоже потребляет token. После restart процесса старые confirmation buttons безопасно становятся недействительными.

## Почему permissions проверяются дважды

Права могли измениться между slash-командой и нажатием `Подтвердить`: пользователь мог потерять DJ/manager-role, покинуть voice channel или изменились guild policies. Поэтому предварительная проверка отвечает только за показ confirmation UI, а окончательная проверка выполняется непосредственно перед mutation.

## Совместимость

Не меняются:

- `music-library.tsv`;
- `guild-settings.properties`;
- `music-sessions.tsv`;
- backup ZIP format;
- queue revision semantics;
- voice recovery/session restoration;
- legacy prefix compatibility layer.

`/settings reset confirm:true` удалён из slash schema: теперь `/settings reset` всегда использует кнопочное подтверждение.
