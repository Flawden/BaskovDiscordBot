# 🎤 Baskov Discord Bot

Текущая версия релизной ветки: **v1.37.0**.

Музыкальный Discord-бот на Java 17, Spring Boot, JDA, LavaPlayer и native libDAVE.

## Возможности

- slash-команды воспроизведения и полноценного управления очередью;
- Smart Discovery Engine: `/radio start|status|why|feedback|model|session|stop` поддерживает `familiar/similar/discovery`; Last.fm генерирует похожих кандидатов при наличии `LASTFM_API_KEY`, а discovery жёстко фильтрует уже знакомые треки и передаёт выбранный `TrackIdentity` в client-aware playback resolver;
- Recommendation Model / Embeddings Foundation: Personal Ranking Model дополнен локальным `feature-hash-v1` 64D embedding-provider, taste-vector и cosine similarity; `/radio model` показывает vector confidence/coverage, а `/radio why` объясняет отдельный vector-вклад. Интерфейс embedding-provider отделён от playback и готов к будущему semantic provider без изменения `ytsearch:` тракта;
- Collaborative Signals: optional ListenBrainz artist graph добавляет независимый collaborative-вклад к Last.fm + personal/vector ranker; без `LISTENBRAINZ_TOKEN` или при сбое upstream система fail-open продолжает старый recommendation pipeline;
- Adaptive Session Intelligence: текущее `personal` radio строит ephemeral short-term taste из feedback после последнего `/radio start`; `/radio session` показывает session momentum/confidence и strongest artist/tag affinity, а restart/new start намеренно сбрасывает краткосрочный слой без изменения durable feedback;
- Contextual Bandit & Exploration Learning: `/radio bandit` показывает online-policy `safe|balanced|bold`; модель учит reward каждого similarity-risk arm отдельно по стратегии из уже существующего feedback, учитывает session momentum и даёт только bounded вклад ±12%, не обходя hard novelty;
- Daily Mixes & Station Continuity: `/mix list|themes|start|resume|status|stop` включает «Микс дня» и «Открытия дня» со стабильным daily seed-набором, а bounded process-local continuity до 36 часов сохраняет station/date/seed cursor/anti-repeat memory для явного `/mix resume`; restart/deploy не автозапускает музыку и новых persistence-файлов нет;
- Mix Generation & Diversity Control: curated mix теперь разводит исполнителей и насыщенные tags на уровне seed/ranker/final transport, `/mix themes` строит положительные темы из feedback V2, а `station:theme` создаёт динамический тематический поток без нового persistence; hard novelty остаётся выше diversity/theme scoring;
- Personalized Home / Music Hub: `/home` собирает client-neutral `HomeSnapshot` с continuation, daily/for-you mix cards, positive themes, library counters, recent preview и taste maturity; Discord только рендерит snapshot, а domain/service не зависит от JDA и уже готов стать read-model будущего Product API/Android;
- Product API Boundary + Android Gateway: `MusicProductService` остаётся общей client-neutral application-границей; `BaskovUser`, Discord pairing proof и hashed device sessions дают authenticated reads, а `v1.30` добавляет `/api/v1/guilds`, JSON-safe string snowflakes, committed OpenAPI v1 и opt-in host-loopback deployment profile для TLS reverse proxy. playlist mutations в v1.36 и personal favorite mutations в v1.37 используют существующий Discord store и linked Discord identity, а voice/player mutations остаются выключены;
- Playback Source Abstraction & Provider Resilience: system-selected recommendation сначала превращается в provider-neutral `TrackIdentity`, затем `PlaybackResolver` строит client-aware YouTube/SoundCloud candidates; runtime health registry считает technical failures/misses/fallbacks, после 3 подряд технических ошибок открывает 90-секундный cooldown, автоматически переводит Smart Radio/Mix на следующий provider и после cooldown делает probe. Прямые `/play`/URL остаются explicit-source path без автоматической подмены; health process-local и не создаёт новый persistence;
- `/search` с пятью результатами YouTube, одноразовыми кнопками выбора и пятиминутной owner-bound сессией; autocomplete последних запросов работает в `/play` и `/search`;
- личное persistent избранное до 100 треков на пользователя и сервер через `/favorites list|add|play|play-all|remove|search|clear`; favorites участвуют в локальном autocomplete и используют существующий ordered batch playback;
- постоянная история до 50 треков на сервер плюс personal history до 200 заказанных и реально дошедших до истории треков на пользователя; `/history scope:server|mine`, `/replay scope:server|mine`, `/discover profile|for-me` и серверные плейлисты с owner/admin-управлением, autocomplete, lifecycle-операциями, поиском, capture queue и ordered batch playback;
- legacy `!`-команды как compatibility layer;
- воспроизведение музыки, пауза, остановка, пропуск и возврат к предыдущим трекам;
- requester, ETA для каждой позиции, постраничная очередь с кнопками навигации, bounded history, previous, repeat mode, shuffle, seek, remove/move/clear и управление громкостью;
- Queue Manager 2.0: ревизия ожидающей очереди, stale-safe batch mutations, `/queue-manage stats|remove-range|dedupe|remove-mine`, сводка длительности/заказчиков/дубликатов и self-service очистка собственных ожидающих треков;
- Queue Collaboration & Social UX: `/queue-manage mine|community|remove-own`, requester contribution summary, кнопки «Мои треки»/«Заказчики»/vote status и ownership-safe удаление одного своего трека с revision guard;
- постоянные guild settings: громкость, repeat, playback/request access, DJ/manager roles, voice-channel restriction и vote-skip;
- `/now` с визуальным прогрессом, state-aware двухрядным пультом previous/±15s/pause/next/shuffle/repeat, disabled-состояниями и кнопкой refresh; `/status` с активными playback modes, uptime, Discord gateway, voice transport snapshot и последними voice/source ошибками;
- Discord Experience 1.6: секционная `/help` с кнопочной навигацией, live refresh `/status` и одноразовые owner/guild-bound подтверждения для stop/clear/delete/reset;
- динамический Docker heartbeat, который подтверждает свежее подключение к Discord, считает gateway transitions/disconnected samples и показывает последнее CONNECTED;
- atomic persistence backups пяти storage-файлов внутри `/app/data/backups` с bounded retention и owner-only permissions;
- live storage probe в `/status`, агрегированный reliability state и command failure rate/последняя ошибка;
- JDA 6.5.0 с настоящей JNI libDAVE `ce725965e`, положительной protocol version и подтверждением playback только после реального запроса аудиофрейма Discord media transport;
- bounded voice recovery: отключённый JDA auto-reconnect, до трёх контролируемых повторных подключений с backoff и сохранением checkpoint при исчерпании попыток;
- Playback Sessions & Recovery 2.0: atomic checkpoint V2 сохраняет voice channel, текущий трек/позицию, очередь, pause, volume, repeat и bounded previous-history; `/session status` показывает guild-scoped recovery state, а `/session recover` даёт manager/admin безопасно повторить pending recovery;
- YouTube как основной провайдер текстового поиска через отдельный modern `youtube-source 1.18.2`; встроенный legacy extractor LavaPlayer отключён, а SoundCloud поддерживает прямые ссылки и выступает secondary search fallback для system-selected recommendation transport;
- переключаемый diagnostic network mode `bridge|host` для A/B-проверки Docker UDP/NAT;
- ограничения CPU, памяти, PID и ротация Docker-логов;
- раздельные политики playback и music requests, manager-role для администрирования, voice/stage restriction, аудит последних изменений и переносимые settings profiles;
- bounded queue, лимит длительности и автоматическое отключение пустых сессий;
- stateless-ядро команд с case-insensitive реестром и защитой от дубликатов;
- конфигурируемый префикс и cooldown для шумных команд;
- команда `!version` с версией, временем сборки и коротким Git revision;
- безопасная обработка ссылок SoundCloud/YouTube без сетевой проверки пользовательского URL; текстовый поиск использует `ytsearch:` и сохраняет резервные результаты того же провайдера;
- согласованные версии SLF4J/Logback под управлением Spring Boot BOM;
- автоматические ежемесячные проверки обновлений Maven и GitHub Actions;
- major-обновления Spring Boot, JDA и GitHub Actions блокируются Dependabot и выполняются только отдельными migration-релизами;
- контейнерный запуск;
- CI на стандартном Linux GitHub-hosted runner (`ubuntu-latest`), публикация immutable-образов в GHCR и автоматический деплой на VPS; deployment проверяет не только immutable SHA-tag, но и опубликованный OCI digest, а SSH-ключ живёт только во временном каталоге job.

