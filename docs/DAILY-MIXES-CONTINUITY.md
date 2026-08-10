# Daily Mixes & Station Continuity

Начиная с v1.22.0 product-level `/mix` получил два daily preset и явное bounded продолжение станции.

## Daily stations

- `daily-mix` — `personal/similar`;
- `daily-discoveries` — `personal/discovery` с неизменным hard novelty.

`DailyMixSeedPlanner` получает уже bounded personal seed pool и детерминированно переставляет его по ключу `guildId:userId:station:date`. В daily-выпуск попадает максимум 8 seed-треков. Planner не загружает audio, не знает о queue/voice и не пишет состояние на диск.

Одинаковые user/station/date дают одинаковый seed order. Новый календарный день runtime меняет salt и естественно вращает исходный набор. Это стабилизирует направление выпуска, но не превращает recommendation result в заранее сохранённый плейлист: Last.fm/local fallback, personal ranking, session, vectors, collaborative signal и bandit продолжают выбирать фактический следующий кандидат.

## Continuity

`/mix resume` использует process-local continuity с TTL 36 часов. При остановке/переключении curated station сохраняются:

- station;
- daily seed date;
- seed cursor;
- generated count;
- recent track keys/identities;
- recent artist cooldown;
- optional theme focus;
- ordered recent mix artists + recent tag windows для diversity-control;
- последняя recommendation metadata.

Resume создаёт новый active `RadioState` из этого snapshot и продолжает прежний seed cursor, theme focus и anti-repeat/diversity context. Для daily station сохраняется исходная дата выпуска: если `Микс дня` от 2026-08-10 остановили и продолжили уже 2026-08-11, `/mix resume` продолжит выпуск 2026-08-10. Новый явный `/mix start station:daily-mix` создаст уже выпуск новой даты.

## Safety boundary

Continuity не является persistence и не переживает restart/deploy. При shutdown map очищается, музыка автоматически не стартует. Новых файлов, secrets или migration нет. Playback остаётся существующим `ytsearch:` → LavaPlayer → queue/voice pipeline, а `daily-discoveries` проходит hard novelty до всех learned ranking signals.
