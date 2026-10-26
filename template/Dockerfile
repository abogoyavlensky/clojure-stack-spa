FROM node:24.22.1-alpine AS frontend

WORKDIR /app/frontend

# npm: install frontend deps
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Build the SPA into /app/resources/public (vite outDir: ../resources/public)
COPY frontend/ ./
RUN npm run build


FROM clojure:temurin-25-tools-deps-1.12.6.1673-alpine AS build

WORKDIR /app

# clj: download deps
COPY deps.edn /app/
RUN clojure -P -X:build && clojure -P

# Build uberjar with the prebuilt SPA (no Node in this stage, so run the
# uberjar build directly instead of `bb build` which rebuilds the frontend)
COPY . /app
COPY --from=frontend /app/resources/public /app/resources/public
RUN clojure -T:build build


FROM eclipse-temurin:25.0.4.1_1-jre-alpine

WORKDIR /app
# SQLite database lives here, mount a volume to persist it
RUN mkdir -p /app/db
COPY --from=build /app/target/standalone.jar /app/standalone.jar

EXPOSE 80
# Increase the max memory limit to your needs
CMD ["java", "-Xmx256m", "--enable-native-access=ALL-UNNAMED", "-jar", "standalone.jar"]
