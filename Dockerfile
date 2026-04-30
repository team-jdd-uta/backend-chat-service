FROM gradle:8.14.4-jdk21-alpine AS build

WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
