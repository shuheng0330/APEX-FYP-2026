# UC-05: Controlled KPI Plan Revisions (Backend Checkpoint)

## Scope and Safety

V44 is forward-only, under `db/migration/annual-kpi`. Run the backed-up isolated
`ApplyAnnualReviewPeriodFoundation.java` runner; do not change global Flyway/baseline/Hibernate settings.
It validates existing Phase 1-3 row digests across migration. All existing plans, including
Individual plans and incomplete Drafts, become Version 1 without replacing any item, assignment,
answer or evidence ID. Effective definitions and cancelled proposals are immutable in PostgreSQL.
Existing V43 approval-return feedback stays on the plan and is not revision history.

Company, Department and Individual exceptional revision backends are implemented. Normal Individual
approval/return and assistance are unchanged. Individual revisions reuse the existing frontend revision workspace.
Frontend revision controls, Target reminders and assessment version-token integration are implemented
for all three plan levels. Frontend manual UAT and subsequent integration testing await approval.
No Role grants are inferred. Provision the four revision authorities through existing RBAC after verification.

## Individual Revisions (V45)

V45 adds nullable preparation mode and routed-Superior fields to existing version records, without
changing existing version IDs, item definitions, assignments, assessment bindings, answers or scores.
The backed-up isolated runner verifies preservation, including all existing version records.
It can adopt precisely validated, empty nullable columns added by development Hibernate hot reload;
partial/incompatible or populated untracked metadata is rejected. No applied migration is edited.

`CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS` permits verified HR/MD users to initiate revisions and prepare
their own HR/MD-mode cases. It does not approve revisions or grant Company revision access.
`CAN_REVIEW_INDIVIDUAL_KPI` plus the live `Staff.manager` relationship permits the recorded Superior's
preparation and approval actions. `CAN_MANAGE_KPI_AMENDMENTS` retains access control and cancellation,
not item editing. No permission grants or business actor identities are inferred.

- `POST /api/individual-kpi-plans/{planId}/revision-requests`: `{ "reason": "...", "preparationMode": "SUPERIOR" }`.
- Modes are `SUPERIOR` (default) or `HR_MD`. If the initiator is also the immediate Superior, the server
  selects `SUPERIOR` even when `HR_MD` is supplied. Scope comes from the existing approved plan.
- Superior mode: `REQUESTED -> prepare -> DRAFT -> confirm -> EFFECTIVE`.
- HR/MD mode: `REQUESTED -> initiating HR/MD prepares -> DRAFT -> submit -> PENDING_APPROVAL
  -> immediate Superior approves -> EFFECTIVE`.
- The Superior may return an HR/MD proposal with a mandatory reason. Only its recorded preparer can
  edit Returned contents; meaningful saved changes are required before resubmission.
- Reuse existing revision `prepare`, `PUT`, `submit`, `approve`, `return`, `confirm`, `cancel`, queue,
  details and history routes, lock tokens, comparisons and server-generated allowed actions.
- **Superior confirmation is approval**, not an additional stage: both confirmation and approval
  use one atomic finalisation method. `approvedBy`, `approvedAt`, `confirmedBy`, `confirmedAt` and
  `effectiveAt` record the Superior decision, with matching approval/confirmation/effective timestamps.
  The response `id`/`versionNumber` and the plan's `currentVersionId` identify the effective version.
  Decision history records `SUPERIOR_APPROVED`. Original plan approval metadata stays unchanged.
- The employee and routed Superior must remain active. The Superior must retain the review authority
  in existing RBAC and remain the employee's manager. Changes freeze writes; there is no rerouting.
  Other Superiors cannot inspect unfinished proposals. Employees see Effective/Superseded history only.
- The initiator can withdraw their own Requested case before preparation while amendments are enabled.
  Amendment managers may cancel any unfinished case despite disabled/expired access or a broken reporting
  relationship, but not in Closed periods. Reasons and previous state remain audited; numbers are not reused.
- Notifications are dispatched through the existing asynchronous email service after commit at case
  creation and HR/MD submission. Rolled-back transactions send no notifications.
- Existing started-assessment protection applies identically to Individual versions. There is no
  recalculation, reopening, employee acknowledgement, extra approver or new assistance approval.

Individual queue/detail responses additionally include `ownerParticipantId`, `employeeId`,
`employeeName`, `preparationMode` and `revisionSuperiorId`.
The API's `CONFIRM` action is labelled **Approve and Apply Revision**; `APPROVE` is **Approve Revision**.

### Individual Frontend

- HR / MD: **KPI Management > KPI Review > KPI Revisions**. Find a confirmed Individual plan
  by employee or KPI level, select **Request Individual Revision**, enter the required reason and
  select Superior preparation (default) or personal HR / MD preparation. The server resolves the
  actual path, including when the initiator is also the employee's Superior.
