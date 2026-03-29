FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw clean package -DskipTests -q
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "target/wallet-service-0.0.1-SNAPSHOT.jar"]