## Локальный запуск

Требования: Java 17 и Discord bot token.

### Через Maven

```bash
export DISCORD_BOT_TOKEN='your-token'
export DISCORD_BOT_PREFIX='!'
./mvnw clean verify
./mvnw spring-boot:run
```

### Через Docker Compose

```bash
export DISCORD_BOT_TOKEN='your-token'
docker compose up -d --build
docker compose logs -f bot
```

По умолчанию боту не нужен входящий HTTP-порт: Product API отключён и Spring запускается в non-web режиме. Перед подключением к Discord выполняется fail-fast storage preflight для guild settings, music library, session checkpoint, recommendation feedback и `baskov-auth.tsv`; после подключения приложение обновляет readiness heartbeat каждые 10 секунд, а Docker считает контейнер healthy только при свежем `CONNECTED`-сигнале.


### Product API v1 + device pairing

`v1.30.0` использует identity/auth boundary Baskov Music и добавляет Android-facing gateway foundation. Base API по-прежнему отключён и non-web по умолчанию. Новый клиент сначала подтверждает Discord identity через `/device pair`, затем обменивает одноразовый код на device session:

```bash
export BASKOV_PRODUCT_API_ENABLED=true
export BASKOV_PRODUCT_API_WEB_APPLICATION_TYPE=servlet
export BASKOV_PRODUCT_API_BIND_ADDRESS=127.0.0.1
export BASKOV_PRODUCT_API_PORT=18080
./mvnw spring-boot:run
```

