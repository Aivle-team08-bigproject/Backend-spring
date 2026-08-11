FROM eclipse-temurin:21-jdk@sha256:efd34b940f2d5a621605c8531c2afb7759c936b6c2ef637a69aa3bf3e1e789d1 AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew clean test bootJar --no-daemon

FROM eclipse-temurin:21-jre@sha256:8cef5fc7bebe421363ab543a2f4db5caf7d119d8db67d56b0f56c485d2de4d55
WORKDIR /app
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home spring
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=api \
    JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"
USER spring
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD sh -c 'case ",$${SPRING_PROFILES_ACTIVE}," in *,worker,*) kill -0 1 ;; *) curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness ;; esac'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
