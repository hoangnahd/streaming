# syntax=docker/dockerfile:1

############################
# Stage 1 - Build the app
############################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the POM first so dependency resolution is cached in its own layer
# and only invalidated when pom.xml actually changes (not on every source edit)
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

############################
# Stage 2 - Runtime image
############################
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is only needed for the HEALTHCHECK below; apt cache is removed
# immediately so it doesn't linger in the final image layers
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Dedicated, unprivileged user - never run the JVM as root
RUN groupadd -r spring && useradd -r -g spring -d /app spring

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Mount point for the TLS keystore at runtime. It is intentionally NOT copied
# into the image - baking certs/secrets into a layer means anyone who pulls
# the image (or an old cached layer) has them, even after "removal".
RUN mkdir -p /certs && chown -R spring:spring /app /certs

# These names must match the ${...} placeholders in application.properties
# exactly - SSL_KEYSTORE_PATH / SSL_KEYSTORE_PASSWORD / DB_URL / USERNAME_DB /
# PASSWORD_DB. Only the non-secret path is set here; passwords and DB
# connection details are supplied at `docker run` / compose level via .env.

EXPOSE 8080

USER spring:spring

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -k -f https://localhost:${SERVER_PORT}/actuator/health || exit 1

# "exec" replaces the shell so the JVM runs as PID 1 and receives SIGTERM
# directly from `docker stop`, allowing Spring Boot to shut down gracefully
# instead of being killed after the 10s grace period.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]