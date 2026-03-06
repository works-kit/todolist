# ================================
# Stage 1 - Build menggunakan Maven
# ================================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom terlebih dahulu (optimasi caching layer)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build aplikasi (skip test — dijalankan terpisah di CI)
RUN mvn clean package -DskipTests

# ================================
# Stage 2 - Runtime
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy hasil build dari stage pertama
COPY --from=build /app/target/*.jar app.jar

# Port 8080 — app port (server.port=8080 di application.properties)
# Port 8082 — actuator management port (management.server.port=8082 di prod profile)
EXPOSE 8080
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]