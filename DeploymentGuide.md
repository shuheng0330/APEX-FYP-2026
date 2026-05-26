# Deployment Guide

This document provides the steps to deploy the **Career Pathway Planning & Learning Management** application (Angular frontend + Spring Boot backend) onto the remote server.

---

## 1. Remote Desktop Connection

Use the provided credentials to log into the remote server:

```
Computer: <server_address>
Username: <username>
Password: <password>
```

---

## 2. Build Angular Frontend Package

1. Navigate to the **frontend** project:

   ```bash
   cd frontend
   ```
2. Check the environment file (e.g., `environment.uat.ts`).
3. Build the package:

   ```bash
   ng build --configuration=<profile>
   # Example:
   ng build --configuration=uat
   ```
4. Locate the build output in:

   ```
   frontend/dist/<project-name>
   ```

   (the folder containing `index.html`).
5. Copy all contents of this folder into:

   ```
   backend/src/main/resources/static
   ```

---

## 3. Database Backup (Optional)

1. Log into the remote server.
2. Connect to the database:

   ```bash
   psql -U <username> -h <hostname> -d <database_name>
   # Example:
   psql -U postgres -h localhost -d career_pathway_planning_learning_management
   ```
3. Dump the database:

   ```bash
   pg_dump -U <username> -d <database_name> -f <output_file_path>
   # Example:
   pg_dump -U postgres -d career_pathway_planning_learning_management -f C:/Users/Administrator/deploy/db/backup/11122025_V10_backup.dump
   ```
4. Naming convention:

   ```
   <date>_<flyway-version>_backup.dump
   ```

---

## 4. Build Spring Boot Backend Jar

1. Navigate to **backend**:

   ```bash
   cd backend
   ```
2. Copy the environment file:

   ```bash
   cp .env.<profile> .env
   # Example:
   cp .env.uat .env
   ```
3. Verify `.env` file content.
4. Copy `.env` to the server:

   ```
   C:/Users/Administrator/deploy
   ```
5. Build the JAR:

   ```bash
   mvn clean package -DskipTests
   ```
6. Copy the JAR to the server:

   ```
   backend/target/career-path-learning-backend-0.0.1-SNAPSHOT.jar
   → C:/Users/Administrator/deploy
   ```

---

## 5. Boot the Application

1. On the server:

   ```bash
   cd C:/Users/Administrator/deploy
   ```
2. Start the backend:

   ```bash
   java -jar career-path-learning-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=<profile>
   # Example:
   java -jar career-path-learning-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat
   ```
3. Watch for **Flyway updates** in logs.
4. Access the app:

   ```
   https://<your-domain>.net
   ```

---

# Quick Commands Cheat Sheet 🚀

### Angular Frontend

```bash
cd frontend
ng build --configuration=uat
# Copy dist/<project-name>/* → backend/src/main/resources/static
```

### Database Backup (Optional)

```bash
psql -U postgres -h localhost -d career_pathway_planning_learning_managment
pg_dump -U postgres -d career_pathway_planning_learning_managment -f C:/Users/Administrator/deploy/db/<date>_<flyway>_backup.dump
```

### Spring Boot Backend

```bash
cd backend
cp .env.uat .env
mvn clean package -DskipTests
# Copy JAR to C:/Users/Administrator/deploy
```

### Start Application

```bash
cd C:/Users/Administrator/deploy
java -jar career-path-learning-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat
```

---
