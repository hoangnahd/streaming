############################
# Stage 1 - Build Custom JRE
############################
FROM eclipse-temurin:21-jdk-alpine AS jre-builder

RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,jdk.unsupported \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

############################
# Stage 2 - Runtime
############################
FROM alpine:latest AS runtime

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-builder /javaruntime $JAVA_HOME

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
RUN mkdir -p /certs && chown -R spring:spring /app /certs

COPY target/*.jar app.jar

EXPOSE 8080
USER spring:spring

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]