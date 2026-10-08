# Annual KPI Foundation And KPI Plans

V27 adds only the four annual-review foundation tables and nullable role review-frequency default.
V28 adds the dedicated annual KPI review period authority and grants it to the active `superadmin` role.
It retains legacy authority records/grants and extends the inherited authority enum CHECK constraint.
V29 adds six Employee Levels, configurable Role classification and independent per-period level weights.
Old global weight columns remain as migration evidence but are no longer mapped or used at runtime.
V30 marks Super Admin as ineligible for performance reviews and replaces four active setup deadlines with one.
The old deadline columns remain as history; the single deadline is backfilled from their latest date.
V31 introduces whole KPI plans, their items, 1-5 scoring definitions and materialised assignments.
V32 adds the annual publication-time participant snapshot marker and Company plan publication metadata.
V33 adds Department plan submission/review metadata, return reasons, validation constraints and two business authorities.
V34 adds Individual plan submission routing to the immediate Superior, review constraints and a reviewer authority.
The application's existing `ddl-auto: update` and `flyway.enabled: false` remain unchanged.

Do not start the application with the new entities before applying V27-V34: Hibernate may otherwise
create untracked tables without the migration's constraints. Do not enable the legacy Flyway location.

## Local migration

Run from the backend directory with Java 17 or newer and the project's `.env`:

```powershell
.\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/phase1-classpath.txt'
$classpath = (Get-Content -LiteralPath target/phase1-classpath.txt -Raw).Trim()
java --class-path $classpath ApplyAnnualReviewPeriodFoundation.java --apply 'C:\Program Files\PostgreSQL\18\bin\pg_dump.exe'
```

The runner checks the inherited IDs, migration history and absence of partial Phase 1 structures,
backs up the local database under the user's `.apex/database-backups` directory, explicitly records the inherited schema as baseline 26
when history is empty, and discovers **only** this directory's V27-V34. Baseline 26 does not claim that
V2-V26 were executed. Preserve applied migration files and the generated backup.
Already-applied migrations are validated, not replayed. A backup is required before any pending migration.
V30 refuses existing Super Admin participants or missing/invalid published setup deadline backfills rather than altering historical data.
It can reconcile a verified nullable DATE setup-deadline column already created by development Hibernate,
preserving any saved value. Incompatible or other partial untracked structures still stop the rollout.
V28 refuses missing/ambiguous active Super Admin roles instead of guessing permission recipients.
V29 copies actual old global weights into all six period-level rows, including incomplete Draft values.
It leaves inherited Role classifications unset. It refuses existing participants or published Role configuration
because their historical level associations need an explicit, reviewed mapping. Do not guess using Role names
or current Role assignments; resolve that backfill in a separately reviewed rollout before applying V29 there.
Flyway cannot baseline an existing empty history table. After backup, the runner locks and
reinitialises only that zero-row metadata table. It never removes a nonempty migration history.

## Verification

```powershell
$env:APEX_PHASE1_POSTGRES_TEST = 'true'
$env:APEX_PHASE2_POSTGRES_TEST = 'true'
.\mvnw.cmd '-Dtest=AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest,AnnualReviewPeriodPermissionPostgresTest,EmployeeLevelMigrationPostgresTest,ReviewPeriodRefinementMigrationPostgresTest' test
```

The PostgreSQL test runs the migration and JPA repositories in a temporary transactional schema,
then rolls back. It does not start Spring or run legacy migrations. Normal test runs skip this opt-in test.
Run `KpiPlanPostgresTest` for the Company plan round-trip, real participant cascade, duplicate-assignment
and cross-period foreign-key checks. Both opt-in test schemas are rolled back without changing live records.

## Slice 2 Rollout

- Annual publication (direct create-and-publish or saved-Draft publication) enrols the initial active,
  non-deleted staff roster for the selected performance-review-eligible Roles. Super Admin is excluded
  through Role eligibility, not Employee Level. Preview, Draft saving and automatic opening never re-enrol.
- Existing published periods intentionally keep a NULL snapshot marker. Do not infer historical rosters.
  Use a fresh annual period for UAT; historical reconciliation needs explicit business review.
- Published Upcoming periods permit only name/date/deadline changes. Participant and configuration IDs,
  frequencies, allocations, composition and consolidation remain frozen. Open/Closed periods are read-only.
- A complete Company plan totals 100% within its level, independent of Employee-Level allocations.
  `POST /api/company-kpi-plans/{id}/publish` publishes and assigns all items atomically to existing participants.
  A passed setup deadline is not a hard lock. Action-time publication lateness is preserved separately.
