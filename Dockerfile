FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 aquaalerta \
    && useradd --system --uid 10001 --gid aquaalerta aquaalerta
COPY --from=build --chown=10001:10001 /workspace/target/aquaalerta-*.jar /app/aquaalerta.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/aquaalerta.jar"]
