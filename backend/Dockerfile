FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 pharmacy && useradd --uid 10001 --gid pharmacy --no-create-home pharmacy
WORKDIR /app
COPY --from=build --chown=pharmacy:pharmacy /workspace/target/pharmacy-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV TZ=UTC
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
