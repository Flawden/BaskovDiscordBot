# Collaborative Recommendation Signals — v1.18.0

## Цель

Добавить к локальной модели вкуса второй независимый сигнал: collective listening graph. Этот слой не генерирует playback identifiers и не управляет Discord voice.

## Pipeline

```text
StoredTrack seed
  ├─ Last.fm track.getSimilar -> RecommendationCandidate[]
  └─ ListenBrainz
       metadata lookup artist/title -> artist MBID
       lb-radio/artist/{mbid} -> similar artist listening graph

RecommendationRanker
  provider similarity
+ novelty / diversity
+ personal track/artist/tag affinity
+ 64D taste-vector cosine
+ collaborative artist affinity
= final candidate

candidate -> ytsearch: -> existing load/policy/queue/playback
```

## Fail-open

`LISTENBRAINZ_TOKEN` optional. Если token отсутствует, lookup не нашёл MBID, запрос превысил bounded timeout, upstream вернул ошибку или payload не распарсился, collaborative contribution становится нулевым. Last.fm и локальная модель продолжают работу.

## Bounds

- общий discovery HTTP timeout: `DISCORD_BOT_DISCOVERY_REQUEST_TIMEOUT`;
- similar artists: `DISCORD_BOT_COLLABORATIVE_ARTIST_LIMIT`, 3..50, default 12;
- ListenBrainz radio mode: `easy|medium|hard`, default `medium`;
- in-memory cache: 256 seed entries, TTL 30 minutes;
- никаких новых persistent files.

## Secrets / delivery

Optional secret:

```text
LISTENBRAINZ_TOKEN
```

Optional vars:

```text
LISTENBRAINZ_API_BASE_URL=https://api.listenbrainz.org
DISCORD_BOT_COLLABORATIVE_ARTIST_LIMIT=12
DISCORD_BOT_LISTENBRAINZ_RADIO_MODE=medium
```

Token используется только в `Authorization: Token ...` для ListenBrainz metadata lookup и не попадает в recommendation context, Discord embeds или persistence.

## Explainability

Материальный вклад отображается в `/radio why`:

```text
collaborative +74% via ListenBrainz
```

`/radio model` показывает `ListenBrainz ON/OFF`.