Pairing flow:

```text
Discord /device pair
→ one-time code
→ POST /api/v1/auth/device/pair
→ access + refresh token
```

`GET /api/v1/capabilities` остаётся публичным внутри opt-in API. `/api/v1/guilds`, `/api/v1/home|mixes|player|library`, `/api/v1/auth/me` и `/api/v1/auth/devices` требуют bearer token. Клиент сначала получает доступные guilds из backend, затем выбирает guild для Home/Mixes/Player/Library; user-scoped reads не принимают `userId` query parameter.

Plaintext access/refresh tokens не сохраняются: `baskov-auth.tsv` содержит только SHA-256 hashes. По умолчанию pairing code живёт 5 минут, access token — 30 минут, refresh token — 30 дней, максимум 8 активных устройств. Base Docker Compose не публикует API-порт наружу; отдельный opt-in profile может публиковать его только на host `127.0.0.1` для TLS reverse proxy.

Music mutation endpoints (`start/skip/favorite/...`) всё ещё выключены. `v1.32` отдельно добавляет authenticated foreground local stream `/api/v1/playback/stream`: Android передаёт только provider-neutral artist/title, backend выбирает playback provider и отдаёт Ogg/Opus, не меняя Discord queue/voice state.

`v1.35` добавляет authenticated read-only `/api/v1/search`: Android может передать текстовый запрос, получить до пяти provider-neutral кандидатов и затем запустить выбранный трек через тот же `/playback/stream` transport. Discord `/search` и Product API используют один LavaPlayer search pipeline.

### Настройки музыкальной сессии

