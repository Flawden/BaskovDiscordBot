# Contextual Bandit & Exploration Learning

Начиная с v1.20.0 smart radio использует лёгкую online-policy поверх существующего hybrid ranker. Цель слоя — не заменить similarity/personal/vector/collaborative ranking, а научиться выбирать подходящий уровень риска для конкретного пользователя.

## Arms

Кандидат получает arm только по provider similarity:

- `safe` — similarity >= 0.82;
- `balanced` — 0.62 <= similarity < 0.82;
- `bold` — similarity < 0.62.

Границы детерминированы и не зависят от provider ID или playback URL.

## Откуда берётся обучение

Нового persistence-файла нет. `ContextualBanditModel` пересобирает profile из `recommendation-feedback.tsv` V2. Каждая завершённая recommendation уже содержит strategy, similarity и накопленный `signalScore`, поэтому arm можно восстановить ретроспективно.

`PENDING` записи не обучают policy. Reward нормализуется в [-1; 1], после чего statistics считаются отдельно для каждой `RadioStrategy` и каждого arm.

## Decision

Bandit contribution складывается из:

1. learned mean reward arm;
2. bounded uncertainty bonus (UCB-style);
3. небольшого prior текущей radio strategy;
4. краткосрочного session momentum.

Итоговый вклад жёстко ограничен диапазоном `[-0.12; +0.12]`.

Hard novelty/recent-track rejection выполняется раньше bandit scoring. Следовательно, online-policy не может вернуть известный или recent track в `discovery`.

## Session context

Если текущая personal-radio session получает серию отрицательных сигналов, `bold` arm временно получает небольшой contextual boost, чтобы выйти из неудачного локального направления. При устойчиво положительном momentum `safe` получает небольшое преимущество.

Это изменение эфемерно: session context сбрасывается при новом `/radio start` и restart/deploy. Long-term arm statistics продолжают восстанавливаться из durable feedback.

## Discord UX

`/radio bandit` показывает:

- preferred arm для `similar` и `discovery`;
- samples каждого arm;
- средний reward;
- confidence policy;
- текущий session momentum/confidence.

`/radio why` добавляет bandit contribution только когда он materially влияет на выбранного кандидата.

## Playback boundary

Bandit работает только с `RecommendationCandidate` metadata. После выбора трека остаётся прежний путь:

`RecommendationCandidate -> ytsearch: -> load -> policy -> queue -> playback`.

Bandit code не зависит от LavaPlayer/JDA voice и не управляет очередью напрямую.
