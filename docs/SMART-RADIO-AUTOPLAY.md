# Smart Radio & Smart Discovery

`v1.13.0` добавил bounded autoplay, а `v1.14.0` добавляет внешний similarity/discovery слой без изменения playback transport.

## Команды

```text
/radio start [mode:personal|server] [strategy:familiar|similar|discovery]
/radio status
/radio why
/radio stop
```

`personal` использует favorites + personal history пользователя, который включил radio. `server` использует retained guild history.

Стратегии:

- `familiar` — v1.13 local seed continuation без внешнего provider;
- `similar` — предпочитает Last.fm similar-track candidates, но знакомая музыка допустима со штрафом;
- `discovery` — hard novelty: already-known/recent track identity отбрасывается, recent artists получают cooldown.

Last.fm опционален. Если `LASTFM_API_KEY` отсутствует или provider временно недоступен, radio сохраняет local fallback вместо падения музыкального lifecycle.

## Safety model

- Radio добавляет только **один** трек после фактического опустошения current+queue.
- Кандидат проходит существующие ограничения длительности, stream policy и global queue bound.
- Radio-generated requester — `📻 Radio` с `userId=0`: personal requester cap не блокирует autoplay, а сгенерированный трек не притворяется ручным заказом владельца radio.
- Асинхронный поиск привязан к `activityVersion`. Любая человеческая активность между idle и завершением search делает результат stale, и он не добавляется.
- Последние radio tracks и свежая guild history исключаются из кандидатов; seed ротируются.
- Novelty сравнивается по normalized `artist + title`, а не только по YouTube/provider identifier.
- External recommendation возвращает только metadata candidate; затем Басков выполняет обычный `ytsearch:` и существующие queue safety checks.
- `/radio why` объясняет последнюю recommendation: provider, seed и ranking reason.
- Три последовательных refill failures автоматически выключают radio.
- Radio state не persist-ится и после restart/deploy всегда OFF. Это намеренная защита от неожиданного бесконечного autoplay после recovery.

## Взаимодействие с recovery

Session checkpoint/recovery не меняется. Если radio было включено до deploy, текущий трек и обычная очередь могут восстановиться через Session Recovery 2.0, но сам autoplay останется выключенным до нового `/radio start`.
