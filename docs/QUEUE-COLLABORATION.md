# Queue Collaboration & Social UX

## Цель

`v1.9.0` делает существующую requester-aware очередь удобной для нескольких слушателей, не меняя playback ownership и storage format.

## Self-service

- `/queue-manage mine` — только собственные ожидающие треки с глобальными позициями.
- `/queue-manage remove-own position:<n> [revision]` — удалить один свой трек.
- `/queue-manage remove-mine [revision]` — удалить все свои ожидающие треки.

`remove-own` не требует DJ/manager permission, потому что сервер атомарно проверяет `TrackRequester.userId` позиции. Чужой трек удалить этим путём нельзя. Если передана устаревшая revision, операция завершается до ownership-check и очередь не меняется.

## Community view

`/queue-manage community` и кнопка `👥 Заказчики` показывают только состояние текущей waiting queue: requester, количество треков, суммарную длительность и глобальные позиции. Это не persistent social profile и не listening telemetry.

## Vote skip status

Кнопка `🗳️ Vote skip` читает текущую сессию голосования и показывает `votes/required`, eligible listeners, threshold и состояние голоса текущего пользователя. Просмотр статуса никогда не считается голосом. Голос по-прежнему отдаётся через `/voteskip`, `/skip` или обычную skip-кнопку согласно `PlaybackAccessMode`.

## Совместимость

- `music-library.tsv`, `guild-settings.properties` и music-session format не меняются.
- Queue revision остаётся единственным stale guard для позиционных mutation.
- Voice recovery/session restoration не меняются.
