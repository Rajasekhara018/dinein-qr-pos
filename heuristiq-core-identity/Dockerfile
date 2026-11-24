# syntax=docker/dockerfile:1
# ------------------------------------------------------------
# Runtime-only image.
#
# The jar is built OUTSIDE Docker: the Jenkins 'Build' stage runs
#   ./gradlew clean build -x test
# in heuristiq-core-identity before `docker build`, so build/libs/*.jar must exist in the build context
# (the context is the heuristiq-core-identity folder, `docker build .`).
#
# Do NOT turn this back into an in-image Gradle build: heuristiq-core-identity depends on the sibling
# heuristiq-core-resilient-client module (settings.gradle includeBuild('../heuristiq-core-resilient-client')), which is
# outside this folder and therefore invisible to a docker build whose context is only heuristiq-core-identity.
# The plain jar is disabled in build.gradle, so build/libs holds a single executable boot jar.
# ------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Non-root user
RUN addgroup -S railcab && \
    adduser -S uam -G railcab

WORKDIR /app

COPY --chown=uam:railcab build/libs/*.jar /app/app.jar

USER uam

# Application port
EXPOSE 9103

# JVM configuration
# Container memory limit from Jenkins: --memory=1g. Budget: heap 45% (about 460m) + metaspace up to 256m + code cache 64m +
# direct buffers 64m + thread stacks and JVM overhead stay under 1g. (160m of metaspace was too small for this application under
# load: the JVM ended with OutOfMemoryError: Metaspace and the container restarted.)
# Heap: max 45% / initial 20% of container memory; the rest is left for metaspace, code cache,
# direct buffers, thread stacks and JVM/native overhead.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=45.0 \
-XX:InitialRAMPercentage=20.0 \
-XX:MaxMetaspaceSize=256m \
-XX:ReservedCodeCacheSize=64m \
-XX:MaxDirectMemorySize=64m \
-Xss512k \
-XX:+UseSerialGC \
-XX:+ExitOnOutOfMemoryError \
-XX:+UseContainerSupport \
-Djava.net.preferIPv4Stack=true"

# Start Spring Boot application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
