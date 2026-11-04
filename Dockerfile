FROM gradle:8.14-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Door 5 of stack-pack-kotlin-spring: distroless, no shell, non-root.
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
