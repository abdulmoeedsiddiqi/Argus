# Generic runtime image for any Argus module. Build the jar first: mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
