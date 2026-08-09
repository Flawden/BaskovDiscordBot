# Maven Repository Routing

Начиная с `v1.6.2`, GitHub-hosted CI/CD использует Maven Resolver groupId Remote Repository Filtering, чтобы специальные Lavalink repositories не участвовали в поиске каждого артефакта из Maven Central.

## Причина

Проекту нужны два нестандартных источника:

- `lavalink-releases` (`https://maven.lavalink.dev/releases`) — для `dev.lavalink.youtube:v2`;
- `lavalink-libdave-snapshots` (`https://maven.lavalink.dev/snapshots`) — для commit-version `moe.kyokobot.libdave`.

Без repository filtering Maven может последовательно опрашивать эти repositories перед Central даже для Spring Boot BOM, JUnit, Jetty, Jackson, Netty и других артефактов, которых там заведомо нет. На чистом hosted runner это превращало model/dependency resolution в сотни лишних сетевых запросов и приводило к 420-секундному timeout ещё до compile/test lifecycle.

## Routing policy

`.mvn/rrf/groupId-lavalink-releases.txt` содержит:

```text
dev.lavalink.youtube
```

`.mvn/rrf/groupId-lavalink-libdave-snapshots.txt` содержит:

```text
moe.kyokobot.libdave
```

Для repository `central` routing-файла нет. Maven Central остаётся unrestricted и обслуживает обычные dependencies, parent POM/BOM и build plugins.

`.github/scripts/maven-ci.sh` запускает Maven с:

```text
-Daether.remoteRepositoryFilter.groupId=true
-Daether.remoteRepositoryFilter.groupId.basedir=<repo>/.mvn/rrf
```

Basedir передаётся абсолютным путём, чтобы Resolver не интерпретировал относительный путь относительно local Maven repository.

## Диагностика

`maven-ci.sh diagnose` записывает в `environment.log`:

```text
maven_remote_repository_filter=groupId
maven_remote_repository_filter_basedir=...
maven_remote_repository_filter_file=groupId-lavalink-releases.txt:dev.lavalink.youtube
maven_remote_repository_filter_file=groupId-lavalink-libdave-snapshots.txt:moe.kyokobot.libdave
```

Transfer progress остаётся включённым. Если после routing сборка снова зависнет, `verify-attempt-*.log` должен показать уже конкретный допустимый repository/artifact, а не fan-out всех Central dependencies через Lavalink.

## Изменение зависимостей

Если новый dependency действительно публикуется только в одном из custom repositories, его `groupId` нужно явно добавить в соответствующий `.mvn/rrf/groupId-<repository-id>.txt` и закрепить contract test. Не добавляйте широкие groupId без необходимости и не создавайте `groupId-central.txt`.
