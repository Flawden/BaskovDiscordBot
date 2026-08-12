# Mobile Search API — v1.35.0

`GET /api/v1/search` exposes the existing Baskov text-search pipeline to authenticated product clients without adding a music mutation boundary.

Request:

```text
GET /api/v1/search?guildId=<snowflake>&query=Green%20Day%20Holiday&limit=5
Authorization: Bearer <device access token>
```

Response candidates contain only provider-neutral `stableKey`, `title`, and `artist`. Android starts a selected candidate through the existing authenticated `/api/v1/playback/stream` endpoint, so provider resolution and Ogg/Opus transport stay server-owned.

Constraints:
- existing bearer identity and guild membership guard are mandatory;
- text queries only;
- query length <= 200 characters;
- result limit 1..10, default 5;
- endpoint is read-only and does not mutate Discord voice/queue state.
