# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/vault.jar /app/vault.jar

ENTRYPOINT ["java", "-cp", "/app/vault.jar", "com.example.vault.cli.Main"]
