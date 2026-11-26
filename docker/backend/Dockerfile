# syntax=docker/dockerfile:1

# Backend image: builds the Spring Boot jar with the Maven wrapper, then runs it on a JRE as a non-root user.
# The build context is the repository root:
#   docker build -f docker/backend/Dockerfile -t scheduler-backend .

# ---------------------------------------------------------------------------------------------------------------
# 1) Build
# ---------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and pom first: the dependency download below is cached as an image layer until pom.xml changes.
# (No BuildKit-only features such as cache mounts, so the image also builds with the classic builder.)
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw
# Best effort: anything this misses is downloaded by the package step.
RUN ./mvnw -B -ntp -q dependency:go-offline || true

COPY backend/src src
# Tests run in CI (./mvnw verify); the image only needs the jar.
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/scheduler-*.jar /workspace/app.jar

# ---------------------------------------------------------------------------------------------------------------
# 2) Runtime
# ---------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# curl is used by the HEALTHCHECK; installed explicitly so the check does not depend on the base image's contents.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring

WORKDIR /app
# Owned by root and only readable by the app user: the process cannot modify its own code.
COPY --from=build /workspace/app.jar app.jar
USER spring

# - The "docker" Spring profile points the datasource at the compose service "db" (all values can be overridden
#   with environment variables: DB_URL, DB_USER, DB_PASSWORD, INSTANCE_ID, ...).
# - The JVM is container-aware: the heap is sized from the container memory limit, and an OutOfMemoryError
#   stops the process so Docker restarts it instead of leaving a half-broken instance running.
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Ready = Flyway migrations applied, Quartz started, startup reconciliation done.
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --start-interval=3s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness" > /dev/null || exit 1

# Exec form: java is PID 1 and receives SIGTERM, so Spring shuts down gracefully (Quartz lets running tasks finish).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
