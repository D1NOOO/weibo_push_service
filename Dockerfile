FROM maven:3.9-eclipse-temurin-21-alpine as builder
WORKDIR /app
COPY pom.xml settings.xml ./
RUN mkdir -p /root/.m2 && cp settings.xml /root/.m2/settings.xml && mvn dependency:go-offline -DskipTests
COPY src/ src/
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p data
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]