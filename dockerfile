# ── Build stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy Maven wrapper & POM first to leverage Docker layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw \
 && ./mvnw -B dependency:go-offline -DskipTests

# Copy source and package the Spring Boot jar
COPY src src
RUN ./mvnw -B -Dmaven.test.skip=true package

# ── Runtime stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
ENV APP_HOME=/app
WORKDIR ${APP_HOME}

# Copy the packaged jar (e.g. file-ingestor-service-0.0.1-SNAPSHOT.jar)
COPY --from=build /workspace/target/*.jar app.jar

# Download Azure Application Insights Java agent
ARG AI_AGENT_VERSION=3.4.17
RUN wget -q \
    https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${AI_AGENT_VERSION}/applicationinsights-agent-${AI_AGENT_VERSION}.jar \
    -O applicationinsights-agent.jar

# Create non-root user
RUN useradd --create-home appuser \
 && chown appuser:appuser app.jar applicationinsights-agent.jar
USER appuser

# Expose the Spring Boot port
EXPOSE 8080

# JVM options and agent
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -javaagent:/app/applicationinsights-agent.jar -jar /app/app.jar"]