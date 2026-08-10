# Personalized Home / Music Hub

`v1.24.0` добавляет первый client-neutral product surface Baskov Music.

## Команда

```text
/home
```

Discord показывает один ephemeral hub с:

- active station или resumable continuity;
- `Микс дня` и `Открытия дня`;
- `Мой микс`, `Настроение сейчас`, `Открытия`, `Знакомое`;
- positive themes из существующего recommendation feedback V2;
- counters favorites и personal history;
- bounded preview последних personal tracks;
- evidence/confidence долгосрочного taste-profile.

## Client-neutral read model

Главный объект релиза — `HomeSnapshot`.

```text
MusicHomeService
      |
      v
MusicHomeReadPort
      |
      v
RuntimeMusicHomeReadAdapter
      |
      +-- PlayerManager
      +-- MusicLibraryRepository
      +-- RecommendationFeedbackService
```

`HomeSnapshot` и `MusicHomeService` не импортируют JDA, `EmbedBuilder`, slash events или Discord entities. `ModernInteractions` получает готовый snapshot и только отображает его.

Это намеренная архитектурная подготовка к будущему Product API:

```text
                   MusicHomeService
                         |
                    HomeSnapshot
                         |
             +-----------+-----------+
             |                       |
        Discord embed           future API DTO
                                     |
                              Android / Web
```

## Read-only invariant

Home не является playback orchestrator.

Он не должен:

- вызывать `startStation`/`stopRadio`;
- изменять queue;
- писать recommendation feedback;
- создавать собственный persistence;
- резолвить или загружать media source.

Карточка `available` только сообщает, достаточно ли текущих favorites/history для seed-based station. Реальное действие по-прежнему выполняется существующими `/mix` командами и их permission/voice policy.

## Persistence

Новых файлов нет. Используются существующие:

```text
guild-settings.properties
music-library.tsv
music-sessions.tsv
recommendation-feedback.tsv V2
```

Home snapshot строится на запрос и не сохраняется отдельно.

## Будущее

`HomeSnapshot` специально не является HTTP DTO и не вводит API преждевременно. В planned Product API release он станет источником semantic data для `GET /api/v1/home`, после чего Discord, Android и Web смогут рендерить одно и то же состояние разными UI.
