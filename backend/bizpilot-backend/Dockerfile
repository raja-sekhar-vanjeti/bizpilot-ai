# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 bizpilot
COPY --from=build /app/app.jar app.jar
USER bizpilot
EXPOSE 8080
# Render (and most hosts) provide the port in $PORT; fall back to 8080 locally.
# All secrets (DB_PASSWORD, JWT_SECRET, MAIL_PASSWORD, ...) come from environment variables.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
