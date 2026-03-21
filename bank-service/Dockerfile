FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY common ./common
COPY bank-service ./bank-service
WORKDIR /workspace/bank-service
RUN sed -i 's/\\r$//' gradlew && chmod +x gradlew && sh ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/bank-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
