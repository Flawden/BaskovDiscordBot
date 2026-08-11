# Android Gateway Foundation — v1.30.0

`v1.30.0` is the backend release that makes the authenticated Product API usable as the stable boundary for the first external Baskov Music client without turning the bot container into a public raw HTTP service.

## Why this release exists

`v1.29.0` introduced `BaskovUser`, Discord pairing proof, hashed device sessions and authenticated reads, but two client-facing gaps intentionally remained:

1. a new client had no authenticated way to discover which Discord guilds it could use before calling `/home?guildId=...`;
2. the production deployment had no durable, opt-in path for a host reverse proxy to reach the API while keeping the container port off the public interface.

The Android client should not guess Discord IDs, hard-code guild IDs, or require the Java backend to publish `0.0.0.0:18080` directly to the Internet.

## Authenticated guild discovery

New endpoint:

```text
GET /api/v1/guilds
Authorization: Bearer <access token>
```

The bearer session resolves to a `BaskovUser`, then to its linked Discord identity. `RuntimeProductGuildAccessAdapter` enumerates only guilds where that linked Discord member is actually present. JDA stays behind `ProductGuildAccessPort`; the HTTP controller still has no JDA dependency.

Wire example:

```json
{
  "userId": "7c1f3d6e-...",
  "guilds": [
    {
      "guildId": "123456789012345678",
      "name": "Music Guild"
    }
  ]
}
```

Discord snowflakes are serialized as decimal **strings** at the wire boundary. This avoids precision loss in future JavaScript/Web clients and freezes the safe contract before Android or Web code depends on numeric JSON snowflakes.

## Committed OpenAPI contract

The repository now contains:

```text
docs/openapi/baskov-product-api-v1.yaml
```

It documents the current v1 surface:

```text
GET    /api/v1/capabilities
GET    /api/v1/guilds
GET    /api/v1/home
GET    /api/v1/mixes
GET    /api/v1/player
GET    /api/v1/library

POST   /api/v1/auth/device/pair
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
GET    /api/v1/auth/devices
DELETE /api/v1/auth/devices/{sessionId}
```

This is a committed client contract, not a new runtime dependency. No Springdoc/OpenAPI library is added to Maven, so repository routing and the existing dependency bootstrap remain unchanged.

## Safe remote deployment profile

Base production behavior is unchanged:

```text
BASKOV_PRODUCT_API_ENABLED=false
BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE=none
BASKOV_PRODUCT_API_BIND_ADDRESS=127.0.0.1
```

A new compose override exists:

```text
deploy/docker-compose.product-api.yml
```

When the GitHub environment variable below is explicitly enabled:

```text
BASKOV_PRODUCT_API_REMOTE_ENABLED=true
```

CI/CD adds that override and publishes the API only on the VPS host loopback interface:

```text
127.0.0.1:${BASKOV_PRODUCT_API_HOST_PORT:-18080}:18080
```

Inside the container Spring binds to `0.0.0.0:18080`, but Docker publishes it only to host `127.0.0.1`. That is deliberate: a TLS reverse proxy on the VPS can reach the API, while the raw Spring port is not Internet-facing.

The remote profile is rejected when `BOT_NETWORK_MODE=host`, because combining host networking with an internal `0.0.0.0` API bind would bypass the loopback publication boundary.

After deployment, `remote-deploy.sh` verifies `/api/v1/capabilities` through the host-loopback port before declaring the runtime healthy.

## Reverse proxy boundary

`v1.30.0` does **not** pretend that loopback publication is public mobile security. Before a phone connects over the Internet, the host reverse proxy must provide at minimum:

- HTTPS/TLS;
- a real hostname;
- request-size/time limits;
- rate limiting for pairing/auth endpoints;
- no direct public forwarding of port `18080`.

Those are edge-deployment concerns. The application keeps access/refresh authentication and guild authorization; the reverse proxy owns Internet transport hardening.

## What this release does not add

Still intentionally absent:

```text
POST /player/play
POST /player/skip
POST /mixes/start
POST /favorites
```

`ProductCapabilities.mutationsEnabled` remains `false`. Android v0.1 can pair, refresh, discover guilds and render authenticated read models; remote music control remains a separate permission/orchestration release.

## Persistence

No persistence migration and no sixth store.

Existing five stores remain:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv
baskov-auth.tsv
```

## Android handoff

The first Android repository can now follow a deterministic bootstrap:

```text
Discord /device pair
        ↓
POST /auth/device/pair
        ↓
store access + refresh securely
        ↓
GET /auth/me
        ↓
GET /guilds
        ↓
select guild
        ↓
GET /home?guildId=...
```

The Android app remains a separate repository and must not reimplement recommendation, library, identity or guild-authorization logic.
