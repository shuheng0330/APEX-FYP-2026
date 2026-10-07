# Annual KPI Phase 1

V27 adds only the four annual-review foundation tables and nullable role review-frequency default.
V28 adds the dedicated annual KPI review period authority and grants it to the active `superadmin` role.
It retains legacy authority records/grants and extends the inherited authority enum CHECK constraint.
V29 adds six Employee Levels, configurable Role classification and independent per-period level weights.
Old global weight columns remain as migration evidence but are no longer mapped or used at runtime.
V30 marks Super Admin as ineligible for performance reviews and replaces four active setup deadlines with one.
The old deadline columns remain as history; the single deadline is backfilled from their latest date.
The application's existing `ddl-auto: update` and `flyway.enabled: false` remain unchanged.

Do not start the application with the new entities before applying V27-V30: Hibernate may otherwise
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
when history is empty, and discovers **only** this directory's V27-V30. Baseline 26 does not claim that
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
.\mvnw.cmd '-Dtest=AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest,AnnualReviewPeriodPermissionPostgresTest,EmployeeLevelMigrationPostgresTest,ReviewPeriodRefinementMigrationPostgresTest' test
```

The PostgreSQL test runs the migration and JPA repositories in a temporary transactional schema,
then rolls back. It does not start Spring or run legacy migrations. Normal test runs skip this opt-in test.

## Deliberate boundaries

- This is a persistence foundation, not the complete Review Period REST/UI workflow.
- Partial drafts are stored; published configurations require complete dates, scoring settings and valid totals.
- Role frequencies and Employee Level configuration references are annual snapshots; participant role/department/superior labels remain directly snapshotted.
- Date-only deadlines have no invented time-of-day or holiday rules; generated offsets use calendar days.
- No automatic closure, additional eligibility inference, transfer/resignation handling or KPI/Appraisal behaviour is added.
- The consolidation method is stored but no annual scores are calculated in Phase 1.
- Publication and overlap rules are enforced by the Phase 1 application service; closure prerequisites remain unresolved.
