# ============================================================
# Dockerfile – Backend Spring Boot 2.7.18 / Java 8
# SAReducción – Instituto Tecnológico de Toluca
#
# Construcción en dos etapas:
#   1. Build   →  Maven compila y empaqueta el JAR
#   2. Runtime →  JRE mínimo ejecuta el JAR
# ============================================================

# ── ETAPA 1: Compilación con Maven ──────────────────────────
FROM maven:3.8.8-eclipse-temurin-8 AS build

WORKDIR /app

# Copiar solo el pom.xml primero para aprovechar caché de Maven
# (las dependencias solo se descargan si el pom cambia)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── ETAPA 2: Runtime mínimo ──────────────────────────────────
FROM eclipse-temurin:8-jre-alpine

LABEL maintainer="ITT-SAReduccion"
LABEL description="Backend API REST - Sistema de Reduccion de Monto de Inscripcion"

WORKDIR /app

# Copiar el JAR generado por Maven
COPY --from=build /app/target/*.jar app.jar

# Variables de entorno con valores por defecto
# (se sobreescriben desde docker-compose.yml o el entorno real)
ENV SPRING_PROFILES_ACTIVE=dev
ENV SERVER_PORT=8080

# Exponer el puerto de la API REST
EXPOSE 8080

# Opciones JVM para Java 8 en contenedor
# -Xmx512m          → máximo de heap
# -Djava.security.egd → acelera el arranque en Linux
ENTRYPOINT ["java", \
  "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", \
  "-jar", "app.jar"]
