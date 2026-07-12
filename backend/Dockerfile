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
COPY --from=build /app/backend/build/libs/*.jar app.jar
USER shopmate
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
