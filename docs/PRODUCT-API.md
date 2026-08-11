# Baskov Music Product API v1

## v1.31 Android Library & Mix Navigation

The authenticated read boundary now exposes full bounded personal favorites/history lists through `/api/v1/library` and exact curated-station details through `/api/v1/mixes/{stationSlug}`. `seedPreview` is intentionally a seed preview rather than a predicted playback queue. Music mutations remain disabled.

`v1.30.0` promotes the authenticated read boundary into an **Android-gateway foundation**. Identity still comes from `BaskovUser` device sessions introduced in v1.29, while v1.30 adds authenticated guild discovery, a committed OpenAPI contract and an opt-in host-loopback deployment profile for a future TLS reverse proxy.

## Security model

1. The user invokes `/device pair` inside Discord.
2. Discord returns an ephemeral one-time 8-character code with a default 5-minute TTL.
3. A client calls `POST /api/v1/auth/device/pair` with that code and a device name.
4. Backend creates or finds the provider-neutral `BaskovUser`, links Discord identity and creates a `DeviceSession`.
5. Client receives access + refresh tokens. `baskov-auth.tsv` stores only SHA-256 hashes.
6. Access token is sent as `Authorization: Bearer <token>`; refresh rotates both token hashes.
7. `GET /api/v1/guilds` discovers guilds available to the linked Discord identity.
8. Every guild-scoped read re-checks that the linked Discord identity is actually a member of the requested guild.

Pairing codes are process-local, one-time and never persisted. Plaintext access/refresh tokens exist only in pair/refresh responses and client storage.

## Endpoints

Without bearer:

```text
GET  /api/v1/capabilities
POST /api/v1/auth/device/pair
POST /api/v1/auth/refresh
```

With bearer:

```text
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
GET    /api/v1/auth/devices
DELETE /api/v1/auth/devices/{sessionId}

GET /api/v1/guilds
GET /api/v1/home?guildId=...
GET /api/v1/mixes?guildId=...
GET /api/v1/player?guildId=...
GET /api/v1/library?guildId=...
```

`home/mixes/library` still read the legacy music profile through the Discord identity linked to the `BaskovUser`. Existing favorites/history/feedback are intentionally not mass-migrated from Discord persistence keys.

## Wire identifiers

Provider-neutral account IDs remain Baskov UUID strings.

Discord snowflakes in JSON responses are also strings:

```json
{
  "guildId": "123456789012345678"
}
```

This prevents loss of integer precision in future JavaScript clients. Internally the Java runtime may continue using `long`; conversion happens in `ProductApiMapper` at the wire boundary.

## OpenAPI

The committed client contract is:

```text
docs/openapi/baskov-product-api-v1.yaml
```

It is source-controlled intentionally instead of adding another runtime/Maven dependency. Android/Web clients can use it as the versioned API reference while architecture tests guard the critical endpoint/security/identifier invariants.

## Token lifecycle

Defaults:

```text
pairing code TTL   5m
access token TTL   30m
refresh token TTL  30d
active devices     max 8
```

Refresh rotation invalidates the previous access/refresh hashes for that session. Logout/revoke affects only the selected device session.

## Persistence

`data/baskov-auth.tsv` remains `BASKOV_AUTH_V1`:

```text
U — BaskovUser
I — ExternalIdentity
S — DeviceSession with token hashes
```

It remains the fifth persistent store and participates in readiness/backup. v1.30 adds no storage file or format migration.

## Runtime defaults and remote profile

Base API posture remains opt-in and non-web:

```text
BASKOV_PRODUCT_API_ENABLED=false
BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE=none
BASKOV_PRODUCT_API_BIND_ADDRESS=127.0.0.1
BASKOV_PRODUCT_API_PORT=18080
```

Base `docker-compose.yml` still publishes no port.

For a TLS reverse proxy on the VPS, CI/CD can explicitly enable:

```text
BASKOV_PRODUCT_API_REMOTE_ENABLED=true
BASKOV_PRODUCT_API_HOST_PORT=18080
```

The `docker-compose.product-api.yml` override starts Spring web mode and publishes only:

```text
127.0.0.1:<host-port>:18080
```

This is **not** a direct public API configuration. The raw Spring port must stay unreachable from the Internet; HTTPS and edge rate limiting belong at the reverse proxy. Host-network mode is intentionally rejected when the remote API profile is enabled.

## Why music mutations are still off

Authentication and client discovery are now sufficient for Android read MVP, but remote playback changes require a separate client-neutral permission/session ownership model. `ProductCapabilities.mutationsEnabled=false` therefore still means no remote music mutation endpoints.

Auth lifecycle (`pair/refresh/logout/revoke`) continues to mutate auth state by design.