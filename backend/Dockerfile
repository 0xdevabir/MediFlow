# syntax=docker/dockerfile:1.7
# MediFlow backend (Spring Boot 3.4, Java 21) - multi-stage, distroless, non-root.
# Build context: backend/   e.g. docker build -t mediflow-backend backend
# Tests are NOT run here (CI runs `./mvnw verify`); this stage only packages.
# Pin base images by digest in production (Dependabot keeps tags current).

ARG BUILD_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=gcr.io/distroless/java21-debian12:nonroot

############################ build ############################
FROM ${BUILD_IMAGE} AS build
WORKDIR /build

# Dependency layer: re-resolved only when pom.xml changes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -Dmaven.test.skip=true package \
 && cp target/*.jar /build/app.jar \
 && java -Djarmode=tools -jar /build/app.jar extract --layers --launcher --destination /build/extracted

# Tiny JVM healthcheck probe (distroless has no shell/curl).
COPY <<'EOF' /build/hc/HealthCheck.java
public class HealthCheck {
  public static void main(String[] args) throws Exception {
    String url = args.length > 0 ? args[0] : "http://127.0.0.1:8081/actuator/health";
    var c = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
    c.setConnectTimeout(2000);
    c.setReadTimeout(4000);
    System.exit(c.getResponseCode() == 200 ? 0 : 1);
  }
}
EOF
RUN javac --release 21 -d /build/hc /build/hc/HealthCheck.java

############################ runtime ############################
FROM ${RUNTIME_IMAGE} AS runtime
LABEL org.opencontainers.image.title="mediflow-backend" \
      org.opencontainers.image.source="https://github.com/0xdevabir/MediFlow"
WORKDIR /app

# Layer order: least -> most frequently changing.
COPY --from=build --chown=65532:65532 /build/extracted/dependencies/ ./
COPY --from=build --chown=65532:65532 /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=65532:65532 /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=65532:65532 /build/extracted/application/ ./
COPY --from=build /build/hc/HealthCheck.class /opt/hc/HealthCheck.class

# Actuator on a separate, internal-only port (never routed by nginx).
ENV MANAGEMENT_SERVER_PORT=8081 \
    MEDIFLOW_PORT=8080 \
    TZ=UTC

USER 65532:65532
EXPOSE 8080 8081

HEALTHCHECK --interval=15s --timeout=6s --start-period=120s --retries=5 \
  CMD ["java", "-Xshare:auto", "-XX:TieredStopAtLevel=1", "-Xmx16m", "-cp", "/opt/hc", "HealthCheck"]

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.io.tmpdir=/tmp", \
  "org.springframework.boot.loader.launch.JarLauncher"]
