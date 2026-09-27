# syntax=docker/dockerfile:1.7

FROM bellsoft/liberica-openjdk-alpine:21 AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test


FROM bellsoft/liberica-openjre-alpine:21

RUN addgroup -S app \
    && adduser -S app -G app \
    && apk add --no-cache wget

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER app

ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.awt.headless=true"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
