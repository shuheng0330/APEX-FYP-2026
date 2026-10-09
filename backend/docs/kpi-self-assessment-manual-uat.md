# KPI Self-Assessment: Isolated Manual UAT

## Safety and scope

The original database and `.env` remain unchanged. `apex_manual_uat` is a marked,
disposable local copy, including the inherited schema and Flyway history through V39.
No migrations are replayed and no production code, date rules or system date are changed.
The original 9 October-31 December 2026 review period, its configuration and KPI data
are preserved in both databases. Do not edit the inherited records in the UAT copy.

`tools/ManualKpiAssessmentUat.java` is an explicit local source-file runner, outside
Spring's application source tree. It refuses non-local sources, unmarked targets,
existing UAT databases on cloning, repeated bootstrap/seeding and existing smoke answers.
It captures original business-table fingerprints and checks them after each stage.
It never drops a database, resets an existing assessment or edits applied migrations.

Only disposable identity/Role/RBAC fixtures and the designated Closed state use direct
test-database SQL. Review periods are saved as Draft then published through Phase 1 APIs;
Company plans are published, Department plans submitted/approved and Individual plans
submitted/Superior-approved through the real Phase 2 APIs. All assessments use UC-08 APIs.

Uploads, generated evidence and captured emails stay under ignored `backend/target/manual-kpi-uat/`.
The test SMTP sink listens only on `127.0.0.1:1025`; no real email is sent. UAT uses a
different JWT signing key and the UAT backend binds only to the loopback address.
The database copy and backup contain inherited private data:
keep them local and never commit or share the target directory.

## Start and switch

Run these commands from the backend directory:

```powershell
# One-time setup ONLY if apex_manual_uat does not yet exist.
.\tools\manual-kpi-uat.ps1 -Action Setup

# Connect the existing Angular localhost:4200 UI to the prepared UAT backend on 8081.
.\tools\manual-kpi-uat.ps1 -Action UseUat

# Context/readiness checks without creating or changing assessment answers.
.\tools\manual-kpi-uat.ps1 -Action Verify

# Stop UAT on 8081 and restore the original development connection.
.\tools\manual-kpi-uat.ps1 -Action UseDevelopment

# Check original period/KPI/actor data against the setup baseline.
.\tools\manual-kpi-uat.ps1 -Action CheckSource
```

Setup refuses an existing target instead of overwriting it. It first runs a separate
UAT backend on 8082, leaving the original 8081 backend running. `UseUat` verifies the
fixture and expected processes before switching. It does not modify Angular environment
files. Log out/reload after switching because JWTs and refresh cookies differ.

If restarting after closing the test backend, use `-Action Start` to start UAT on 8082,
then `-Action UseUat`. To reconnect after `UseDevelopment`, likewise use Start then UseUat.
The script refuses to stop an unrecognised process or use an unrelated SMTP listener.

## Disposable accounts

All accounts below use the local-only password **`ApexUat2026!`**.

| Email | Use |
|---|---|
| `uat.monthly@example.test` | Monthly employee manual UAT |
| `uat.quarterly@example.test` | Quarterly employee manual UAT |
| `uat.annually@example.test` | Annual employee manual UAT |
| `uat.superior@example.test` | Immediate Superior of all three; Department creator |
| `uat.md@example.test` | Company plan creator and Department reviewer |
| `uat.setup@example.test` | Review period administrator, not a participant |
| `uat.outsider@example.test` | Active employee deliberately not enrolled |

Separate `uat.smoke.monthly`, `uat.smoke.quarterly`, `uat.smoke.annually` accounts
at `@example.test` are reserved for automated API smoke checks. Their submitted
assessments do not affect the three manual accounts. Do not use them for manual UAT.
Test business authorities are granted to these disposable Roles only, not inherited Roles.

## Prepared scenarios (setup on 9 October 2026)

| Review Period | Performance dates | Final Self deadline | Expected behaviour |
|---|---|---|---|
| UAT - Ready | 1 Oct 2025-30 Sep 2026 | 14 Oct 2026 | Open; all assigned levels; ready to assess |
| UAT - Overdue | 1 Oct 2024-30 Sep 2025 | 5 Oct 2025 | Open; late submission allowed for all three frequencies |
| UAT - Missing Department | 1 Oct 2023-30 Sep 2024 | 5 Oct 2024 | Open; Department plan awaiting approval; Draft allowed, submission blocked |
| UAT - Closed | 1 Oct 2022-30 Sep 2023 | 5 Oct 2023 | Saved partial Drafts visible, all assessment writes blocked |
| UAT - Upcoming | 1 Jan-31 Dec 2027 | Not yet available | Checkpoints visible, assessment editing/submission unavailable |

