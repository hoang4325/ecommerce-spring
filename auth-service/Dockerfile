FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/src auth-service/src

RUN mvn -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/auth-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
