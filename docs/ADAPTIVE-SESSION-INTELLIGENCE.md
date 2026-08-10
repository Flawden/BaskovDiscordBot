# Adaptive Session Intelligence — v1.19.0

`v1.19.0` добавляет второй горизонт персонализации поверх durable taste model.

## Два горизонта вкуса

- **Long-term**: `PersonalTasteProfile`, 64D taste-vector и feedback V2 переживают restart/deploy.
- **Session**: `SessionTasteProfile` существует только для текущего explicit `/radio start` и строится из feedback, возникшего после его старта.

Это позволяет глобально любить один набор жанров, но временно сместить ranking под настроение конкретной сессии без загрязнения постоянного профиля.

## Session scoring

`AdaptiveSessionModel` использует максимум 20 рекомендаций текущей сессии. Более свежие записи получают больший recency weight. Track, artist и tag affinity объединяются в bounded session score; confidence растёт быстрее долгосрочного профиля, потому что краткосрочная модель должна реагировать за несколько треков.

Отрицательный session momentum создаёт небольшой exploration pressure только для ещё неизвестных track+artist. Положительный momentum не отменяет novelty/diversity и не разрешает известным трекам обходить `discovery` hard filter.

## Безопасность архитектуры

Session-layer:

- не создаёт нового persistence-файла;
- не меняет `recommendation-feedback.tsv` V2;
- не используется для `server` radio;
- не знает о LavaPlayer/Discord voice;
- не может обойти recent/known rejection;
- исчезает после нового `/radio start`, `/radio stop` и restart/deploy.

Playback boundary остаётся: recommendation metadata → `ytsearch:` → load → policy → queue → playback.

## Discord UX

`/radio session` показывает session start, evidence/confidence, momentum и strongest artist/tag affinity текущего personal-radio. `/radio model` остаётся долгосрочным профилем, а `/radio why` при material влиянии добавляет session contribution.