| Переменная | По умолчанию | Назначение |
|---|---:|---|
| `DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE` | `100` | максимум ожидающих треков на сервер |
| `DISCORD_BOT_MUSIC_MAX_TRACK_DURATION` | `4h` | максимальная длительность одного трека |
| `DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT` | `5m` | отключение после опустошения очереди |
| `DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT` | `15s` | максимальное время одной попытки voice-подключения |
| `DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT` | `10s` | ожидание первого аудиофрейма после запуска трека |
| `DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN` | `30s` | пауза после неудачного подключения/transport failure |
| `DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE` | `5s` | допустимый краткий разрыв во время воспроизведения |
| `DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE` | `false` | `false` только наблюдает; `true` закрывает transport после подтверждённого timeout |
| `DISCORD_BOT_PLAYBACK_PROVIDER_FAILURE_THRESHOLD` | `3` | consecutive technical provider failures до открытия runtime circuit breaker |
| `DISCORD_BOT_PLAYBACK_PROVIDER_COOLDOWN` | `90s` | cooldown provider перед half-open/probe возвратом в resolver |
| `BOT_NETWORK_MODE` | `bridge` | `bridge` production default или `host` для диагностического A/B-теста |
| `DISCORD_BOT_VOICE_LOG_LEVEL` | `DEBUG` | узкий log level внутренних JDA audio-классов |
| `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` | `100` | громкость новой guild-сессии |
| `DISCORD_BOT_MUSIC_MAX_VOLUME` | `150` | верхняя граница команды `/volume` |
| `DISCORD_BOT_PERSISTENCE_FILE` | `data/guild-settings.properties` | файл постоянных guild-настроек; в Docker используется `/app/data/...` |
| `DISCORD_BOT_MUSIC_LIBRARY_FILE` | `data/music-library.tsv` | отдельный atomic-файл постоянных плейлистов, истории и личного избранного; в Docker `/app/data/music-library.tsv` |
| `DISCORD_BOT_MUSIC_SESSION_FILE` | `data/music-sessions.tsv` | atomic checkpoint активных voice/music-сессий; в Docker `/app/data/music-sessions.tsv` |
| `DISCORD_BOT_RECOMMENDATION_FEEDBACK_FILE` | `data/recommendation-feedback.tsv` | persistent bounded history результатов рекомендаций; в Docker `/app/data/recommendation-feedback.tsv` |
| `BASKOV_AUTH_FILE` | `data/baskov-auth.tsv` | users, external identity links и hashed device sessions; в Docker `/app/data/baskov-auth.tsv` |
| `BASKOV_AUTH_PAIRING_TTL` | `5m` | срок жизни одноразового Discord pairing code |
| `BASKOV_AUTH_ACCESS_TOKEN_TTL` | `30m` | срок жизни access token |
| `BASKOV_AUTH_REFRESH_TOKEN_TTL` | `30d` | срок жизни rotating refresh token |
| `BASKOV_AUTH_MAX_DEVICE_SESSIONS` | `8` | максимум активных device sessions на BaskovUser |
| `BASKOV_PRODUCT_API_REMOTE_ENABLED` | `false` | CI/CD opt-in для host-loopback Product API profile; raw port не становится public |
| `BASKOV_PRODUCT_API_HOST_PORT` | `18080` | VPS loopback port для reverse proxy при включённом remote profile |
| `DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL` | `5s` | период сохранения активной сессии |
| `DISCORD_BOT_MUSIC_SESSION_MAX_AGE` | `6h` | максимальный возраст checkpoint для автозапуска |
| `DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP` | `true` | восстановление после restart/redeploy |
| `DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER` | `true` | не входить в пустой voice channel; checkpoint остаётся pending |
| `DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED` | `true` | bounded recovery при неожиданном LEAVE или пропаже frame polling |
| `DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS` | `3` | максимум transport-recovery попыток |
| `DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF` | `2s` | базовый линейный backoff между попытками |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED` | `true` | включает periodic snapshot пяти persistent storage |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_DIRECTORY` | `data/backups` | локальный каталог backup; в Docker принудительно `/app/data/backups` |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL` | `6h` | период между backup snapshot |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION` | `14` | максимальное число ZIP-backup, старые удаляются |

