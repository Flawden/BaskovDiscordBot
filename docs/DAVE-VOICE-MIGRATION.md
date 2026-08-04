# Discord DAVE Voice Migration

## Incident evidence

Production voice diagnostics captured the following deterministic sequence on JDA 5.3.0:

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

The same behavior was reproduced in Docker `bridge` and `host` network modes. This excludes the bridge/NAT layer as the primary cause and identifies Discord voice protocol negotiation as the failing layer.

## Migration boundary

Version 0.8.0 changes only the Discord library line:

```text
JDA 5.3.0 -> 5.6.1
```

The following remain pinned:

```text
Java 17
Spring Boot 3.4.3
LavaPlayer 2.2.3
Lombok 1.18.36
```

The JDA version is the exact 5.x upgrade already proposed by Dependabot in this repository. JDA 6 remains a separate source migration because its components API is incompatible with the current code.

## Playback confirmation

A successfully decoded LavaPlayer track does not prove that Discord voice media is alive. The bot now uses two phases:

```text
TRACK_LOADED
-> wait for a new AudioSendHandler.canProvide() call
-> PLAYBACK_CONFIRMED
```

If the bot leaves voice before the first frame request, or Discord never polls frames before the readiness timeout, the success message is replaced with a transport failure and the orphaned session is released.

## Runtime evidence

`/status` now displays the loaded JDA version. Startup logs include the same value:

```text
JDA is ready: version=5.6.1, status=CONNECTED, ...
```

The expected healthy `/play` sequence is:

```text
Voice JOIN
DAVE/media handshake remains open
Track loaded
Audio frame polling begins
Playback confirmed
```

## Smoke test

1. Deploy in normal `bridge` mode.
2. Run `/version` and verify `v0.8.0`.
3. Run `/status` and verify `JDA: 5.6.1`.
4. Run `/play green day`.
5. The first message must say that the track is loaded and transport is being checked.
6. It may change to `Воспроизведение подтверждено` only after frame polling starts.
7. Confirm that the bot remains in voice for at least 90 seconds.
8. Confirm that logs no longer contain close code `4017`.
