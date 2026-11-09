FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /source
COPY . .
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre AS api
WORKDIR /app
RUN groupadd -g 10001 chaos && useradd -u 10001 -g chaos chaos && mkdir -p /data/evidence && chown -R chaos:chaos /data /app
COPY --from=build /source/chaosmonkey-api/target/*-exec.jar /app/app.jar
USER 10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]

FROM mcr.microsoft.com/playwright/java:v1.56.0-noble AS worker
WORKDIR /app
RUN groupadd -g 10001 chaos && useradd -u 10001 -g chaos chaos && mkdir -p /data/evidence && chown -R chaos:chaos /data /app
COPY --from=build /source/chaosmonkey-worker/target/*.jar /app/app.jar
USER 10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar","--server.port=8081"]

FROM eclipse-temurin:21-jre AS demo
WORKDIR /app
RUN groupadd -g 10001 chaos && useradd -u 10001 -g chaos chaos
COPY --from=build /source/chaosmonkey-demo-app/target/*-exec.jar /app/app.jar
USER 10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
