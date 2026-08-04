# Discord DAVE Voice Migration

## Production evidence

Production voice diagnostics captured the following deterministic sequence on the old JDA voice stack:

```text
Self voice JOIN
Audio WebSocket closed by remote
Close code: 4017
Reason: E2EE/DAVE protocol required
Resume attempted
Close code: 4006
Reason: Session is no longer valid
Self voice LEAVE
```

The same behavior was reproduced in Docker `bridge` and `host` network modes. This excluded Docker bridge/NAT as the primary cause and identified Discord voice protocol negotiation as the failing layer.

## Migration history

The first conservative migration moved JDA `5.3.0 → 5.6.1`. It improved diagnostics and preserved source compatibility, but production still left voice before the first audio frame.

Version `0.9.0` performs the isolated source migration:

```text
JDA 5.6.1 -> 6.5.0
```

The following remain pinned:

```text
Java 17
Spring Boot 3.4.3
LavaPlayer 2.2.3
Lombok 1.18.36
Maven Compiler Plugin 3.13.0
```

JDA `6.5.0` is the exact JDA 6 line already observed in this repository's Dependabot candidate. The migration intentionally does not combine Spring Boot 4, LavaPlayer updates or GitHub Actions major updates.

## Source migration

JDA 6 moved message components out of the old interaction package:

```text
net.dv8tion.jda.api.interactions.components.*
->
net.dv8tion.jda.api.components.*
```

`ActionRow`, `LayoutComponent` and `Button` are migrated together so slash responses, interaction edits and legacy message buttons share the same JDA 6 component model.

## Playback confirmation

A decoded LavaPlayer track does not prove that Discord voice media is alive. The bot keeps the two-phase readiness gate:

```text
TRACK_LOADED
-> wait for a new AudioSendHandler.canProvide() call
-> PLAYBACK_CONFIRMED
```

If Discord leaves voice before the first frame request, or frame polling does not begin before the readiness timeout, the temporary message is replaced with a transport failure and the orphaned session is released.

## Runtime evidence

`/status` displays the loaded JDA version. Startup logs include the same value:

```text
JDA is ready: version=6.5.0, status=CONNECTED, ...
```

The expected healthy sequence is:

```text
Self voice JOIN
DAVE/E2EE voice handshake remains open
Track loaded
Audio frame polling begins
Playback confirmed
```

## Smoke test

1. Deploy in normal `bridge` mode.
2. Run `/version` and verify `v0.9.0`.
3. Run `/status` and verify `JDA: 6.5.0`.
4. Run `/play green day`.
5. The first response must say that the track is loaded and voice media is being checked.
6. It may change to `Воспроизведение подтверждено` only after frame polling begins.
7. Confirm that the bot remains in voice for at least 90 seconds.
8. Confirm that logs do not contain close code `4017` or `E2EE/DAVE protocol required`.
9. Confirm that `Frame polling` in `/status` is no longer `never`.

## Native implementation (v0.9.4)

JDA 6 alone is insufficient: its default `PassthroughDaveSessionFactory` reports maximum protocol version `0`, which Discord rejects with close code `4017`. Release v0.9.4 installs `libdave-jvm ce725965e`, validates the JNI runtime before JDA startup and injects `LDJDADaveSessionFactory` through `AudioModuleConfig`.

See [`NATIVE-DAVE.md`](NATIVE-DAVE.md) for platform profiles, fail-fast behavior and smoke checks.
