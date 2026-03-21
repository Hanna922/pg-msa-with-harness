FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY . .
RUN sed -i 's/\\r$//' gradlew && chmod +x gradlew && sh ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]