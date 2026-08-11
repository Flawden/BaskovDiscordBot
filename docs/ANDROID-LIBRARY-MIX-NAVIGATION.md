# Android Library & Mix Navigation — v1.31.0

`v1.31.0` extends the authenticated read contract needed by Baskov Android v0.2 without enabling music mutations.

## Library

`GET /api/v1/library?guildId=...` keeps the existing counters and `recent` preview and adds full bounded personal lists:

- `favoriteTracks` — persisted favorites for the linked Discord user in the guild;
- `historyTracks` — persisted personal listening history, newest first.

The repository limits remain authoritative (100 favorites, 200 personal-history rows).

## Mix details

`GET /api/v1/mixes/{stationSlug}?guildId=...` returns metadata for an exact curated station plus `seedPreview`.

`seedPreview` is the deterministic/read-only seed set used to initialize the station. It is **not** a predicted queue: discovery/ranking/provider resolution still happens in the existing radio/playback pipeline. The endpoint does not start radio, create a music manager or mutate playback state.

## Security invariants

All user/guild reads require the existing bearer device session and guild-access guard. Discord snowflakes remain JSON strings. Music mutations remain disabled.
