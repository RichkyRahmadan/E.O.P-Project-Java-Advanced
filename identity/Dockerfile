# ============================================================
# E.O.P Identity Service — Dockerfile
# Port 8081 | PostgreSQL (eop_identity_db)
# ============================================================

# --- STAGE 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -DskipTests -q

# --- STAGE 2: Run ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S eop && adduser -S eop -G eop
USER eop

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
