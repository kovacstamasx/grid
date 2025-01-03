FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app
COPY . .
RUN ./gradlew build -x test
CMD ["java", "-jar", "build/libs/substation-client-1.0-all.jar"]
