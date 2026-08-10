# Playback Source Abstraction & Resolver

`v1.26.0` вводит явную границу между логическим музыкальным треком и transport-источником, через который конкретный клиент может его воспроизвести.

## Главный инвариант

```text
Recommendation / Mix Engine
        ↓
    TrackIdentity
        ↓
  PlaybackResolver
        ↓
PlaybackSourceReference[]
        ↓
client-specific transport
```

`TrackIdentity` не знает URL, provider id и search-prefix. Эти детали появляются только в `PlaybackSourceProvider` после того, как recommendation engine уже выбрал песню.

## Client capabilities

`PlaybackClientCapabilities` описывает текущего клиента и допустимые providers. Поэтому один логический трек может иметь разный transport plan для Discord, Android и Web без изменения recommendation model.

Текущая Discord policy:

```text
YouTube search   priority 100   primary
SoundCloud search priority 200  secondary candidate
```

`v1.27.0` добавляет поверх этого ordering runtime provider health и automatic fallback. Активный cooldown исключает provider из новых resolutions, а после cooldown он возвращается как probe. Подробности: `docs/PROVIDER-HEALTH-FALLBACK.md`.

## Provider boundary

В v1.26 есть два search-provider adapter:

- `YoutubePlaybackSourceProvider` → `ytsearch:`;
- `SoundCloudPlaybackSourceProvider` → `scsearch:`.

Recommendation/catalog classes не импортируют эти transport identifiers.

## Где resolver применяется сейчас

Smart Radio / curated Mix выбирают `RecommendationCandidate`, получают его `TrackIdentity`, затем вызывают:

```text
playbackResolver.resolve(trackIdentity, PlaybackClientCapabilities.discord())
```

и передают primary `PlaybackSourceReference.identifier()` существующему LavaPlayer transport path.

Ручные `/play`, `/search` и прямые URL намеренно остаются на `MediaQueryResolver`: если пользователь явно указал источник, Baskov не должен самовольно заменять его другим provider.

## Persistence

Нового storage нет. Поля старых library/session records:

```text
playbackIdentifier
sourceIdentifier
provider
```

остаются совместимыми. `PlaybackResolver` — runtime boundary, а не новая persisted identity model.

## Resilience layer

`v1.27` реализует provider health/fallback поверх этой границы: technical failures открывают bounded cooldown, `noMatches` остаётся track-specific miss, Smart Radio последовательно пробует уже разрешённые resolver candidates, а `/doctor source` показывает runtime health без network probe.
