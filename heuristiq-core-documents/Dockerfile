FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Kolkata

# Set timezone
RUN apt-get update && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/*.jar app.jar

# JVM memory budget for the container's --memory limit (e.g. 512m). The container is OOM-killed
# (exit 137) when heap + non-heap exceeds that limit, so heap is capped at 45% to leave room for
# metaspace (<=160m), code cache (48m), direct buffers (32m), thread stacks and GC overhead.
# MALLOC_ARENA_MAX=2 stops glibc from creating one malloc arena per thread, which otherwise makes
# resident memory creep up for hours and look like a leak.
ENV MALLOC_ARENA_MAX=2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 \
 -XX:MaxMetaspaceSize=160m \
 -XX:MaxDirectMemorySize=32m \
 -XX:ReservedCodeCacheSize=48m \
 -Xss512k \
 -XX:ActiveProcessorCount=1 \
 -XX:+UseSerialGC \
 -XX:TieredStopAtLevel=1 \
 -XX:+ExitOnOutOfMemoryError \
 -Duser.timezone=Asia/Kolkata"

# Smaller thread/connection pools = less memory per service
ENV SERVER_TOMCAT_THREADS_MAX=30 \
    SPRING_DATA_MONGODB_MAX_POOL_SIZE=10

EXPOSE 9104

ENTRYPOINT ["java", "-jar", "app.jar"]