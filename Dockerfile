FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/transaction-platform-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]