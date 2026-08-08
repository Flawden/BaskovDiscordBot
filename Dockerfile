# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy AS builder
ARG APP_REVISION=development
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress package -DskipTests -Dbuild.revision="${APP_REVISION}"

FROM eclipse-temurin:17-jre-jammy AS runtime
ARG APP_VERSION=development
WORKDIR /app

LABEL org.opencontainers.image.title="Baskov Discord Bot" \
      org.opencontainers.image.description="Музыкальный Discord-бот на Java, Spring Boot, JDA, LavaPlayer и native libDAVE" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/logs /app/data \
    && chown -R app:app /app \
    && chmod 750 /app/logs /app/data

COPY --from=builder --chown=app:app \
    /workspace/target/baskov-discord-bot.jar /app/app.jar
COPY --chmod=0555 --chown=app:app deploy/healthcheck.sh /app/healthcheck.sh

USER 10001:10001

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
  CMD ["/app/healthcheck.sh"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
