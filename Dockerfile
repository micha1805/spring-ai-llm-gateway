# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /src
COPY --link .mvn .mvn
COPY --link mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:resolve dependency:resolve-plugins
COPY --link src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre-noble AS runtime
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --link --from=build --chown=10001:10001 /src/target/llm-gateway.jar /app/llm-gateway.jar
RUN mkdir -p /app/onnx-cache && chown -R 10001:10001 /app
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/llm-gateway.jar"]
