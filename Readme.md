# 🎤 Baskov Discord Bot

Текущая версия релизной ветки: **v1.18.0**.

Музыкальный Discord-бот на Java 17, Spring Boot, JDA, LavaPlayer и native libDAVE.

## Возможности

- slash-команды воспроизведения и полноценного управления очередью;
- Smart Discovery Engine: `/radio start|status|why|feedback|model|stop` поддерживает `familiar/similar/discovery`; Last.fm генерирует похожих кандидатов при наличии `LASTFM_API_KEY`, а discovery жёстко фильтрует уже знакомые треки и сохраняет обычный `ytsearch:` playback pipeline;
- Recommendation Model / Embeddings Foundation: Personal Ranking Model дополнен локальным `feature-hash-v1` 64D embedding-provider, taste-vector и cosine similarity; `/radio model` показывает vector confidence/coverage, а `/radio why` объясняет отдельный vector-вклад. Интерфейс embedding-provider отделён от playback и готов к будущему semantic provider без изменения `ytsearch:` тракта;
- Collaborative Signals: optional ListenBrainz artist graph добавляет независимый collaborative-вклад к Last.fm + personal/vector ranker; без `LISTENBRAINZ_TOKEN` или при сбое upstream система fail-open продолжает старый recommendation pipeline;
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
- atomic persistence backups четырёх storage-файлов внутри `/app/data/backups` с bounded retention и owner-only permissions;
- live storage probe в `/status`, агрегированный reliability state и command failure rate/последняя ошибка;
- JDA 6.5.0 с настоящей JNI libDAVE `ce725965e`, положительной protocol version и подтверждением playback только после реального запроса аудиофрейма Discord media transport;
- bounded voice recovery: отключённый JDA auto-reconnect, до трёх контролируемых повторных подключений с backoff и сохранением checkpoint при исчерпании попыток;
- Playback Sessions & Recovery 2.0: atomic checkpoint V2 сохраняет voice channel, текущий трек/позицию, очередь, pause, volume, repeat и bounded previous-history; `/session status` показывает guild-scoped recovery state, а `/session recover` даёт manager/admin безопасно повторить pending recovery;
- YouTube как основной провайдер текстового поиска через отдельный modern `youtube-source 1.18.2`; встроенный legacy extractor LavaPlayer отключён, а SoundCloud остаётся только для прямых ссылок и совместимости;
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

Боту не нужен входящий HTTP-порт. Перед подключением к Discord выполняется fail-fast storage preflight для guild settings, music library, session checkpoint и recommendation feedbacks; после подключения приложение обновляет readiness heartbeat каждые 10 секунд, а Docker считает контейнер healthy только при свежем `CONNECTED`-сигнале.

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
| `BOT_NETWORK_MODE` | `bridge` | `bridge` production default или `host` для диагностического A/B-теста |
| `DISCORD_BOT_VOICE_LOG_LEVEL` | `DEBUG` | узкий log level внутренних JDA audio-классов |
| `DISCORD_BOT_MUSIC_DEFAULT_VOLUME` | `100` | громкость новой guild-сессии |
| `DISCORD_BOT_MUSIC_MAX_VOLUME` | `150` | верхняя граница команды `/volume` |
| `DISCORD_BOT_PERSISTENCE_FILE` | `data/guild-settings.properties` | файл постоянных guild-настроек; в Docker используется `/app/data/...` |
| `DISCORD_BOT_MUSIC_LIBRARY_FILE` | `data/music-library.tsv` | отдельный atomic-файл постоянных плейлистов, истории и личного избранного; в Docker `/app/data/music-library.tsv` |
| `DISCORD_BOT_MUSIC_SESSION_FILE` | `data/music-sessions.tsv` | atomic checkpoint активных voice/music-сессий; в Docker `/app/data/music-sessions.tsv` |
| `DISCORD_BOT_RECOMMENDATION_FEEDBACK_FILE` | `data/recommendation-feedback.tsv` | persistent bounded history результатов рекомендаций; в Docker `/app/data/recommendation-feedback.tsv` |
| `DISCORD_BOT_MUSIC_SESSION_CHECKPOINT_INTERVAL` | `5s` | период сохранения активной сессии |
| `DISCORD_BOT_MUSIC_SESSION_MAX_AGE` | `6h` | максимальный возраст checkpoint для автозапуска |
| `DISCORD_BOT_MUSIC_SESSION_RESTORE_ON_STARTUP` | `true` | восстановление после restart/redeploy |
| `DISCORD_BOT_MUSIC_SESSION_REQUIRE_HUMAN_LISTENER` | `true` | не входить в пустой voice channel; checkpoint остаётся pending |
| `DISCORD_BOT_MUSIC_SESSION_VOICE_RECOVERY_ENABLED` | `true` | bounded recovery при неожиданном LEAVE или пропаже frame polling |
| `DISCORD_BOT_MUSIC_SESSION_MAX_RECOVERY_ATTEMPTS` | `3` | максимум transport-recovery попыток |
| `DISCORD_BOT_MUSIC_SESSION_RECOVERY_BACKOFF` | `2s` | базовый линейный backoff между попытками |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_ENABLED` | `true` | включает periodic snapshot трёх persistent storage |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_DIRECTORY` | `data/backups` | локальный каталог backup; в Docker принудительно `/app/data/backups` |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_INTERVAL` | `6h` | период между backup snapshot |
| `DISCORD_BOT_OPERATIONS_PERSISTENCE_BACKUP_RETENTION` | `14` | максимальное число ZIP-backup, старые удаляются |

Live-потоки отключены. Подробные правила voice-доступа и lifecycle находятся в [`docs/MUSIC-SESSIONS.md`](docs/MUSIC-SESSIONS.md).
Voice recovery и восстановление сессий после restart/redeploy описаны в [`docs/VOICE-RECOVERY-SESSION-RESTORATION.md`](docs/VOICE-RECOVERY-SESSION-RESTORATION.md).
GitHub-hosted delivery и резервный self-hosted режим описаны в [`docs/SELF-HOSTED-DELIVERY.md`](docs/SELF-HOSTED-DELIVERY.md).
Современный Discord-интерфейс описан в [`docs/MODERN-COMMANDS.md`](docs/MODERN-COMMANDS.md).
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