- Superior: **Team Performance > Team Reviews > Individual KPI Revisions**. Uses the shared
  Annual Review Period selector. Shows only Individual cases routed to the logged-in Superior,
  including for users who also have broader HR permissions. The backend rechecks the live relationship.
- Use **Prepare Revision**, the existing editor and **Apply to Plan**. Superior-prepared plans
  finish with **Approve and Apply Revision**. HR / MD-prepared proposals use **Send for Superior
  Approval**, then the Superior uses **Approve Revision** or **Return for Revision** with a reason.
- Whole-plan readiness, immutable pending proposals, saved meaningful changes after returns,
  Target reminders, cancellation, stale-write handling and unsaved-change protection are reused.
  Approval identity, date, effective version and activity appear in the shared history/details.
- Employee: **My Performance > My KPI Plan** shows current Individual version and read-only
  **Version History**, filtered to Effective/Superseded versions. It exposes no revision workspace.
- Access Control retains the original concise permission labels/descriptions. No Role permissions
  are granted or inferred from Role names.

Frontend checkpoint verification: 163 focused Angular tests passed, including Company/Department
regressions, Individual revision paths, employee history and My Assessments; 9 AuthorityService tests
passed, including permission-description checks. `ng build --configuration=production` passed with
existing Sass/CommonJS/bundle warnings. The optional UAT environment file is not present locally, so
the production configuration was used. No manual UAT cases or Role grants were created.

### Individual Backend Verification

- 471 relevant Phase 1-3 backend tests passed with all three isolated PostgreSQL test flags enabled;
  backend JAR packaging passed (`target/individual-revision-verified-build.log`).
- JPA round trips cover both preparation paths, Return/resubmission, approval metadata, owner-only
  assignments, stale finalisation and rollback after a failed cascade. Existing assessment state is
  compared before/after. Version protection tests cover all three KPI levels.
- V45 was applied on the local development database with the isolated runner, backup and row-digest
  preservation checks. No live employee fixtures, Role grants or KPI revision cases were created.
- Older migration-test fixtures exclude the V45 authority when constructing pre-V45 schemas; applied
  migrations remain unchanged. Individual frontend manual UAT awaits review.

## Authorities and Scope

| Authority | Capability |
|---|---|
| `CAN_MANAGE_KPI_AMENDMENTS` | Enable/disable period access; cancel unfinished Company/Department revisions, even when access is disabled/expired |
| `CAN_REVISE_COMPANY_KPI` | Create, edit and confirm own Company revision Draft; cancel own Draft while access is available |
| `CAN_PREPARE_DEPARTMENT_KPI_REVISION` | Explicitly prepare a requested Department revision; edit and submit as its recorded preparer |
| `CAN_APPROVE_DEPARTMENT_KPI` | MD decision on Department revisions; cannot act as requester or preparer |
| `CAN_MANAGE_DEPARTMENT_KPI` | Request in the existing assigned department; requesting HOD confirms/returns; withdraw own Requested case |

All actions also require active staff. HOD department scope uses the existing organisation resolver,
not a Manager label. Requester, preparer and MD approver are different staff identities. No actor
replacement or reassignment is implemented. Company management/setup authority alone is not revision authority.
HR amendment-management authority can inspect/cancel Company proposals, but cannot edit or confirm them.

## API

All routes below have `/api` prefix and use the authenticated UUID; no client actor overrides.

| Method / Route | Purpose |
|---|---|
| `GET /kpi-amendment-access/{periodId}` | Amendment-management-only access settings |
| `PUT /kpi-amendment-access/{periodId}` | `{ "enabled": true, "closeDate": "2026-12-31" }`; closing date may be null |
| `GET /kpi-plan-revisions/periods` | Scoped published periods for revision operators |
| `GET /kpi-plan-revisions/plans?reviewPeriodId=...` | Scoped confirmed Company/Department plans |
| `GET /kpi-plan-revisions?reviewPeriodId=...&level=...&status=...` | Scoped working/historical revision queue; filters optional |
| `GET /kpi-plan-revisions/{id}` | Version details, items, comparison, actors, audit, blockers and allowed actions |
| `GET /kpi-plan-revisions/plans/{planId}/versions` | Authorised version history |
| `POST /company-kpi-plans/{planId}/revisions` | Create next Company Draft, copying effective definitions |
| `POST /department-kpi-plans/{planId}/revision-requests` | Request next Department revision, copying effective definitions |
| `PUT /kpi-plan-revisions/{id}` | Save whole working item collection; items may be incomplete |
| `POST /kpi-plan-revisions/{id}/prepare` | HR accepts preparation responsibility |
| `POST /kpi-plan-revisions/{id}/submit` | HR submits complete Department proposal |
| `POST /kpi-plan-revisions/{id}/approve` | MD approval, awaiting requesting HOD |
| `POST /kpi-plan-revisions/{id}/return` | MD/HOD returns to HR with a reason |
| `POST /kpi-plan-revisions/{id}/confirm` | Company initiator or requesting HOD finalises immediately |
| `POST /kpi-plan-revisions/{id}/cancel` | Audited cancellation/withdrawal, retaining contents and decisions |

