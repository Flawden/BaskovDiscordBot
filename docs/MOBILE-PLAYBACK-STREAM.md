# Mobile Playback Stream — v1.32.0

`v1.32.0` adds the first local-playback transport for Baskov Android without turning Android into a provider search client and without enabling guild music mutations.

## Contract

Authenticated endpoint:

```text
GET /api/v1/playback/stream?guildId=<snowflake>&artist=<artist>&title=<title>
Authorization: Bearer <device access token>
Accept: audio/ogg
```

The request contains only provider-neutral logical track fields. The server creates `TrackIdentity`, runs the existing `PlaybackResolver`, tries healthy YouTube/SoundCloud transports in resolver order, loads one exact `AudioTrack` through the shared LavaPlayer runtime, and streams it as Ogg/Opus.

The Android client does **not** perform YouTube/SoundCloud search or source extraction. Provider fallback remains server-side.

## Stream properties

- content type: `audio/ogg`;
- no decode/transcode: LavaPlayer's Opus packets are remuxed into Ogg pages;
- foreground-only transport for v0.3;
- no seek/range contract in v1.32 (`Accept-Ranges: none`);
- sends `X-Accel-Buffering: no` so the existing nginx TLS proxy does not buffer the foreground audio response;
- maximum four concurrent mobile streams per backend process;
- existing `discord-bot.music.maxTrackDuration` applies;
- stream access requires the caller's linked Discord identity to have access to the requested guild;
- Product API remains `AUTHENTICATED_READ`, `mutationsEnabled=false`.

## Failure model

Input/auth/guild failures retain the existing v1 JSON error shape. If providers are unavailable, the track cannot be loaded, or the mobile stream pool is exhausted, the endpoint returns `503 PLAYBACK_UNAVAILABLE` before audio body streaming starts.

A client disconnect during the audio body closes the isolated LavaPlayer instance and releases the stream slot.

## Non-goals

This release does not add MediaSession, background playback, notification/lock-screen controls, Bluetooth/headset actions, remote Discord playback mutations, seeking, or persistent mobile queues. Those remain Android-side follow-up work after the foreground playback slice is proven.
