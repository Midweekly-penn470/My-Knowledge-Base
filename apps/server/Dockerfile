FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY apps/server/pom.xml ./apps/server/pom.xml
RUN mvn -pl apps/server -am dependency:go-offline

COPY apps/server ./apps/server
RUN mvn -pl apps/server -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/apps/server/target/server-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
