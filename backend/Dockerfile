# Build context must be the repository root (the build reads ../api/openapi.yaml).
# docker build -f backend/Dockerfile .

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/backend

# Gradle wrapper + build scripts first for layer caching
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew --version --no-daemon

# OpenAPI contract (referenced as ../api/openapi.yaml) and sources
COPY api /app/api
COPY backend/src ./src

RUN ./gradlew bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S shopmate && adduser -S shopmate -G shopmate

# OTel Java agent, shipped in the image but inert by default — prod compose
# activates it via JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar
ARG OTEL_JAVAAGENT_VERSION=2.29.0
ADD --chmod=444 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
# ADD creates /otel root-only; the shopmate user needs to traverse it.
RUN chmod 755 /otel

COPY --from=build /app/backend/build/libs/*.jar app.jar
USER shopmate
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
