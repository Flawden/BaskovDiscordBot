# Modern YouTube Source

## Production root cause

`v0.11.3` correctly routed plain text to `ytsearch:`, but LavaPlayer `2.2.3`
still registered its embedded deprecated YouTube extractor. Production then failed
before track selection with:

```text
YoutubeSearchProvider
Invalid status code for search response: 400
```

Discord voice, native DAVE and Docker networking were already healthy.

## Engine

`v0.11.4` adds the Lavaplayer 2 integration of
`lavalink-devs/youtube-source`:

```text
dev.lavalink.youtube:v2:1.18.2
```

The dependency is resolved from:

```text
https://maven.lavalink.dev/releases
```

The source uses its released default multi-client order:

```text
MUSIC → ANDROID_VR → WEB → WEBEMBEDDED
```

A failed InnerTube client can therefore fall through to another implementation
instead of depending on one legacy request shape.

## Registration order

The modern source is registered explicitly first. LavaPlayer then registers the
remaining remote sources while excluding its embedded legacy
`com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager`.

```text
modern youtube-source
→ remaining remote providers except legacy YouTube
```

SoundCloud remains available for explicit direct links. Plain text still resolves
to `ytsearch:`.

## Runtime gate

Startup emits:

```text
Modern YouTube source ready:
engine=lavalink-devs/youtube-source
version=1.18.2
clients=MUSIC,ANDROID_VR,WEB,WEBEMBEDDED
legacyLavaplayerYoutube=disabled
```

Production deployment requires this marker in addition to the native libDAVE
marker. `/status` shows:

```text
Основной поиск: YouTube
YouTube engine: youtube-source 1.18.2
```

Rollback remains compatible with the previous image because rollback verification
intentionally skips new-release startup markers.

## Authentication boundary

This release does not enable YouTube OAuth or poToken support and introduces no
account credentials, secrets or environment variables. The released default
unauthenticated clients are used first. Authentication is a separate operational
decision only if production later returns an explicit sign-in or bot-verification
error.

## Autocomplete

Discord autocomplete can expire while the user continues typing. Error `10062`
is now handled by an explicit failure callback and logged only at debug level,
instead of producing a large production stack trace.