Live-потоки отключены. Подробные правила voice-доступа и lifecycle находятся в [`docs/MUSIC-SESSIONS.md`](docs/MUSIC-SESSIONS.md).
Voice recovery и восстановление сессий после restart/redeploy описаны в [`docs/VOICE-RECOVERY-SESSION-RESTORATION.md`](docs/VOICE-RECOVERY-SESSION-RESTORATION.md).
GitHub-hosted delivery и резервный self-hosted режим описаны в [`docs/SELF-HOSTED-DELIVERY.md`](docs/SELF-HOSTED-DELIVERY.md).
Современный Discord-интерфейс описан в [`docs/MODERN-COMMANDS.md`](docs/MODERN-COMMANDS.md).
Contextual online exploration policy описана в [`docs/CONTEXTUAL-BANDIT-EXPLORATION.md`](docs/CONTEXTUAL-BANDIT-EXPLORATION.md).
Готовые персональные станции и их mapping описаны в [`docs/PERSONALIZED-MIXES-STATIONS.md`](docs/PERSONALIZED-MIXES-STATIONS.md), а daily-выпуски и `/mix resume` — в [`docs/DAILY-MIXES-CONTINUITY.md`](docs/DAILY-MIXES-CONTINUITY.md). Diversity/thematic generation описаны в [`docs/MIX-GENERATION-DIVERSITY.md`](docs/MIX-GENERATION-DIVERSITY.md).
Персональный Home read-model и граница будущих клиентов описаны в [`docs/PERSONALIZED-HOME-MUSIC-HUB.md`](docs/PERSONALIZED-HOME-MUSIC-HUB.md).
Product API и device identity/auth описаны в [`docs/PRODUCT-API.md`](docs/PRODUCT-API.md) и [`docs/USERS-AUTH-DEVICE-SESSIONS.md`](docs/USERS-AUTH-DEVICE-SESSIONS.md); Android gateway/deployment boundary — в [`docs/ANDROID-GATEWAY-FOUNDATION.md`](docs/ANDROID-GATEWAY-FOUNDATION.md), committed contract — в [`docs/openapi/baskov-product-api-v1.yaml`](docs/openapi/baskov-product-api-v1.yaml).
Track identity и provider-neutral catalog foundation описаны в [`docs/TRACK-IDENTITY-CATALOG.md`](docs/TRACK-IDENTITY-CATALOG.md), а client-aware playback resolver — в [`docs/PLAYBACK-SOURCE-RESOLVER.md`](docs/PLAYBACK-SOURCE-RESOLVER.md).
Интерактивная помощь, status refresh и destructive confirmations описаны в [`docs/DISCORD-EXPERIENCE.md`](docs/DISCORD-EXPERIENCE.md).
Интерактивный поиск и безопасный выбор трека описаны в [`docs/SEARCH-TRACK-SELECTION.md`](docs/SEARCH-TRACK-SELECTION.md).
Постоянные плейлисты, история и replay описаны в [`docs/PLAYLISTS-HISTORY-REPLAY.md`](docs/PLAYLISTS-HISTORY-REPLAY.md), а личное избранное — в [`docs/FAVORITES-PERSONAL-LIBRARY.md`](docs/FAVORITES-PERSONAL-LIBRARY.md).
DJ-роли, access modes и vote-skip описаны в [`docs/DJ-ROLES-AND-VOTING.md`](docs/DJ-ROLES-AND-VOTING.md), а guild administration — в [`docs/GUILD-ADMINISTRATION.md`](docs/GUILD-ADMINISTRATION.md).
Least-privilege queue moderation, moderator-role и per-requester pending limit описаны в [`docs/ADMINISTRATION-MODERATION-2.md`](docs/ADMINISTRATION-MODERATION-2.md).
Очередь и новые команды управления описаны в [`docs/QUEUE-EXPERIENCE.md`](docs/QUEUE-EXPERIENCE.md).
Расширенный пульт, история и `/previous` описаны в [`docs/ADVANCED-PLAYBACK-CONTROLS.md`](docs/ADVANCED-PLAYBACK-CONTROLS.md).
Маршрутизация источников и переход на YouTube primary описаны в [`docs/YOUTUBE-PRIMARY-PROVIDER.md`](docs/YOUTUBE-PRIMARY-PROVIDER.md).
Замена встроенного YouTube extractor на modern source описана в [`docs/MODERN-YOUTUBE-SOURCE.md`](docs/MODERN-YOUTUBE-SOURCE.md).
Подробности recovery обрезанных preview и SoundCloud `404` описаны в [`docs/SOURCE-STREAMING-STABILITY.md`](docs/SOURCE-STREAMING-STABILITY.md).
Постоянные guild-настройки описаны в [`docs/GUILD-SETTINGS.md`](docs/GUILD-SETTINGS.md).
Operations и health-модель описаны в [`docs/OPERATIONS.md`](docs/OPERATIONS.md).
Maven repository routing для GitHub-hosted CI/CD описан в [`docs/MAVEN-REPOSITORY-ROUTING.md`](docs/MAVEN-REPOSITORY-ROUTING.md).
Voice connection state machine описана в [`docs/VOICE-CONNECTIONS.md`](docs/VOICE-CONNECTIONS.md).
Root-cause voice diagnostics и bridge/host A/B-тест описаны в [`docs/VOICE-ROOT-CAUSE-DIAGNOSTICS.md`](docs/VOICE-ROOT-CAUSE-DIAGNOSTICS.md).
DAVE voice migration и переход JDA 5 → 6 описаны в [`docs/DAVE-VOICE-MIGRATION.md`](docs/DAVE-VOICE-MIGRATION.md).
Native libDAVE runtime, platform profiles и startup fail-fast описаны в [`docs/NATIVE-DAVE.md`](docs/NATIVE-DAVE.md).
Релиз с Android описан в [`docs/TERMUX-RELEASE.md`](docs/TERMUX-RELEASE.md).


