# syntax=docker/dockerfile:1

# Build stage: Maven Wrapper keeps the project build reproducible on the Linux host.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

# Runtime stage: only the JRE and packaged application are kept in the final image.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl is used only by the container health check. Run the application as a non-root user.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home spring

COPY --from=build /workspace/target/ai-data-analyst-*.jar app.jar

EXPOSE 8080
USER spring

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
