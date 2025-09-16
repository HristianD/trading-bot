FROM openjdk:17-jdk-slim

# Install Maven
RUN apt-get update && apt-get install -y maven

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Run the application
ENTRYPOINT ["java", "-jar", "target/trading-bot-0.0.1-SNAPSHOT.jar"]