## Playback Source Abstraction & Provider Resilience (v1.26.0–v1.27.0)

`v1.26.0` ввёл client-aware `PlaybackResolver` поверх provider-neutral `TrackIdentity`: recommendation/mix слой выбирает логический трек, а transport identifiers (`ytsearch:`, `scsearch:`) создаются только внутри `PlaybackSourceProvider`. `v1.27.0` добавляет поверх этой границы process-local provider health, controlled sequential fallback и bounded circuit breaker: после трёх подряд technical load failures provider уходит в cooldown на 90 секунд, новые resolutions выбирают следующий доступный источник, а после cooldown primary возвращается через probe. `noMatches` вызывает fallback, но не портит provider health, потому что отсутствие конкретного трека не равно отказу площадки.

Новый `PlaybackClientCapabilities` позволяет одному и тому же `TrackIdentity` разрешаться по-разному для Discord, Android и Web без изменения recommendation engine. Smart Radio/Mix больше не конструирует YouTube search напрямую: он вызывает resolver и загружает его primary source. Ручные `/play` и прямые URL остаются на существующем explicit-input path.

Persistence не меняется: нового provider registry/storage нет, а legacy `StoredTrack.playbackIdentifier/sourceIdentifier/provider` сохраняются для совместимости.

## Track Identity & Catalog Foundation (v1.25.0)

`v1.25.0` вводит client/provider-neutral `TrackIdentity`: логическая песня определяется display + normalized artist/title и стабильным `stableKey`, а не URL, YouTube video id или SoundCloud locator. Старый `RecommendationIdentity` сохранён как совместимый facade и делегирует в новую identity-модель, поэтому novelty/feedback keys не требуют миграции.

`TrackCatalogEntry` объединяет `TrackIdentity`, descriptive tags и authoritative catalog IDs (`MusicBrainz recording`, `ISRC`). `RecommendationCandidate` может нести такой catalog metadata ещё до playback, а Last.fm `mbid` теперь сохраняется именно как MusicBrainz recording id. Provider-specific playback locators намеренно остаются вне catalog слоя: их abstraction и `PlaybackResolver` вынесены в отдельный `v1.26`.

Persistence v1.25 не меняется: существующие `playbackIdentifier/sourceIdentifier` остаются transport detail старых library/session records до отдельной безопасной migration.

## Personalized Home / Music Hub (v1.24.0)

```text
/home
```

`/home` — первый client-neutral product surface Baskov Music. `MusicHomeService` строит immutable `HomeSnapshot` через read-only `MusicHomeReadPort`; snapshot содержит active/resumable station, `Микс дня`/`Открытия дня`, персональные curated stations, positive themes, counters библиотеки, preview personal history и зрелость taste-profile.

Discord-слой только превращает этот snapshot в embed. Home не стартует playback, не меняет очередь и не создаёт отдельный persistence: он читает существующие library/feedback/runtime состояния. Такая граница нужна специально для будущего Product API и Android-клиента — тот же semantic snapshot сможет отображаться без переноса recommendation/mix логики в Kotlin.

## Mix Generation & Diversity Control (v1.23.0)

```text
/mix themes
/mix start station:theme theme:pop punk
```

