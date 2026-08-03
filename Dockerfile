# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R app:app /app \
    && chmod 750 /app/logs

COPY --from=builder --chown=app:app \
    /workspace/target/BascovDiscordBot-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
  CMD test -s /tmp/baskov-discord-bot.ready || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
