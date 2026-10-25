# Multi-stage Dockerfile for Taiga MCP Server

# --- Build Stage ---
FROM gradle:8.12-jdk17 AS builder
WORKDIR /app

# Cache dependencies
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server/build.gradle.kts server/

# Copy source code and build fat JAR
COPY server/src/ server/src/
RUN ./gradlew :server:fatJar --no-daemon

# --- Runtime Stage ---
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="Taiga MCP Team"

WORKDIR /app

# Run as non-root user
RUN addgroup -S taiga && adduser -S taiga -G taiga
USER taiga:taiga

# Copy runnable fat JAR
COPY --from=builder /app/server/build/libs/server-standalone.jar /app/server-standalone.jar

ENV HOST=0.0.0.0 \
    PORT=8080 \
    TAIGA_API_URL=https://api.taiga.io

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-jar", "/app/server-standalone.jar"]
