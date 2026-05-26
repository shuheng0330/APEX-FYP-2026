# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

APEX is an HR/talent management platform for staff appraisal and performance analytics. It combines career pathway planning, competency tracking, training management, learning materials, and performance evaluation into a single integrated system.

**Stack**: Angular 19 (frontend) + Spring Boot 3.5 (backend) + PostgreSQL + Flyway

## Development Commands

### Frontend (`cd frontend`)

```bash
npm install              # Install dependencies
ng serve                 # Dev server at http://localhost:4200
ng build --configuration=uat   # Production build → dist/frontend/
ng test                  # Run Karma/Jasmine tests
ng test --include="**/foo.spec.ts"  # Run a single test file
```

### Backend (`cd backend`)

```bash
# First-time setup
cp .env.example .env     # then fill in values

# Run dev server (port 8081)
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev

# Build production JAR
mvn clean package -DskipTests

# Run production JAR
java -jar target/career-path-learning-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=uat
```

### Full Deployment Workflow

1. `ng build --configuration=uat` in `frontend/`
2. Copy `frontend/dist/frontend/` contents → `backend/src/main/resources/static/`
3. `mvn clean package` in `backend/`
4. Deploy and run the JAR with `--spring.profiles.active=uat`

The backend serves the Angular SPA from `/static/` and exposes REST APIs at `/api/**`.

## Required Environment Variables

Copy `backend/.env.example` to `backend/.env` and configure:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | PostgreSQL connection |
| `FRONTEND_ORIGIN` | CORS allowed origin (e.g., `http://localhost:4200`) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP for email notifications |
| `JWT_SECRET` | JWT signing secret |
| `FILE_UPLOAD_DIR`, `TEMPLATE_DIR` | File storage paths |
| `SERVER_PORT` | Backend port |
| `KEY_STORE`, `KEY_STORE_PASSWORD`, `KEY_STORE_TYPE` | SSL (UAT/prod only) |

## Architecture

### Frontend (`frontend/src/app/`)

Angular 19 using **standalone components** (no NgModules). All routes are protected by guards.

- `pages/` — Feature pages grouped by domain (evaluation, training, learning, career pathway, staff, org chart, login)
- `components/` — Reusable UI components (side-menu, table, modals, drawers)
- `services/` — HTTP services for each API domain
- `models/` — TypeScript interfaces shared across features
- `guards/` — `AuthGuard`, `LoginGuard`, `PermissionGuard` applied per-route
- `interceptors/` — JWT injection, error handling, loading state
- `app.routes.ts` — Central route configuration with lazy loading

**UI libraries**: ng-zorro-antd (Ant Design), Angular Material, Chart.js/ng2-charts, ng2-pdf-viewer, ngx-interactive-org-chart.

### Backend (`backend/src/main/java/com/tbm/careerpathlearning/`)

Standard Spring layered architecture: **Controller → Service → Repository**.

- `controller/` — 28+ REST controllers, one per domain
- `service/` — Service interfaces + `impl/` implementations
- `repository/` — Spring Data JPA repositories
- `model/` — JPA entities
- `dto/` — Request/response DTOs (never expose entities directly)
- `mapper/` — MapStruct mappers for entity↔DTO conversion
- `config/` — Security, CORS, JWT filter, message config
- `aspect/` — AOP logging
- `scheduler/` — Scheduled background tasks
- `enums/` — Domain enumerations
- `exception/` — Custom exception classes

### Database

PostgreSQL managed by Flyway with 20 versioned migrations (`V1`–`V20`) in `backend/src/main/resources/db/migration/`. Flyway uses baseline versioning with out-of-order support. New schema changes must be added as new numbered migration scripts — never edit existing ones.

## Security Model

- **Authentication**: JWT tokens injected by `JwtAuthenticationFilter`; stateless sessions
- **Authorization**: Method-level `@PreAuthorize` on service/controller methods
- **Permissions**: Fine-grained permissions such as `CAN_MANAGE_TRAINING`, `CAN_MANAGE_EVALUATION`, `CAN_MANAGE_STAFF`, `CAN_PROPOSE_ROLE_COMPETENCIES`, plus `ROLE_USER` for basic access
- **Account lockout**: 5 failed login attempts
- **OTP expiry**: 5 minutes for password reset flows
- **Profiles**: `dev` uses HTTP on port 8081; `uat` uses HTTPS on port 8443

## Key Patterns

- **DTO pattern is mandatory**: Controllers accept/return DTOs; MapStruct handles conversion — never expose JPA entities in API responses
- **Angular new control flow**: Use `@if`, `@for`, `@switch` (Angular 17+ syntax), not `*ngIf`/`*ngFor` directives
- **Reactive streams**: Use RxJS observables in services; avoid nested subscriptions — prefer `switchMap`/`forkJoin`
- **Bulk data upload**: Excel templates (Apache POI) are in `backend/src/main/resources/data-upload-template/`; PDF processing uses PDFBox
- **Async operations**: Heavy processing uses Spring `@Async`; email sending is always async
- **i18n**: `ngx-translate` is wired up — use translation keys rather than hardcoded strings in templates
