ARG BUILD_IMAGE=maven:3.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${BUILD_IMAGE} AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM ${RUNTIME_IMAGE} AS runtime
ENV TZ=Asia/Shanghai \
    APP_HOME=/app \
    JAVA_OPTS=
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata curl \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/"$TZ" /etc/localtime \
    && echo "$TZ" > /etc/timezone \
    && groupadd --system app \
    && useradd --system --gid app --home-dir "$APP_HOME" --create-home app \
    && mkdir -p "$APP_HOME/data/exports" "$APP_HOME/logs" \
    && chown -R app:app "$APP_HOME"
WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar
COPY deploy/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["/entrypoint.sh"]
