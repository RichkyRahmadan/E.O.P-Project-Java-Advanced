# ============================================================
# E.O.P Support & Oracle Service — Dockerfile
# Port 8083 | MongoDB (eop_support_db) + Gemini AI + JavaMail
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

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
