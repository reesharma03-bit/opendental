FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

COPY pom.xml .
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY src ./src
RUN mvn -B package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser -s /sbin/nologin appuser

WORKDIR /app

COPY --from=builder /app/target/api-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=dev \
    SUPABASE_DB_URL=jdbc:postgresql://host.docker.internal:5432/opendental?sslmode=disable \
    SUPABASE_DB_USERNAME=postgres \
    SUPABASE_DB_PASSWORD=postgres

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
