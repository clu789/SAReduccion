# Guía de Entorno Docker - SAReducción

Este documento explica cómo configurar, levantar y administrar el entorno de desarrollo del sistema **SAReducción** utilizando Docker.

## 📋 Requisitos Previos

1. **Docker Desktop** (o Docker Engine + Docker Compose) instalado y ejecutándose.
   - [Descargar Docker Desktop](https://www.docker.com/products/docker-desktop/)
2. Git (Opcional, para clonar el repositorio).

## ⚙️ Configuración Inicial

Antes de levantar los contenedores, asegúrate de tener configurado tu archivo `.env` en la raíz del proyecto. Este archivo debe contener las credenciales y puertos de la base de datos (por ejemplo, el usuario `ORACLE_APP_USER` y su contraseña).

## 🚀 Levantando los Contenedores

El proyecto está dividido en varios servicios dentro del archivo `docker-compose.yml`. Puedes levantar partes específicas del proyecto según lo que necesites probar.

### 1. Solo levantar el Frontend
Si solo vas a trabajar o visualizar el diseño y la maquetación HTML/JS:
```bash
docker compose up frontend -d
```
> 🌐 **Acceso:** http://localhost:8081/solicitante/login.html o http://localhost:8081/administrativo/login.html

### 2. Solo levantar la Base de Datos (Oracle XE)
Si estás desarrollando el Backend localmente en tu IDE (Eclipse/IntelliJ) y solo necesitas la base de datos:
```bash
docker compose up oracle-db -d
```
> 🛢️ **Acceso:** `localhost:1521`
> *Nota: La primera vez que se ejecuta, Oracle tarda un par de minutos en inicializar la base de datos y correr los scripts de creación de tablas de `docker/init-db/`.*

### 3. Levantar Todo (Front, Back y Base de Datos)
Si tienes el backend listo (con su Dockerfile habilitado) y quieres probar el ecosistema completo:
```bash
docker compose --profile full-stack up -d
```

## 🛑 Detener y Limpiar el Entorno

Para detener los contenedores sin borrar los datos de la base de datos:
```bash
docker compose down
```

Para detener los contenedores y **BORRAR** la base de datos (útil si modificaste los scripts SQL y necesitas que se vuelvan a ejecutar desde cero):
```bash
docker compose down -v
```

## 🔍 Comandos Útiles (Troubleshooting)

**Ver los logs de un contenedor (ej. la base de datos):**
```bash
docker compose logs -f oracle-db
```

**Ver el estado de los contenedores:**
```bash
docker compose ps
```

## 🌐 Puertos Utilizados

Si tienes conflictos con otras aplicaciones (como XAMPP, Tomcat o servicios locales), verifica estos puertos:
- **`8081`** - Frontend (Nginx)
- **`8080`** - Backend (Spring Boot - API REST)
- **`1521`** - Base de Datos (Oracle XE)
