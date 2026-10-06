# syntax=docker/dockerfile:1.7
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean installDist

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/install/civil-connection-backend/ ./
USER app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["./bin/civil-connection-backend"]