- `CAN_MANAGE_COMPANY_KPI` must be granted through existing RBAC to a verified MD/Top Management Role.
  The migration does not guess recipients or grant it to Super Admin.
- Slice 2 stops at V32. Slice 3 adds Department workflow APIs only; Individual approval and assistance are not exposed yet.

## Slice 3 Backend

- The inherited Job Role organisation node determines an HOD's Department, directly or through its
  nearest unambiguous Department ancestor. `CAN_MANAGE_DEPARTMENT_KPI` must additionally be explicitly
  granted to a verified HOD Role. Neither Employee Level nor a Manager title grants business authority.
- `CAN_APPROVE_DEPARTMENT_KPI` is granted explicitly through the existing access-control UI.
  Reviewer access follows this permission, not the account's Job Role name or performance-review eligibility.
  No Role, including Super Admin, receives the permission automatically.
- All routes are under `/api/department-kpi-plans`:
  `GET /`, `/periods`, `/departments`, `/{id}`; `POST /`; `PUT /{id}`;
  `POST /{id}/submit`; reviewer-only `GET /pending`, `POST /{id}/approve`, `POST /{id}/return`.
  Return accepts `{ "reason": "Clarify the target" }`.
- HOD reads/edits are restricted to their resolved Department and require an eligible business Role.
  Any active, non-deleted account with the review permission can read and decide Department plans.
- Draft/Returned plans can be incomplete. Submission requires complete KPI items, five scoring criteria
  per item and an exact 100% within-level total. Pending and Approved contents are immutable.
- Approval rechecks completeness and requires an Upcoming/Open period with a confirmed participant roster.
  It atomically assigns every item only to matching snapshotted Department participants; no enrolment occurs.
- Return requires a nonblank reason, retained during revision. Resubmission clears the previous decision
  and records the latest submission. No separate decision-history or workflow-engine table is introduced.
- Late setup, submission and review are allowed while the period is not Closed. Submission/review lateness
  is saved at action time, independent of current overdue indicators and later deadline changes.
- Shared transaction and plan locks serialize competing writes and prevent duplicate approval/cascade.
  V33 is forward-only, preserves existing Company plans and grants, and does not change Hibernate/Flyway settings.
  It can reconcile all seven verified nullable review columns created by development Hibernate; partial
  or incompatible columns are refused. The migration adds the required foreign keys and checks after backup.
- Department frontend is implemented; Slice 4 adds backend functionality only and stops before its frontend.

## Slice 4 Backend

- Employees manage one Individual KPI plan per Annual Review Period in which they are enrolled.
  Draft/Returned plans are editable, including after the target KPI Setup Deadline while the period remains writable.
- Submission requires a complete plan with five scoring definitions per item and exactly 100% within-level weightage.
  It routes to the employee's active immediate Superior from the inherited Staff manager relationship.
- The routed Superior also needs `CAN_REVIEW_INDIVIDUAL_KPI`, assigned through existing RBAC. This migration
  does not guess which Roles should receive it. Reviewers may approve or return the whole plan; return requires a reason.
- Approval atomically assigns all Individual KPI items only to the plan owner's existing Review Period participant.
  Neither submission nor approval creates participants. Pending/Approved plans cannot be edited.
- Review checks both the stored submission route and the current reporting relationship. Automatic rerouting after
  a Manager change, assisted creation and KPI revision remain outside Slice 4.
- V34 is forward-only and refuses pre-existing non-Draft Individual plans with unknown routing. It can reconcile
  a verified nullable UUID routing column created by development Hibernate, then adds the foreign key and checks.
  Existing Company and Department plan data and permissions are preserved.

## Deliberate boundaries

- Review Period management and Company plan Draft/publication REST/UI workflows are implemented.
- Partial drafts are stored; published configurations require complete dates, scoring settings and valid totals.
- Role frequencies and Employee Level configuration references are annual snapshots; participant role/department/superior labels remain directly snapshotted.
- Date-only deadlines have no invented time-of-day or holiday rules; generated offsets use calendar days.
- No automatic closure, additional eligibility inference, participant refresh or transfer/resignation handling is added.
- KPI revisions and later assessment, attitude, appraisal and analytics workflows remain outside these slices.
- The consolidation method is stored but no annual scores are calculated in Phase 1.
- Publication and overlap rules are enforced by the Phase 1 application service; closure prerequisites remain unresolved.
