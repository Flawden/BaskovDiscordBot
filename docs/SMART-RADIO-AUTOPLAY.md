# Smart Radio & Autoplay

`v1.13.0` добавляет bounded autoplay поверх уже существующих favorites, personal history, guild history и безопасного YouTube search.

## Команды

```text
/radio start [mode:personal|server]
/radio status
/radio stop
```

`personal` использует favorites + personal history пользователя, который включил radio. `server` использует retained guild history.

## Safety model

- Radio добавляет только **один** трек после фактического опустошения current+queue.
- Кандидат проходит существующие ограничения длительности, stream policy и global queue bound.
- Radio-generated requester — `📻 Radio` с `userId=0`: personal requester cap не блокирует autoplay, а сгенерированный трек не притворяется ручным заказом владельца radio.
- Асинхронный поиск привязан к `activityVersion`. Любая человеческая активность между idle и завершением search делает результат stale, и он не добавляется.
- Последние radio tracks и свежая guild history исключаются из кандидатов; seed ротируются.
- Три последовательных refill failures автоматически выключают radio.
- Radio state не persist-ится и после restart/deploy всегда OFF. Это намеренная защита от неожиданного бесконечного autoplay после recovery.

## Взаимодействие с recovery

Session checkpoint/recovery не меняется. Если radio было включено до deploy, текущий трек и обычная очередь могут восстановиться через Session Recovery 2.0, но сам autoplay останется выключенным до нового `/radio start`.
