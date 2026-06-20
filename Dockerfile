FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q --no-transfer-progress
COPY src ./src
RUN MAVEN_OPTS="-Xms64m -Xmx384m" mvn package -DskipTests -q --no-transfer-progress

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/tontine-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms128m", "-Xmx400m", "-XX:+UseContainerSupport", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
