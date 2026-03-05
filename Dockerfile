# ================================
# Stage 1 - Build menggunakan Maven
# ================================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom terlebih dahulu (optimasi caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build aplikasi
RUN mvn clean package -DskipTests

# ================================
# Stage 2 - Runtime
# ================================
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy hasil build dari stage pertama
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]