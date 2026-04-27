# Stage 1 - Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Quality plugins (checkstyle, spotbugs) precisam dos configs no contexto do build.
COPY checkstyle.xml checkstyle-suppressions.xml spotbugs-exclude.xml ./
COPY src/ src/
RUN mvn clean package -DskipTests -B

# Stage 2 - Runtime
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001
WORKDIR /app
COPY --from=build /app/target/arremateai-media-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads/avatares /app/uploads/imoveis /app/uploads/videos
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