Creation requires `{ "reason": "..." }`. Existing-version writes require
`{ "expectedLockVersion": 3 }`, using the most recent detail/save response. Return and cancellation
also require `reason`. Update additionally supplies the complete `items` collection using existing
KPI DTOs. New items omit their IDs; existing IDs must belong to that working version, not its base.
Each successful write returns refreshed details and lock token. Stale tokens return HTTP 409.

Reasons are trimmed, nonblank and at most 10,000 characters. Empty item collections and incomplete
fields are allowed in Draft/Returned saves. Existing final-plan validation applies: at least one
complete item, five definitions per item and exactly 100% internal weightage. Final transitions
also require meaningful differences from the effective baseline and, after a return, a saved
meaningful change since the latest return. Ordering, whitespace-only changes and decimal formatting
are ignored by comparisons. Existing Perspective/KRA rules are not made stricter by UC-05.

## State and Amendment Controls

- Company: `DRAFT -> EFFECTIVE` without approval.
- Department: `REQUESTED -> DRAFT -> PENDING_MD_APPROVAL -> PENDING_HOD_CONFIRMATION -> EFFECTIVE`.
- MD/HOD return: `RETURNED -> HR edits -> PENDING_MD_APPROVAL`; prior MD approval is invalidated.
  Decision history retains prior approvals/returns. Finalisation always repeats MD and HOD stages.
- Replaced effective versions become `SUPERSEDED`. Unfinished cases may become `CANCELLED`.
  Cancellation records actor, time, reason and previous state; proposed definitions remain stored.
- Only one unfinished revision exists per plan. Cancelled numbers are never reused. Current plan
  scope, Employee-Level allocations and original publication/approval metadata remain unchanged.
- Amendment access starts disabled. The optional closing date is inclusive in the injected business clock.
  Disabled/expired access freezes all actions except amendment-management cancellation.
- Upcoming and Open periods, including ended Open periods, permit revisions. Closed periods are fully
  read-only, including access changes and cancellation. Normal setup/assessment actions are unaffected.
- Every write uses the existing transaction-level configuration lock, row lock and an optimistic token;
  forced token increments protect item-only saves even with a fixed UAT clock. Finalisation and cascading
  occur in one transaction. Cascade creates assignments for existing participants only.

## Assessment Version Contract

`KpiPlanDto` now includes `currentVersionId`, `currentVersionNumber`, `currentVersionEffectiveAt`.
`KpiItemDto.predecessorId` is read-only lineage for the revision editor's live Target reminder.
Incoming JSON cannot change that link; it does not add any new revision or assessment rule.
`KpiAssessmentDto.planVersionIds` maps each available/bound level to its immutable version ID.
`KpiAssessmentRequest.planVersionIds` should echo this map when saving, especially on first save.
Legacy requests without it are accepted for unrevised V1 first saves and existing saved assessments.
A first save against revised definitions requires the map. A stale map returns HTTP 409 without
silently carrying answers to new definitions. The frontend must integrate the map at its authorised checkpoint.

- Viewing a virtual Draft does not bind versions or create records.
- Persisting a Draft, even with an empty answer collection, binds available confirmed definitions.
- Cascading a newly confirmed level binds that version to started Drafts missing that level;
  `ON CONFLICT` never replaces a binding. Submitted and Reviewed sets remain frozen.
- Saved Drafts use their bound versions, including superseded ones. Later item reconciliation preserves
  answers and evidence; new assessments use current effective versions, regardless of checkpoint age.
- Checkpoint scoring uses immutable assigned item weights and recorded participant allocations, with
  Superior points only. No completed score is recalculated and no assessment is reopened.
- Employee history exposes only Effective/Superseded versions available to that employee. Pending,
  cancelled proposals and internal return/decision comments are restricted to workflow operators.

## Backend Verification

Focused tests include `KpiRevisionServiceTest`, `KpiRevisionControllerTest`,
`KpiVersionMigrationPostgresTest`, `KpiPlanPostgresTest` and `KpiAssessmentServiceTest`.
PostgreSQL tests run in rollback-only isolated schemas with `APEX_PHASE2_POSTGRES_TEST=true`.
Phase 1 and attitude regressions also use `APEX_PHASE1_POSTGRES_TEST=true` and
`APEX_PHASE3_POSTGRES_TEST=true`. No live test actors or permission grants are inserted.

Frontend/UAT integration testing requires the next explicit approval. Actor replacement,
retrospective recalculation and Phase 4 remain deferred.
