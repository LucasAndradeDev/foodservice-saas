# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source changes.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# pg_dump, used by the /api/v1/internal/backups endpoint. pg_dump refuses to run against a
# NEWER server major version than itself, so this has to track whatever's newest in production
# (Render Postgres is 18.x today), not the docker-compose dev database (16.x) - a newer client
# dumping an older server works fine, so this one binary covers both.
RUN apk add --no-cache postgresql18-client

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
