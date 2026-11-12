FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
COPY --from=frontend-build /frontend/dist/ backend/src/main/resources/static/
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
# Fonts for watermarked previews (Java 2D text rendering).
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/framebucket-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-8080}"]
