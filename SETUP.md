# APEX Project — Teammate Setup Guide

## Prerequisites

Install these tools first:

| Tool | Version | Download |
|---|---|---|
| **Java JDK** | 17+ | https://adoptium.net |
| **Maven** | 3.8+ | Bundled via `mvnw` in repo |
| **Node.js** | 18+ (LTS) | https://nodejs.org |
| **PostgreSQL** | 14+ | https://www.postgresql.org/download/ |
| **Git** | any | https://git-scm.com |

---

## 1. Clone the Repository

```bash
git clone <repo-url>
cd "APEX Staff Appraisal and Performance Analytics"
```

---

## 2. Database Setup

Open **pgAdmin** or `psql` and create the database:

```sql
CREATE DATABASE apex_db;
```

> Flyway handles all schema creation automatically on first boot — **do not manually run any SQL migration files**.

---

## 3. Backend Setup

### 3a. Create the `.env` file

```bash
cd backend
cp .env.example .env
```

Edit `backend/.env` with your values:

```env
APP_NAME=Adaptive Career Pathway and Learning Management System

# Database
DB_URL=jdbc:postgresql://localhost:5432/apex_db
DB_USER=postgres
DB_PASS=your_postgres_password

# Backend port
SERVER_PORT=8081

# Frontend CORS
FRONTEND_ORIGIN=http://localhost:4200

# SMTP (use Gmail App Password or any SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_STARTTLS=true
MAIL_FROM_NO_REPLY=your-email@gmail.com

# JWT (any long random string, min 32 chars)
JWT_SECRET=your-random-secret-key-at-least-32-chars

# File storage (absolute path to a folder you create)
FILE_UPLOAD_DIR=C:/apex-uploads/
TEMPLATE_DIR=data-upload-template/
```

> SSL fields (`KEY_STORE`, `KEY_STORE_PASSWORD`, `KEY_STORE_TYPE`) are **not needed** for the dev profile.

### 3b. Run the backend

```bash
# From the backend/ directory
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
```

On first run, Flyway will automatically execute all migration scripts (V2–V24) and seed the test data. Backend runs at **http://localhost:8081**.

---

## 4. Frontend Setup

```bash
# From the frontend/ directory
npm install
ng serve
```

Frontend runs at **http://localhost:4200**.

---

## 5. Test Accounts

All seeded accounts use the password: **`Password@123`**

| Name | Email | Role | Permissions |
|---|---|---|---|
| Shu Heng (superadmin) | `thongshuheng0330@gmail.com` | Superadmin | Full access |
| Alice Johnson | `alice.johnson@apex.dev` | IT Manager | Manage Evaluation |
| David Lim | `david.lim@apex.dev` | HR Manager | Manage Evaluation, View Staff |
| Frank Nguyen | `frank.nguyen@apex.dev` | Sales Manager | Manage Evaluation |
| Bob Smith | `bob.smith@apex.dev` | Software Engineer | Basic (ROLE_USER) |
| Carol Tan | `carol.tan@apex.dev` | HR Officer | View Staff |
| Grace Wong | `grace.wong@apex.dev` | Sales Executive | Basic (ROLE_USER) |

---

## 6. Quick Sanity Check

After both servers are running:

1. Open **http://localhost:4200**
2. Log in with `alice.johnson@apex.dev` / `Password@123`
3. You should see the dashboard with evaluation data pre-populated

---

## Common Issues

**Flyway fails on startup**
The database does not exist yet. Create `apex_db` in PostgreSQL first, then restart the backend.

**Port 8081 already in use**
Change `SERVER_PORT` in `backend/.env` to a free port (e.g. `8082`).

**`ng` command not found**
Install the Angular CLI globally: `npm install -g @angular/cli`, or prefix commands with `npx` (e.g. `npx ng serve`).

**`FILE_UPLOAD_DIR` errors on startup**
Create the upload folder manually (e.g. `C:\apex-uploads\`) and make sure the path in `.env` uses an absolute path with a trailing slash.
