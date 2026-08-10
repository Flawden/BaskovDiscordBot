# Mixes & Personalized Stations

Начиная с v1.21.0 Discord UX получает product-level слой `/mix` поверх существующего smart radio. Цель — дать пользователю готовые музыкальные сценарии и не заставлять его вручную выбирать внутренние `mode`/`strategy`.

## Команды

```text
/mix list
/mix start station:my-mix
/mix start station:discoveries
/mix start station:familiar
/mix start station:mood
/mix status
/mix stop
```

## Station mapping

| Station | Radio mode | Strategy | Seed policy |
|---|---|---|---|
| Мой микс | personal | similar | favorites + personal history |
| Открытия | personal | discovery | favorites + personal history; hard novelty |
| Знакомое | personal | familiar | favorites + personal history |
| Настроение сейчас | personal | similar | до 12 самых свежих personal-history seed |

`Настроение сейчас` не хранит отдельный mood-profile. После старта уже существующий `AdaptiveSessionModel` строит ephemeral taste только по feedback текущего запуска. Если свежей personal history нет, station использует обычный bounded personal seed pool.

## Architecture boundary

`PersonalizedStation` содержит только metadata preset: slug, label, description, `RadioStrategy` и bounded seed hint. Он не знает о `AudioTrack`, LavaPlayer, JDA voice, queue или HTTP providers.

`PlayerManager.startStation(...)` делегирует в тот же `startRadioInternal(...)`, что и manual `/radio start`. Поэтому сохраняются:

- voice/channel access policy;
- requester/manager permission model;
- one-candidate refill;
- human-first activity-version race guard;
- queue limits и playback readiness;
- `ytsearch:` transport boundary;
- fail-open recommendation providers;
- три consecutive refill failures → radio OFF.

## Manual radio vs curated station

Manual `/radio start` хранится как `PersonalizedStation.CUSTOM`. Curated `/mix start` хранит конкретный station только в ephemeral `RadioState`.

Это позволяет `/mix status` отличать готовый продуктовый preset от ручной инженерной конфигурации и не создаёт нового persistence.

## Persistence

Новых файлов и форматов нет. Продолжают использоваться:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv (V2)
```

Active mix выключается после restart/redeploy вместе с остальным ephemeral radio-state. Long-term feedback, personal ranking, 64D taste-vector и contextual bandit затем восстанавливаются из прежних durable данных.
