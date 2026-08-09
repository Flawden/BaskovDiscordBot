# Smart Discovery Engine — v1.14.0

## Boundary

Recommendation layer не воспроизводит музыку и не управляет Discord voice. Его результат — только `artist`, `title`, `similarity`, `source`, `reason`.

```text
local seed
  -> Last.fm track.getSimilar (optional)
  -> RecommendationRanker
  -> novelty / artist cooldown
  -> RecommendationCandidate
  -> ytsearch:artist title
  -> existing PlayerManager / TrackScheduler
```

## Strategies

### familiar

Внешний provider не нужен. Используется локальный seed, как в v1.13.0.

### similar

Provider similarity — главный сигнал. Known tracks допускаются, но получают repetition penalty; recent radio tracks исключаются.

### discovery

Known tracks из retained guild history, personal history/favorites и recent radio history hard-reject-ятся по normalized `artist + title`. Последние 3 radio artists участвуют в diversity cooldown.

## Last.fm

Runtime интеграция опциональна и включается только если `LASTFM_API_KEY` непустой. HTTP timeout bounded, candidate count bounded. Ошибка/timeout/пустой ответ не ломают playback и приводят к local fallback.

Настройки:

```text
LASTFM_API_KEY
LASTFM_API_BASE_URL=https://ws.audioscrobbler.com/2.0/
DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT=3s
DISCORD_BOT_DISCOVERY_CANDIDATE_LIMIT=25
```

## Privacy / secrets

API key используется только для HTTP request и не включается в `RecommendationCandidate`, `/radio why`, logs или persistent music/session storage. Recommendation state остаётся ephemeral вместе со smart-radio state.

## Future roadmap

`v1.15` сможет добавить implicit recommendation feedback, `v1.16` — personal ranking weights, а дальнейшая модель сможет заменить/дополнить provider, не меняя playback pipeline.
