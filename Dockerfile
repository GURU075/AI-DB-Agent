# syntax=docker/dockerfile:1.7

FROM node:22.23.2-alpine3.24 AS web-build
WORKDIR /workspace/web
COPY ai_db_agent_ui/package.json ai_db_agent_ui/package-lock.json ./
RUN npm ci
COPY ai_db_agent_ui/ ./
RUN npm run build

FROM maven:3.9.14-eclipse-temurin-17-alpine AS api-build
WORKDIR /workspace/api
COPY AI-Assisted_Database_Agent/pom.xml ./
COPY AI-Assisted_Database_Agent/.mvn ./.mvn
COPY AI-Assisted_Database_Agent/mvnw AI-Assisted_Database_Agent/mvnw.cmd ./
RUN mvn -B -q dependency:go-offline
COPY AI-Assisted_Database_Agent/src ./src
COPY --from=web-build /workspace/web/dist/ ./src/main/resources/static/
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine-3.24 AS runtime
WORKDIR /app
RUN addgroup -S dbagent && adduser -S -G dbagent -u 10001 dbagent
COPY --from=api-build --chown=dbagent:dbagent /workspace/api/target/*.jar /app/app.jar
USER dbagent
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
