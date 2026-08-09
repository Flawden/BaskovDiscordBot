# Maven Delivery Diagnostics & Resilience

`v1.6.1` не меняет runtime-поведение Discord-бота. Релиз ограничивает и делает наблюдаемым Maven dependency resolution на GitHub-hosted runners после повторяющихся 30-минутных зависаний до первого lifecycle plugin.

## Почему прежний workflow выглядел зависшим

`actions/setup-java` с Maven cache использует dependency files для cache key. Если `pom.xml` меняется даже только из-за версии приложения, возникает новый dependency cache key. Холодный runner снова резолвит Maven artifacts.

Дополнительно прежний workflow явно запускал Maven с `--no-transfer-progress`, поэтому после строки `Building BaskovDiscordBot ...` долгий сетевой resolution выглядел как полная тишина.

## Stable dependency cache identity

`.github/maven-cache-key.txt` содержит только версии зависимостей/build tooling, влияющие на содержимое `~/.m2/repository`:

- Spring Boot;
- JDA;
- LavaPlayer;
- youtube-source;
- native libDAVE commit;
- Lombok;
- Maven Compiler Plugin;
- Maven Wrapper distribution.

Application version в fingerprint не входит. `MavenDeliveryResilienceContractTest` сверяет fingerprint с `pom.xml` и wrapper metadata, чтобы реальное обновление dependency не осталось незамеченным.

## Диагностика

Перед `clean verify` `.github/scripts/maven-ci.sh diagnose` печатает:

- runner/Java/Maven metadata;
- размер `~/.m2`;
- число `*.lastUpdated` markers;
- bounded `curl` probes к Maven Central, Lavalink releases и Lavalink snapshots.

Repository probes диагностические: сами по себе они не определяют результат build. Источник истины — Maven verification.

## Bounded verification

`maven-ci.sh verify` выполняет максимум две попытки. Каждая попытка:

- имеет hard timeout 420 секунд;
- сохраняет полный лог в runner temp;
- не подавляет Maven transfer progress;
- повторяется только после timeout или распознанной network/repository ошибки;
- при compile/test failure завершается сразу без бессмысленного rerun;
- перед network retry удаляет только Maven `*.lastUpdated` markers.

Workflow-level timeout остаётся отдельным последним предохранителем.

## Artifacts

Даже при падении Maven workflow загружает `maven-diagnostics-<run-id>` с environment/probe/attempt logs. CI отдельно сохраняет Surefire reports.

## Cache restore safety

`SEGMENT_DOWNLOAD_TIMEOUT_MINS=2` ограничивает зависший download segment GitHub Actions cache. Maven Wrapper и dependency cache обслуживаются `actions/setup-java@v5`.
