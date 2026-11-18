FROM eclipse-temurin:17-jre
WORKDIR /app
COPY app/server/target/dorm-repair-server-1.0.0.jar app.jar
RUN useradd --system --uid 10001 appuser && mkdir -p /app/data/uploads && chown -R appuser:appuser /app
USER appuser
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
