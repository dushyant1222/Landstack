FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/landstack-backend-*.jar /src/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 landstack && mkdir -p /data/uploads && chown landstack /data/uploads
USER landstack
WORKDIR /app
COPY --from=build /src/app.jar app.jar
ENV LANDSTACK_STORAGE_DIR=/data/uploads JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
