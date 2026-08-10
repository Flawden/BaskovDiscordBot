# Mix Generation & Diversity Control

Начиная с v1.23.0 curated `/mix`-станции управляют не только качеством следующего кандидата, но и качеством последовательности треков.

## Diversity context

`MixDiversityProfile` живёт только внутри активного `RadioState` и содержит:

- до 6 последних mix-исполнителей в реальном порядке воспроизведения;
- до 6 последних непустых tag-наборов;
- optional `themeFocus` для динамического тематического микса.

Manual `/radio` оставляет этот слой выключенным. `Знакомое` также намеренно не включает жёсткий diversity-control, чтобы маленькая favorite/history библиотека одного исполнителя не переставала работать.

## Artist diversity

Curated mix использует три рубежа:

1. `MixSeedDiversityPlanner` round-robin разводит seed по исполнителям, не удаляя треки;
2. `MixDiversityPolicy` hard-reject-ит immediate повтор исполнителя и даёт bounded penalty за повторное появление артиста в коротком окне;
3. финальный YouTube transport result повторно проходит `blocksArtistForMix(...)`, потому что фактический `ytsearch:` result может отличаться metadata от provider candidate.

Таким образом один сильный artist affinity не должен превращать микс в восемь песен одного исполнителя подряд.

## Tag diversity

Для кандидата вычисляется максимальная доля его tags среди последних tagged mix-tracks. При насыщении одного направления применяются bounded penalties. Отсутствие tags нейтрально: metadata failure не блокирует radio.

Last.fm по-прежнему является optional candidate/tag provider. В v1.23 bounded tag enrichment увеличен до top-5 candidates; timeout/error остаются fail-open.

## Dynamic theme station

Команды:

```text
/mix themes
/mix start station:theme theme:pop punk
```

`/mix themes` берёт только положительные значения из `PersonalTasteProfile.tagAffinity`, то есть из уже существующего `recommendation-feedback.tsv` V2. Option `theme` поддерживает autocomplete; если пользователь запускает `station:theme` без значения, используется strongest positive tag. Если положительного tag evidence ещё нет, команда предлагает указать theme вручную или накопить feedback.

Theme match даёт bounded ranking bonus. Кандидат с metadata tags, не совпадающими с focus, получает небольшой penalty. При отсутствии tags theme contribution равен нулю.

## Safety ordering

Порядок принципиален:

```text
recent identity rejection
        ↓
hard novelty (`discovery`)
        ↓
mix immediate-artist guard
        ↓
personal/session/vector/collaborative/bandit/theme/diversity scoring
        ↓
ytsearch transport guard
```

Theme/diversity не могут воскресить known track в `discovery`, обойти queue/voice permissions или загружать audio напрямую.

## Continuity and persistence

`/mix resume` переносит в process-local continuity:

- station и daily seed date;
- optional theme focus;
- seed cursor;
- recent track/artist anti-repeat memory;
- ordered recent mix artists;
- recent tag windows;
- generated count и последнюю recommendation metadata.

TTL остаётся 36 часов. Restart/deploy очищает continuity и active mix. Новых persistence-файлов, env vars, secrets или migration в v1.23.0 нет.
