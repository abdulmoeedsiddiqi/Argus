# Generic image for any Argus module, built in two stages so that one command
# (docker compose up --build) is enough on a clean machine (QR-19).
#
#   docker build -f deploy/Dockerfile --build-arg MODULE=argus-gateway .

# Stage 1: compile and package the chosen module (and the modules it needs) with Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
# Tests run in CI and with ./mvnw verify, not while building the image.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -pl ${MODULE} -am package

# Stage 2: only the JRE and the runnable jar end up in the final image.
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/*-exec.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
