# Build stage
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG JAR_FILE=target/aimailbox-0.0.1-SNAPSHOT.jar
COPY --from=build /app/target/aimailbox-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}"]