# Annual KPI Phase 1

V27 adds only the four annual-review foundation tables and nullable role review-frequency default.
The application's existing `ddl-auto: update` and `flyway.enabled: false` remain unchanged.

Do not start the application with the new entities before applying V27: Hibernate may otherwise
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
when history is empty, and discovers **only** this directory's V27. Baseline 26 does not claim that
V2-V26 were executed. Preserve applied migration files and the generated backup.
Already-applied V27 is validated, not replayed.
Flyway cannot baseline an existing empty history table. After backup, the runner locks and
reinitialises only that zero-row metadata table. It never removes a nonempty migration history.

## Verification

```powershell
$env:APEX_PHASE1_POSTGRES_TEST = 'true'
.\mvnw.cmd '-Dtest=AnnualReviewPeriodFoundationTest,AnnualReviewPeriodPostgresTest' test
```

The PostgreSQL test runs the migration and JPA repositories in a temporary transactional schema,
then rolls back. It does not start Spring or run legacy migrations. Normal test runs skip this opt-in test.

## Deliberate boundaries

- This is a persistence foundation, not the complete Review Period REST/UI workflow.
- Partial drafts are stored; published configurations require complete dates, scoring settings and valid totals.
- Role frequencies and participant role/department/superior labels are annual snapshots.
- Date-only deadlines have no invented time-of-day or holiday rules; generated offsets use calendar days.
- No automatic closure, eligibility inference, transfer/resignation handling or KPI/Appraisal behaviour is added.
- The consolidation method is stored but no annual scores are calculated in Phase 1.
- Review-period publication/overlap concurrency and closure prerequisites belong to later workflow implementation.
