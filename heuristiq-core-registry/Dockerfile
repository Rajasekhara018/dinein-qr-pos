# syntax=docker/dockerfile:1
# ── Stage 1: Build ───────────────────────────────────────────────────────────────
# Uses the official Gradle image (bundled binary matching the wrapper's version) so the
# build never needs to download the Gradle distribution zip itself — only dependencies.
FROM gradle:9.2.1-jdk21-alpine AS builder
WORKDIR /workspace

# Copy dependency descriptors first — layer caches until these change
COPY settings.gradle .
COPY build.gradle .

# Cache mount for ~/.gradle so dependencies survive across build attempts
RUN --mount=type=cache,target=/root/.gradle \
    gradle dependencies --no-daemon --quiet || true

# Now copy source and build — this layer busts when src changes
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user — avoids running as PID 1 root inside the container
RUN addgroup -S railcab && adduser -S registry -G railcab

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown registry:railcab app.jar

USER registry

# Expose the registry port (matches server.port default)
EXPOSE 9100

# JVM memory budget for the container's --memory limit (e.g. 512m, 384m for the registry). A fixed
# -Xmx512m (or 75%) inside a 512m container leaves no room for non-heap memory, so the container
# was OOM-killed (exit 137) and restarted. Heap is capped at 45% of the limit; metaspace, code
# cache and direct (Netty) buffers are bounded so heap + non-heap stays under it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=45.0 -XX:InitialRAMPercentage=20.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=64m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -Djava.net.preferIPv4Stack=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