Curated станции (`my-mix`, daily/discovery variants, `mood`, `theme`) используют bounded diversity-context: immediate повтор исполнителя блокируется, частые artist/tag получают штраф, а seed pool round-robin разводит исполнителей до повторного использования. `familiar` остаётся permissive для маленьких библиотек.

`Тематический микс` использует положительные `tagAffinity` из recommendation feedback V2 как focus. Theme влияет только на near-tie ranking и не может обойти recent/hard novelty, queue policy или обычный `ytsearch:` playback transport. `/mix resume` сохраняет theme и diversity window только в памяти процесса; нового storage нет.

## Daily Mixes & Station Continuity (v1.22.0)

```text
/mix list
/mix start station:my-mix
/mix start station:daily-mix
/mix start station:discoveries
/mix start station:daily-discoveries
/mix start station:familiar
/mix start station:mood
/mix resume
/mix status
/mix stop
```

`/mix` остаётся product-level preset layer поверх `/radio` и не создаёт отдельный player/queue/voice path. `Микс дня` и `Открытия дня` получают детерминированный bounded seed-набор из personal library на дату runtime; discovery-вариант сохраняет hard novelty. `/mix resume` до 36 часов восстанавливает process-local station continuity вместе с daily release, seed cursor и recent anti-repeat memory. Restart/deploy очищает continuity и оставляет mix OFF; durable feedback/model остаются без новой migration.

## CI/CD

### CI

`.github/workflows/ci.yml` запускается для pull request и вручную. Он:

1. поднимает Java 17 через `actions/setup-java@v5` и восстанавливает dependency cache по стабильному dependency fingerprint, который не меняется от одного bump версии приложения;
2. печатает bounded network probes для Maven Central и обоих Lavalink repository;
3. выполняет `clean verify` через `.github/scripts/maven-ci.sh`: transfer progress виден, одна network/timeout ошибка получает ровно один retry, а каждый Maven attempt ограничен 7 минутами;
4. проверяет сборку Docker-образа;
5. всегда сохраняет Maven diagnostics и Surefire reports как artifacts.

### Delivery

`.github/workflows/delivery.yml` запускается:

- при push в `dev` — environment `development`, channel tag `dev`;
- при push в `master` — environment `production`, channel tag `latest`;
- вручную через `workflow_dispatch`.

В текущем режиме разработки релизы отправляются напрямую в `master`; ветка `dev` остаётся технически поддерживаемой, но не используется.

Каждая доставка сначала выполняет Maven diagnostics + bounded Maven verification. `setup-java` настроен с `show-download-progress: true`, поэтому dependency resolution больше не выглядит как бесконечная тишина; cache restore segment ограничен двумя минутами, а Maven verification — двумя попытками максимум по семь минут. Начиная с `v1.6.2`, Maven Resolver groupId filtering маршрутизирует `dev.lavalink.youtube` только в `lavalink-releases`, `moe.kyokobot.libdave` только в `lavalink-libdave-snapshots`, а Maven Central остаётся unrestricted. Начиная с `v1.6.3`, CI сначала восстанавливает project-owned Maven cache с fallback на известный зелёный cache `v1.5.0`, проверяет наличие закреплённых external POM/JAR и сохраняет новый cache только после успешного Maven gate. Production image собирается из уже проверенного `target/baskov-discord-bot.jar`, без второго Maven resolution внутри Docker. После этого workflow публикует в GHCR:

```text
ghcr.io/<owner>/<repository>:sha-<full-git-sha>
ghcr.io/<owner>/<repository>:dev|latest
```

На VPS всегда разворачивается immutable SHA-тег. После Docker healthcheck workflow дополнительно сверяет фактический image, опубликованный OCI digest, `RestartCount`, storage-readiness marker, persistence-backup marker и внутренний heartbeat. При любой неудаче автоматически восстанавливаются предыдущий `.env` и предыдущий образ.

## Настройка GitHub

Создайте GitHub Environments:

- `development`;
- `production`.

Для production можно включить Required reviewers.

Добавьте repository variable:

| Variable | Значение | Назначение |
|---|---:|---|
| `BOT_DEPLOY_ENABLED` | `true` | включает SSH-деплой после публикации образа |

`BOT_DEPLOY_DIR` и `BOT_CONTAINER_NAME` задаются как environment variables соответствующего GitHub Environment, если нужны нестандартные пути или имена контейнеров.

