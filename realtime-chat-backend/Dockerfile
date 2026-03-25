# Build Stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy the Gradle artifacts and source code
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Build the application (with constrained memory for Render free tier)
RUN ./gradlew bootJar -x test --no-daemon --no-watch-fs --max-workers=1 -Dorg.gradle.jvmargs="-Xmx300m"

# Package Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
