FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build application
RUN ./mvnw clean package -DskipTests

# Run application
EXPOSE 8081
CMD ["java", "-jar", "target/taiwan-stock-kanban-1.0.0-SNAPSHOT.jar"]