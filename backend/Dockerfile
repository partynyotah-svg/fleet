FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY web ./src/main/resources/static
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 --create-home relay
COPY --from=build /workspace/target/relay-fleet-1.0.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]