# Build do backend CO-TI (Spring Boot / yudao-server)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests -pl yudao-server -am

FROM eclipse-temurin:21-jre
# ping: monitores do tipo ping (ICMP sem root: o Docker libera net.ipv4.ping_group_range)
RUN apt-get update && apt-get install -y --no-install-recommends iputils-ping && rm -rf /var/lib/apt/lists/*
RUN groupadd --system coti && useradd --system --gid coti --home /app coti \
    && mkdir -p /app/logs /app/arquivos && chown -R coti:coti /app
WORKDIR /app
COPY --from=build /src/yudao-server/target/yudao-server.jar app.jar
USER coti
ENV SPRING_PROFILES_ACTIVE=coti \
    CO_TI_LOG_DIR=/app/logs \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 48080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD bash -c '</dev/tcp/127.0.0.1/48080' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
