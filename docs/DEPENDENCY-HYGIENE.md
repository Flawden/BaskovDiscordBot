# Гигиена зависимостей

## Spring Boot управляет logging stack

Проект использует `spring-boot-starter-parent`. Поэтому версии SLF4J, Logback и связанных библиотек не фиксируются вручную в `dependencies`.

Прямые зависимости вида:

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>...</version>
</dependency>
```

или:

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>...</version>
</dependency>
```

не добавляются без отдельного обоснованного migration-релиза. Иначе можно получить несовместимые версии `logback-classic` и `logback-core`.

## Один `org.json.JSONObject` на test classpath

`spring-boot-starter-test` транзитивно может приносить `android-json`, а другие зависимости — стандартный `org.json`. В проекте `android-json` исключён, чтобы Spring Boot не обнаруживал две реализации одного класса.

## Тестовое логирование

`src/test/resources/logback-test.xml` использует только консольный appender. Maven-тесты не должны создавать production-файл `logs/bot.log` в рабочем каталоге.

## Контракт

`DependencyHygieneContractTest` проверяет:

- отсутствие прямых logging-зависимостей, которыми управляет Spring Boot;
- отсутствие устаревшего свойства Discord4J;
- сохранение исключения `android-json`;
- отдельную конфигурацию Logback для тестов без файлового appender.

## Обновления

Dependabot ежемесячно проверяет Maven dependencies и GitHub Actions. Его pull request не объединяется автоматически: сначала должны пройти Maven verification и Docker build.

## Совместимый baseline

Текущая production-линия зафиксирована на совместимом наборе:

- Spring Boot `3.4.3`;
- JDA `6.5.0` (isolated DAVE/E2EE voice migration line);
- LavaPlayer `2.2.3`;
- Lombok `1.18.36`;
- Maven Compiler Plugin `3.13.0`;
- Java `17`.

Major-переходы вроде Spring Boot `3 → 4` или будущий JDA `6 → 7` не являются обычным обновлением версии. Они могут менять Java API, package names, lifecycle, тестовую инфраструктуру и требования к runtime. Такие переходы выполняются только отдельным migration-релизом с адаптацией исходников, тестов и deployment-контрактов.

## Правило обновлений

- Не объединять пакетные обновления нескольких framework-зависимостей в один production-коммит.
- Один major framework upgrade — один отдельный релиз.
- Сначала `./mvnw clean verify`, затем Docker build, только потом commit и push.
- Если CI красный после dependency-only изменения, возвращаться к последнему зелёному baseline, а не адаптировать production-код вслепую под несколько новых major API одновременно.
- GitHub Actions обновляются по тому же правилу: major-теги меняются отдельно и только после проверки синтаксиса workflow и реального CI-прогона.

Dependabot настроен игнорировать автоматические major-обновления Maven и GitHub Actions. Minor/patch PR остаются доступными, но не объединяются автоматически.

## Native libDAVE pin

Voice encryption uses the isolated `libdave-jvm ce725965e` line (`adapter-jda`, `impl-jni` and platform natives). It must be upgraded only in a dedicated voice migration release because Java adapter, JNI binding and native binaries have to stay on exactly the same version.
