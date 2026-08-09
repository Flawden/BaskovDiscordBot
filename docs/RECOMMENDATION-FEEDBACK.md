# Recommendation Feedback — v1.15.0

`v1.15.0` превращает Smart Discovery из односторонней выдачи рекомендаций в замкнутый feedback loop.

## Что сохраняется

Отдельный atomic store:

```text
data/recommendation-feedback.tsv
BASKOV_RECOMMENDATION_FEEDBACK_V1
```

На пользователя хранится максимум 200 recommendation records. Запись содержит:

- seed artist/title;
- фактически воспроизведённый radio candidate;
- provider и radio strategy;
- similarity исходного candidate plan;
- timestamp рекомендации;
- последний outcome;
- completion ratio;
- positive/negative signal counters;
- накопленный feedback score.

Store включён в persistence readiness и periodic ZIP backup вместе с guild settings, music library и music sessions.

## Implicit signals

| Событие | Outcome | Вес |
|---|---|---:|
| трек естественно завершён | `COMPLETED` | +1 |
| добавлен в favorites | `FAVORITED` | +3 |
| повторён через `/replay` | `REPLAYED` | +2 |
| быстрый skip | `QUICK_SKIPPED` | -2 |
| быстрый stop | `QUICK_STOPPED` | -2 |
| удалён из favorites | `UNFAVORITED` | -3 |
| поздний skip/stop | `SKIPPED` / `STOPPED` | 0 |

Quick negative = не более 30 секунд **или** не более 20% длительности.

Только radio-generated requester `📻 Radio` автоматически получает playback completion/skip feedback. Обычные ручные треки не загрязняют recommendation dataset.

## Команда

```text
/radio feedback
```

Показывает последние 10 рекомендаций текущего пользователя и aggregate +/- signals. `/radio why` остаётся объяснением последнего выбора, а `/radio feedback` — историей того, насколько удачными были прошлые выборы.

## Архитектурный принцип

Feedback не вмешивается в playback transport. Ошибка записи feedback логируется, но не должна блокировать `TrackScheduler` или voice.

`v1.16.0 Personal Ranking Model` сможет использовать этот журнал как входные данные для персональных весов, artist/tag affinity и exploration/exploitation.
