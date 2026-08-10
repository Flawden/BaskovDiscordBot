# Provider Health & Automatic Fallback

`v1.27.0` добавляет resilience поверх `PlaybackResolver`, не смешивая logical track identity с provider transport.

## Flow

```text
RecommendationCandidate
        ↓
    TrackIdentity
        ↓
  PlaybackResolver
        ↓
health-aware ordered candidates
        ↓
YouTube → technical failure/no match
        ↓
SoundCloud fallback
```

Recommendation engine не вызывается повторно при fallback: меняется только transport source для уже выбранного `TrackIdentity`.

## Runtime health states

- `HEALTHY` — нет consecutive technical failures;
- `DEGRADED` — есть недавний technical failure, но threshold ещё не достигнут;
- `COOLDOWN` — threshold достигнут, provider временно исключён из новых resolutions;
- `PROBE` — cooldown закончился, provider снова допускается с исходным priority; успешная загрузка возвращает `HEALTHY`, новый technical failure снова открывает cooldown.

Default policy:

```text
failure threshold = 3
cooldown = 90s
```

Настройки:

```text
DISCORD_BOT_PLAYBACK_PROVIDER_FAILURE_THRESHOLD
DISCORD_BOT_PLAYBACK_PROVIDER_COOLDOWN
```

## Failure semantics

`loadFailed` считается technical provider failure и влияет на circuit breaker.

`noMatches` означает, что площадка ответила, но не нашла конкретный track. Это запускает fallback, но не увеличивает consecutive technical failures.

Успешный `trackLoaded`/`playlistLoaded` закрывает circuit и сбрасывает consecutive failures.

## All providers cooling down

Если все client-supported providers находятся в `COOLDOWN`, resolver не возвращает фальшивый candidate. `PlaybackResolution.retryAfter` содержит ближайшее время probe, а Smart Radio отменяет текущий refill-in-progress и планирует повтор после этого delay вместо трёх мгновенных refill failures.

## Explicit source isolation

Автоматический resolver/fallback применяется к system-selected recommendation transport (`/radio`, curated `/mix`).

Явный пользовательский `/play`/`/search`/URL остаётся на explicit source path. Baskov не заменяет площадку, которую пользователь указал вручную.

## Diagnostics

`/doctor source` показывает runtime provider state и counters `success/failure/miss/fallback`. Никакой live HTTP probe не выполняется — health выводится из реальных playback events.

## Persistence

Provider health process-local. Не появляются новые storage-файлы и не меняются форматы:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv V2
```

Restart/deploy намеренно сбрасывает circuit state.
