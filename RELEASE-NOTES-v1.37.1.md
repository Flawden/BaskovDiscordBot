# BaskovDiscordBot v1.37.1 — Favorites UX & Scale

- Favorites are no longer hard-capped at 100 tracks.
- `GET /api/v1/favorites` supports optional `offset`/`limit` while keeping the legacy no-pagination response behavior.
- Added `/favorites/keys`, `/favorites/status`, and stable-key removal for responsive Android favorite toggles.
- Discord and Android continue to share the same persistent favorite store.
