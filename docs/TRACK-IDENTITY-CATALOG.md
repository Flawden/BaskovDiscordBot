# Track Identity & Catalog Foundation

`v1.25.0` отделяет логическое понятие музыкального трека от места, через которое он сейчас воспроизводится.

## Главный инвариант

```text
TrackIdentity = что это за трек
Playback source = откуда конкретный клиент может его сыграть
```

`TrackIdentity` содержит display artist/title, normalized artist/title и стабильный application-level key. Он намеренно не содержит YouTube video id, SoundCloud URL, `MediaProvider`, `ytsearch:` или другие transport details.

Стабильный ключ сохраняет прежнюю semantics recommendation novelty/feedback:

```text
normalizedArtist::normalizedTitle
```

Поэтому `RecommendationIdentity` в v1.25 остаётся deprecated compatibility facade и делегирует в `TrackIdentity`; существующий recommendation feedback не требует миграции.

## Catalog metadata

`TrackExternalId` поддерживает authoritative catalog identifiers:

- `MUSICBRAINZ_RECORDING`;
- `ISRC`.

YouTube и SoundCloud сюда принципиально не входят: это playback providers, а не authoritative identity namespaces.

`TrackCatalogEntry` объединяет:

```text
TrackIdentity
+ external catalog ids
+ descriptive tags
```

Last.fm `track.getSimilar` уже может вернуть `mbid`; v1.25 сохраняет его в `RecommendationCandidate` как MusicBrainz recording id. Tag enrichment сохраняет catalog ids.

## StoredTrack compatibility

`StoredTrack` получает `trackIdentity()` и `catalogEntry()`, но его текущие persisted transport поля пока не удаляются:

```text
playbackIdentifier
sourceIdentifier
provider
```

Это сознательная compatibility стадия. `music-library.tsv` и `music-sessions.tsv` не мигрируют в v1.25.

## Что будет дальше

`v1.26` — `Playback Source Abstraction & Resolver`.

Там ожидаемая граница будет выглядеть так:

```text
TrackIdentity
    ↓
PlaybackResolver
    ↓
PlaybackCandidate[]
    ↓
client/provider-specific transport
```

`PlaybackResolver` не вводится раньше времени в v1.25: сначала система должна иметь стабильное provider-neutral понятие самого трека.
