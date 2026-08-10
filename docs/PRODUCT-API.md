# Baskov Music Product API v1 — preview

`v1.28.0` вводит первую внешнюю границу будущего Baskov Music. Это **read-only preview**, а не публичный remote-control API: собственные пользователи, auth и device sessions запланированы на `v1.29.0`.

## Архитектура

```text
Discord / future HTTP / Android
          ↓
   MusicProductService
          ↓
 Home / Product read ports
          ↓
 runtime + persistent repositories
```

Discord `/home` и HTTP adapter используют одну application boundary. HTTP controller не импортирует `PlayerManager`, JDA или repositories.

## HTTP resources

При явном локальном opt-in:

- `GET /api/v1/capabilities`
- `GET /api/v1/home?guildId=<id>&userId=<id>`
- `GET /api/v1/mixes?guildId=<id>&userId=<id>`
- `GET /api/v1/player?guildId=<id>`
- `GET /api/v1/library?guildId=<id>&userId=<id>`

Wire DTO versioned отдельно от внутренних `HomeSnapshot`/`ProductPlaybackSnapshot`, поэтому внутренний refactoring не обязан ломать внешний JSON contract.

## Безопасность v1.28

По умолчанию:

```text
BASKOV_PRODUCT_API_ENABLED=false
BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE=none
BASKOV_PRODUCT_API_BIND_ADDRESS=127.0.0.1
BASKOV_PRODUCT_API_PORT=18080
```

Docker Compose не публикует порт. В v1.28 отсутствуют `POST`/mutation endpoints. Это сознательно: start/skip/favorite и остальные изменения состояния нельзя открывать до появления Baskov user identity, authentication, device sessions и authorization в `v1.29.0`.

## Что дальше

`v1.29.0` должен добавить собственную identity/auth boundary и только затем разрешить authenticated mutation use-cases. Android будет работать не с Discord/JDA, а с этой product boundary.
