# BaskovDiscordBot v1.37.0 — Favorites Mobile API

Baskov Music external clients can now read and mutate the same personal favorites already used by Discord `/favorites`.

## Highlights
- authenticated guild-scoped favorites list;
- server-resolved remote track add;
- ordered position-based remove;
- clear personal favorites;
- duplicate add is idempotent;
- linked Discord user identity remains the only favorite owner;
- no Android-side favorites database;
- no Discord voice/player mutation surface added.

This release is the backend half of BaskovAndroid v0.14.0 My Music & Servers.
