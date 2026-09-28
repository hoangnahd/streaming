FROM eclipse-temurin:21-jdk-alpine AS runtime

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

RUN mkdir -p /certs && chown -R spring:spring /app /certs

COPY target/*.jar app.jar

EXPOSE 8080

USER spring:spring

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]