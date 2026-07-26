FROM maven:3.9-eclipse-temurin-21-alpine as builder
WORKDIR /app
COPY pom.xml settings.xml ./
RUN mkdir -p /root/.m2 && cp settings.xml /root/.m2/settings.xml && mvn dependency:go-offline -DskipTests
COPY src/ src/
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN apk add --no-cache curl tzdata \
    && addgroup -S app && adduser -S app -G app \
    && mkdir -p data && chown -R app:app /app
COPY --from=builder /app/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
CMD ["java", "-jar", "app.jar"]
