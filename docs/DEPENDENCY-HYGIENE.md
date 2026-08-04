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
