# Etapa de build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Imagem final: Java 21 + Chromium do Playwright 1.63 já instalados
FROM mcr.microsoft.com/playwright/java:v1.63.0-noble
WORKDIR /app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    AICOGWALK_STORAGE_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
RUN mkdir -p /data
COPY --from=build /src/build/libs/*.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
