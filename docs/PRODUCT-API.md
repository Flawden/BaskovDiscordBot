# Baskov Music Product API v1

`v1.29.0` переводит HTTP boundary из read-only preview в **authenticated read API**. Product API всё ещё отключён и bind-ится на loopback по умолчанию, но user-scoped endpoints больше не принимают произвольный `userId`: личность выводится из bearer device session.

## Безопасная модель

1. Пользователь внутри Discord вызывает `/device pair`.
2. Бот показывает ephemeral одноразовый 8-символьный код с TTL 5 минут.
3. Новый клиент вызывает `POST /api/v1/auth/device/pair` с кодом и именем устройства.
4. Backend создаёт или находит provider-neutral `BaskovUser`, связывает его с Discord identity и создаёт `DeviceSession`.
5. Клиент получает access token + refresh token. В `baskov-auth.tsv` сохраняются только SHA-256 хэши токенов.
6. Access token используется как `Authorization: Bearer <token>`; refresh token ротируется при каждом refresh.
7. Для guild-scoped reads backend дополнительно проверяет, что linked Discord identity действительно состоит в указанном guild.

Pairing-коды process-local, одноразовые и никогда не записываются в persistent storage. Access/refresh plaintext выдаются только в pair/refresh response и не логируются.

## Endpoints

Без bearer:

```text
GET  /api/v1/capabilities
POST /api/v1/auth/device/pair
POST /api/v1/auth/refresh
```

С bearer:

```text
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
GET    /api/v1/auth/devices
DELETE /api/v1/auth/devices/{sessionId}

GET /api/v1/home?guildId=...
GET /api/v1/mixes?guildId=...
GET /api/v1/player?guildId=...
GET /api/v1/library?guildId=...
```

`home/mixes/library` получают legacy music profile через Discord identity, привязанную к `BaskovUser`. В `v1.29` существующие favorites/history/feedback intentionally **не мигрируются** с Discord numeric ID: account boundary вводится без риска для уже накопленного музыкального профиля.

## Token lifecycle

Defaults:

```text
pairing code TTL   5m
access token TTL   30m
refresh token TTL  30d
active devices     max 8
```

Refresh token rotation инвалидирует предыдущие access/refresh hashes этой session. Logout/revoke устанавливают session как revoked; другое устройство того же пользователя продолжает работать.

## Persistence

Новый `data/baskov-auth.tsv` имеет формат `BASKOV_AUTH_V1` и содержит три типа записей:

```text
U — BaskovUser
I — ExternalIdentity
S — DeviceSession with token hashes
```

Файл входит в storage readiness и обычный bounded backup ZIP. В owner-only persistent store нет plaintext access/refresh tokens.

## Runtime defaults

API остаётся opt-in:

```text
BASKOV_PRODUCT_API_ENABLED=false
BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE=none
BASKOV_PRODUCT_API_BIND_ADDRESS=127.0.0.1
BASKOV_PRODUCT_API_PORT=18080
```

Docker Compose по-прежнему не публикует `18080` наружу. Перед будущим remote/mobile exposure нужен отдельный TLS/reverse-proxy deployment contract; сам `v1.29` не открывает production port.

## Почему music mutations всё ещё выключены

Authentication уже существует, но `v1.29` не смешивает её с переносом Discord permission/playback orchestration в HTTP. `ProductCapabilities.mutationsEnabled=false` относится именно к **music mutations** (`start/skip/favorite/...`). Auth lifecycle (`pair/refresh/logout/revoke`) естественно изменяет auth state.

Следующий Android-facing этап может безопасно добавить client-neutral authenticated mutation use-cases поверх уже существующих `BaskovUser`/`DeviceSession`, не принимая Discord user IDs от клиента и не раскрывая transport/provider identifiers.
