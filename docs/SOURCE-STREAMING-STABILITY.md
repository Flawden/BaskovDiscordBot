# Source Streaming Stability

## Production root cause

Production evidence from 2026-08-05 showed two independent SoundCloud source failures while Discord voice transport stayed connected and native DAVE remained ready:

1. A track advertised as `00:03:13` finished at roughly `00:00:30` with LavaPlayer reason `FINISHED`. This is treated as a truncated preview, not a successful full-track completion.
2. Several SoundCloud HLS playback URLs failed with `Invalid status code for soundcloud stream: 404`.

Neither condition is a Discord voice, DAVE, Docker bridge, UDP, or frame-polling failure.

## Recovery policy

For non-live tracks with advertised duration of at least 90 seconds, a `FINISHED` event is considered premature when:

- playback position is at most 45 seconds; and
- at least 60 seconds of the advertised duration remain.

A premature finish is recorded as a source failure and advances to the next hidden search fallback. It is not written into playback history and does not trigger repeat mode.

YouTube and legacy SoundCloud text searches retain up to nine deduplicated hidden fallbacks in addition to the selected result. New plain-text requests use YouTube; the SoundCloud branch remains only for compatibility and direct-source recovery. The visible queue still contains only the user's requested item.

## Diagnostics

`/status` keeps three independent values:

- `Last source error` — the root media failure, including deepest HTTP/IO cause and playback position;
- `Last recovery` — the latest fallback transition;
- `Last stale callback` — the latest ignored LavaPlayer callback.

Fallback and stale callback events must not overwrite the root source error.

## Limitations

The SoundCloud recovery path cannot make a removed or unavailable media stream playable when every result returns `404`. New plain-text requests no longer start from SoundCloud and therefore avoid its 30-second preview/expired HLS failure mode by default. In that case the bot exhausts the hidden recovery pool, advances to the next visible queue item, and starts the normal idle-disconnect timer only when no playable items remain.
