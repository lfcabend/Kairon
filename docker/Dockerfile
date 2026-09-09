# syntax=docker/dockerfile:1
#
# One image for all of Kairon: the REST API and the React SPA, served from a
# single Spring Boot jar. See docs/DESIGN.md §8.1.
#
# Build context is the repo root:  docker build -f docker/Dockerfile -t kairon .

# ---- Stage 1: build the SPA -------------------------------------------------
FROM node:24-bookworm-slim AS web
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# ---- Stage 2: build the jar ----------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY backend/ backend/
COPY web/ web/
# Drop in the already-built SPA so Gradle only has to package it.
COPY --from=web /app/web/dist/ web/dist/
RUN chmod +x gradlew && \
    ./gradlew --no-daemon :backend:bootJar \
      -x :web:buildWeb -x :web:npmInstall -x :web:npmSetup -x :web:nodeSetup

# ---- Stage 3: runtime -------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system kairon && useradd --system --gid kairon --home /app kairon
WORKDIR /app
COPY --from=build /app/backend/build/libs/kairon.jar app.jar
USER kairon
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