The setup selects the last completed calendar quarter relative to the real setup date.
Other historical cases move back one year each. The Ready deadline is five days after
setup, so it will naturally become overdue later: no dates are frozen or silently reset.
The exact created dates and IDs are in `target/manual-kpi-uat/fixtures.json`.

Each complete assigned plan has one KPI weighted 100% internally with all five criteria.
Employee-Level allocations come from Phase 1 defaults, not scoring constants in the fixture.
Only the three frequency Roles are selected; the system administrator is excluded.
Frequency, level, Department and Superior context is snapshotted when the period publishes.

## Manual Angular test steps

1. Log in as a manual employee and open **My Performance > My Assessments**.
2. Explicitly select **UAT - Ready**. The default selection may be Upcoming or an older
   available checkpoint; always choose the intended test period and checkpoint yourself.
3. Select September 2026 for Monthly, Quarter 3 - 2026 for Quarterly, or Annual Review -
   2026 for Annually. Checkpoint End Date is 30 September; assessment opens 1 October.
4. Confirm Company, Department and Individual rows, targets and scoring definitions.
5. Leave some points blank; enter a comment and **Save Draft**. Reload and check persistence.
   Submit must explain missing points. Check the unsaved-changes prompt when switching.
6. Attach `target/manual-kpi-uat/evidence.pdf` or `evidence.png` to one KPI. Download it,
   remove it and attach again. Evidence attachment also saves current Draft answers.
7. Try `invalid-evidence.pdf`: it must be rejected. A file exceeding the configurable
   default 10 MiB limit must also be rejected. Evidence is optional.
8. Select every point, click **Submit Assessment** and confirm. Expect **Pending Review**,
   read-only points/comments, no upload/removal actions and downloadable evidence.
9. Select **UAT - Overdue** and its final checkpoint. Repeat submission: it is permitted
   and displayed as Submitted Late. This works for Annual as well as Monthly/Quarterly.
10. Select **UAT - Missing Department**. Save an incomplete Draft successfully; submission
    must state that Department KPIs are not yet approved/fully assigned. Optionally log in
    as `uat.md`, approve that period's plan in **KPI Management > KPI Review**, then refresh
    the employee assessment. The new Department row should appear without losing answers.
11. Select **UAT - Closed**. The seeded comment/point remain readable; saving, submission
    and evidence mutation are blocked. Closed was set by a scoped fixture, not an HR
    closure workflow; there is still no production Close API or invented closure rule.
12. Select **UAT - Upcoming**. Editing is disabled and the availability reason is visible.
13. Log in as `uat.outsider`. No test period should be available. Through authenticated
    browser developer-tool requests, try another employee's assessment/evidence ID or a
    checkpoint of another frequency: access must be denied, even with guessed IDs.

Repeat the Draft/evidence/submission journey with all three manual employee accounts.
There is no Superior assessment completion UI in Slice 1; do not expect Pending Review
to become Reviewed during these tests. Self points must not create an official score.

## Verification already exercised by the setup

The context verifier checks all three frequencies across Ready, Overdue, Missing Department,
Closed and Upcoming; correct assignments; non-participant exclusion and foreign-frequency
denial. It creates no assessment rows. Six separate smoke journeys (three frequencies x
on-time/late) exercise Draft creation, missing-point rejection, content-verified evidence,
download/removal, submission, duplicate/edit denial and scoped employee/Superior access.
Existing fixed-clock unit tests cover exact opening/deadline-day boundaries without
changing Windows time or adding any production date override.

Verified on 9 October 2026: 120 focused backend tests passed, the backend package
build succeeded, and all six HTTP submission journeys passed. Additional checks
passed for missing Department assignments, oversized evidence, private Draft access,
foreign assignments, Closed-period writes and Upcoming checkpoint availability.
Switching back to the original development backend was also verified before
leaving the app connected to UAT for manual testing.

Captured `.eml` files are under `target/manual-kpi-uat/mail/`. `source-backup.dump`
and source fingerprints allow verification that the inherited data was not altered.

Suggested commit: `test(assessment): add isolated manual UAT setup`
