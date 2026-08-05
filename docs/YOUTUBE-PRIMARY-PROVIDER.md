# YouTube Primary Provider

## Decision

Starting with `v0.11.3`, plain-text music requests use YouTube as the primary search provider:

```text
/play green day holiday
→ ytsearch:green day holiday
```

The change responds to production evidence from 2026-08-05: SoundCloud returned 29.7-second previews for full-length metadata and mass `404` responses for HLS playback URLs.

## Routing

| User input | LavaPlayer identifier | Provider |
|---|---|---|
| Plain text | `ytsearch:<query>` | YouTube |
| YouTube URL | URL unchanged | YouTube |
| SoundCloud URL | URL unchanged | SoundCloud |
| Unsupported URL | rejected | none |

SoundCloud remains available for explicit direct links. It is no longer the default search provider.

## Hidden fallbacks

Search result playlists keep up to nine deduplicated hidden candidates. The visible queue still contains one user request. A failed YouTube candidate advances to the next YouTube candidate; the bot does not silently switch the requested recording to a SoundCloud remix.

## User-visible diagnostics

Load confirmations, `/now`, and `/queue` show the detected source provider. Startup logs contain:

```text
Modern YouTube source ready: engine=lavalink-devs/youtube-source version=1.18.2
```

This makes it possible to distinguish Discord voice transport failures from provider/extractor failures.

## Compatibility boundary

`v0.11.3` deliberately keeps the known-green platform line:

- Java 17;
- Spring Boot 3.4.3;
- JDA 6.5.0;
- LavaPlayer 2.2.3;
- native libDAVE `ce725965e`.

Production proved that LavaPlayer's embedded extractor returns HTTP `400` for `ytsearch:`.
`v0.11.4` therefore replaces it with `dev.lavalink.youtube:v2:1.18.2`; see
[`MODERN-YOUTUBE-SOURCE.md`](MODERN-YOUTUBE-SOURCE.md).
