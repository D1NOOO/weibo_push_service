# 使用 Docker Hub 官方镜像
FROM eclipse-temurin:21-jdk-alpine as builder
WORKDIR /app
COPY . .
RUN apk add --no-cache maven
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p data
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]