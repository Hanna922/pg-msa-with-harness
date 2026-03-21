FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY common ./common
COPY card-authorization-service ./card-authorization-service
WORKDIR /workspace/card-authorization-service
RUN sed -i 's/\\r$//' gradlew && chmod +x gradlew && sh ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/card-authorization-service/build/libs/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
