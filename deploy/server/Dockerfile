FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY app/server/target/dorm-repair-server-1.0.0.jar app.jar
RUN useradd --system --uid 10001 appuser && mkdir -p /app/data/uploads && chown -R appuser:appuser /app
COPY deploy/server/healthcheck.sh /usr/local/bin/healthcheck.sh
RUN chmod 0555 /usr/local/bin/healthcheck.sh
USER appuser
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