Необязательные environment variables музыкальной сессии:

| Variable | По умолчанию | Назначение |
|---|---:|---|
| `DISCORD_BOT_PREFIX` | `!` | префикс legacy-команд |
| `DISCORD_BOT_MUSIC_MAX_QUEUE_SIZE` | `100` | размер очереди от 1 до 1000 |
| `DISCORD_BOT_MUSIC_MAX_TRACK_DURATION` | `4h` | максимальная длительность трека |
| `DISCORD_BOT_MUSIC_IDLE_DISCONNECT_TIMEOUT` | `5m` | таймаут отключения пустой сессии |
| `DISCORD_BOT_MUSIC_VOICE_CONNECT_TIMEOUT` | `15s` | timeout одной voice-попытки |
| `DISCORD_BOT_MUSIC_PLAYBACK_READY_TIMEOUT` | `10s` | timeout подтверждения первого media frame poll |
| `DISCORD_BOT_MUSIC_VOICE_FAILURE_COOLDOWN` | `30s` | cooldown после voice failure |
| `DISCORD_BOT_MUSIC_VOICE_DISCONNECT_GRACE` | `5s` | grace при разрыве активной сессии |
| `DISCORD_BOT_MUSIC_VOICE_WATCHDOG_ENFORCE` | `false` | observe-only либо enforce watchdog |
| `BOT_NETWORK_MODE` | `bridge` | Docker bridge или диагностический host network |
| `DISCORD_BOT_VOICE_LOG_LEVEL` | `DEBUG` | уровень узкого JDA voice logger |
| `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` | `100` | начальная громкость |
| `DISCORD_BOT_MUSIC_MAX_VOLUME` | `150` | максимальная громкость |
| `DISCORD_BOT_MUSIC_LIBRARY_FILE` | `data/music-library.tsv` | файл постоянных плейлистов, истории и личного избранного |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED` | `true` | включить backup persistent storage |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL` | `6h` | интервал backup |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION` | `14` | число сохраняемых backup |

Workflow кодирует эти значения перед SSH-передачей, а серверный deploy-скрипт проверяет формат до перезаписи защищённого `.env`.

В каждый environment добавьте secrets с одинаковыми именами:

| Secret | Назначение |
|---|---|
| `DISCORD_BOT_TOKEN` | токен соответствующего Discord-бота |
| `BOT_DEPLOY_HOST` | адрес VPS |
| `BOT_DEPLOY_PORT` | SSH-порт, обычно `22` |
| `BOT_DEPLOY_USER` | SSH-пользователь с доступом к Docker |
| `BOT_DEPLOY_SSH_KEY` | приватный SSH-ключ |
| `BOT_DEPLOY_KNOWN_HOSTS` | доверенная строка host key сервера |

`BOT_DEPLOY_KNOWN_HOSTS` можно получить локально:

```bash
ssh-keyscan -p 22 your-server.example.com
```

Пользователь на VPS должен иметь доступ к `docker` и `docker compose`, а также право записи в родительский каталог деплоя. Каталог деплоя создаётся workflow автоматически. Для одновременно работающих development и production используйте разные Discord applications/tokens, иначе оба экземпляра будут отвечать на одни и те же события.

## Режим тестирования

В обычном запуске `DISCORD_BOT_ENABLED=true` по умолчанию. Префикс задаётся через `DISCORD_BOT_PREFIX` и по умолчанию равен `!`. Тестовый Spring context запускается с `discordBot.enabled=false`, поэтому CI не требует токен и не подключается к Discord.

## Версии и релизы

Проект использует Semantic Versioning. История изменений находится в [`CHANGELOG.md`](CHANGELOG.md), а пошаговый процесс выпуска — в [`docs/RELEASING.md`](docs/RELEASING.md). Основная ветка `master` одновременно является production-веткой.

## Документация

- [О проекте](https://github.com/Flawden/BaskovDiscordBot/wiki)
- [Развёртывание и запуск](https://github.com/Flawden/BaskovDiscordBot/wiki/Развертывание-приложения)
- [Гигиена зависимостей](docs/DEPENDENCY-HYGIENE.md)

## Лицензия

MIT.
