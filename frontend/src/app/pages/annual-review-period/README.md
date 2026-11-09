# Review Period Closure - Frontend

## Flow

The existing Review Period detail form hosts `ReviewPeriodClosureComponent` above
the saved configuration summary. Draft creation, configuration previews and
Upcoming periods do not request closure readiness or offer closure actions.

`AnnualReviewPeriodService.closureReadiness` -> typed `ReviewPeriodClosure` ->
completion counts and optional View/Hide outstanding requirements -> ng-zorro
confirmation -> `close` -> update the parent's displayed status and closing
metadata. The API remains authoritative for dates, rosters, configurations and
completion; the browser does not recalculate closure eligibility or scores.

Employee counts and employee/checkpoint task counts are labelled separately.
Blocker messages and employee/period names use plain-text interpolation. Closed
records show only recorded actor/time when available, with no invented historical
metadata or reopening control. Times are displayed in the UTC+8 business zone.

HTTP 409 supplies fresh readiness directly. Other close failures clear stale
eligibility and require a new readiness check, not an automatic POST retry.
Permission failures hide actions. Context changes/navigation cancel subscriptions
and dismiss confirmation dialogs. Existing route permissions and form guards stay
in place; closing never invokes configuration update/publish APIs.

## Appraisal Compatibility

The existing appraisal list/detail components now recognise Open and Closed
periods for appraisal work. Saved records still require backend `allowedActions`;
HR decisions, submission validation, frozen snapshots and unsaved-input guards
are unchanged. A Closed-period notice distinguishes frozen performance results
from appraisal recommendations and HR reviews that can still be completed.

The existing employee AI card receives an editable unsaved context in Open/Closed
periods. Backend eligibility still controls Generate/Regenerate. AI requests do
not save a Draft or recommendation inputs and never block appraisal submission.
HR continues to retrieve only the exact submitted insight in read-only mode.

## Review Boundary

Focused tests cover the readiness/close HTTP contract, permissions, counts,
blocked and stale replies, confirmations, failures, navigation cleanup, plain-text
rendering, mobile wrapping and Closed-period appraisal/AI behaviour. Existing
review configuration, appraisal navigation and AI regressions are included.

Verified: 90 review-period/service tests and 216 appraisal/navigation/AI tests
passed in headless Chrome (306 total, zero failures).
`tsc -p tsconfig.spec.json --noEmit` and `git diff --check` passed.
The production-mode `uat` build passed to
`dist/review-period-closure-review`; existing Sass, 5.21 MB initial-bundle warning
and panzoom CommonJS warnings remain. Logs are
`.angular/test-period-closure-focused.log`, `.angular/test-period-closure-appraisal.log` and
`.angular/build-period-closure.log`. APIs/Gemini were mocked, not live-tested.

The first browser attempt was sandbox-blocked and the combined runner exited
before tests; the two bounded-worker batches above completed successfully. The
production build needed normal network access for the existing Google font
download. Neither workaround changed application configuration or dependencies.

No backend edits, database operations, deployment, permission grants, live Gemini
calls, commits or pushes belong to this frontend slice. UAT still needs separate
deployment approval and the prepared backend migration before manual integration.

## Closure Usability Refinement - 9 November 2026

Inspection confirmed readiness returns the full blocker list, not a backend page.
Check Readiness remains a read-only GET. Close remains an explicit POST with
server-side revalidation under the existing lock and a fresh readiness DTO on
HTTP 409. Permissions, roster/checkpoint denominators and completion rules are
unchanged; Close stays disabled until the backend reports Ready.

The existing inline section now separates Review Period Requirements from
Outstanding Employee Assessments. Participant-less blockers and configuration
issues appear first; employee-specific configuration retains its recorded name,
Department and checkpoint context. Employee-scoped result/reconciliation issues
remain in the employee table. No blocker is deduplicated or discarded, and each
category preserves its original recorded order.

Employee tasks use the shared APEX page-size selector and ng-zorro pagination,
defaulting to 10 rows with 10/20/50 choices and an unfiltered task total. Paging is
local display-only: no page HTTP requests, score changes or readiness calculations.
Fresh results reset the page; the View/Hide disclosure remains intact. The three
metrics keep exact API values with stronger numbers, quieter denominators and
responsive alignment. The helper sentence is the requested shorter wording.

Ongoing periods show a friendly heading and the API's formatted earliest date.
Configuration messages remain plain text. Generic, absent or null details receive
an explicit unavailable-details explanation, never an inferred cause. Nullable
client fields match the existing backend's possible missing date/message values;
the rejected-close DTO guard preserves generic blockers without message text.
Unknown codes use a generic human-readable heading, not an internal enum string.

View Saved Configuration reuses the form's existing scroll/focus pattern to the
saved settings below, including keyboard focus. It does not navigate to an edit
route or unlock Open-period configuration. The API supplies no targeted repair
destination, so no speculative repair link/workflow was introduced. A successful
Open-to-Closed update emits one existing APEX success toast; already-Closed reads
do not repeat it. Existing confirmation, duplicate prevention and error handling
remain in place.

Final verification: 323 focused closure/appraisal/navigation/AI tests passed with
zero failures, including 106 review-period/service tests. Spec type-check and diff
whitespace check passed. The final production-mode UAT build passed to
`dist/closure-usability-review`, with existing Sass, 5.21 MB bundle-warning and
panzoom CommonJS warnings. Tests cover category/context retention, 10/20/50 paging,
full task totals, null/generic details, keyboard focus, 1440/768/360px wrapping,
blocked/Ready/Closed/error states, fresh 409 blockers and unchanged appraisal
behaviour. APIs/providers were mocked; no live UAT/browser acceptance was run.

Evidence: `.angular/test-closure-usability-final.log` and
`.angular/build-closure-usability.log`. No backend edits, schema/data operations,
permission grants, UAT deployment, commits or pushes. Stop for manual review.

## Attitude Coverage Safeguard - 9 November 2026

New Review Period publication now validates an explicitly selected Published
Attitude configuration against all participating Job Roles and applicable active
criteria. Draft saving remains allowed when selection/coverage is incomplete.
The existing period FK stores the selection; no schema change is necessary.
The selected edition stays pinned through Upcoming/opening. Legacy already-
published periods keep their existing assignments and fallback behaviour.

Data flow: `AttitudeConfigurationBinding` supplies Published coverage options and
validates server-side publication through `AnnualKpiReviewPeriodServiceImpl`;
`AnnualReviewPeriodService` loads those choices for the existing form. Closure
presentation groups identical configuration causes but retains all blocker
members, readiness denominators and task pagination. Coverage labels use actual
saved Job Roles. Setup deep links load the assigned Published edition using the
authorised period response; global Draft editing remains separate.

Open-period reassignment is design-only. See
`attitude-configuration-correction-plan.md` for the required audit-table proposal,
the assessment FK/lock protections, started-record correction options and the
read-only UAT finding. No update-assignment code or migration belongs to this slice.

Verification logs/builds are isolated in `frontend/docs/attitude-coverage-review`.
Backend/provider calls in tests are mocked. Development/UAT assignments and
permissions are unchanged; no deployment, commit or push.

Final verification: 187 focused backend tests and 160 focused frontend tests
passed with zero failures. The backend package, production-mode UAT frontend
build, spec type-check and scoped whitespace check passed. Frontend checks cover
Draft/publication coverage, server contracts, assignment-vs-coverage states,
context navigation/permissions, retained input, slow lookups, grouped causes and
1440/768/360px wrapping, plus existing closure behaviour. Responsive fixtures use
readable translated labels/help rather than untranslated technical keys.

Logs: `backend-focused-final.log`, `backend-build.log`,
`frontend-final-verified.log` and `frontend-build-final.log` in that review folder.
Frontend warnings remain the existing Sass deprecations, 5.21 MB initial bundle
budget and panzoom CommonJS warning. The first backend test attempt stalled on
the sandboxed Mockito attachment; only its verified review JVMs were stopped and
the normal-permission isolated run passed. No running application was stopped.

No database mutation/migration integration tests or live browser acceptance were
performed. A separate marker-guarded, read-only UAT query confirmed period 5 has
zero persisted Attitude assessments, with schema still at V55. Existing migration
files match the initial isolated source copy; no V56 migration was created.

## Controlled Reassignment and UAT Deployment - 9 November 2026

The next slice was explicitly approved. `AttitudeAssignmentService` now composes
the existing Published-configuration binding, saved Role/roster repositories,
Attitude assessment records, appraisal references and protected AI fixture roots.
The existing configuration lock serialises updates with assessment creation and
closure. GET remains read-only; POST requires an expected assignment ID, target
Published ID, reason and request UUID. Any saved Draft/submitted/reviewed record,
Closed/non-Open period, stale decision, incomplete coverage or protected source
dependency blocks reassignment. No assessment is reset, moved or copied.

V56 introduces only `attitude_configuration_assignment_activity`, its indexes and
an immutable-audit trigger. Old/new configuration and actor references are kept;
the same successful request replays without another write. A failing audit insert
rolls back the assignment in the same transaction. No data/permission backfill or
period-scoped Role mapping supplement is included.

The existing Setup component and HTTP service host readiness, confirmation,
explicit retry, affected-record references and collapsed assignment history.
Uncertain retries reuse the request UUID; fresh server 409 state updates the
context without overwriting the global configuration editor. The six-scenario AI
preview, Gemini prompts and employee appraisal flows are unchanged.

Final verification: 272 backend tests, including eight real PostgreSQL migration/
transaction checks on a fresh loopback-only test cluster, and 165 focused frontend
tests passed. Spec type-check, backend package and production frontend build
passed. Twenty concurrent identical corrections produced one audit and 19
replays; first-assessment and closure races, audit rollback, immutable history and
direct/historical fixture guards passed. Existing frontend Sass/bundle/CommonJS
warnings remain. Logs are in `frontend/docs/attitude-assignment-review`.

Isolated UAT now serves the verified artifacts from
`frontend/docs/attitude-assignment-uat/20261109` on ports 4201/8082. A full verified
backup preceded applying exactly V56. All 107 prior UAT tables and development
table contents matched their baseline after startup/smoke checks. Hibernate
validation remains enabled. No assignment, assessment, closure, permission or AI
generation was submitted/seeded during deployment. The disposable test cluster
was stopped; development processes stayed unchanged. No commit or push.

The exact stakeholder steps and current fixture limitations are in
`attitude-configuration-manual-uat.md`. In particular, period 5 is correction-
eligible, but no existing Open period is fully closure-ready. Use a fresh short
disposable period for the positive Close test after completing its entire roster.

## Attitude Selection and Setup Navigation - 9 November 2026

The existing Published-options response feeds the creation form. For a new
period only, the first successful non-empty load selects the latest publication
timestamp (ID breaks ties). Manual changes, clearing, saved Draft selections and
published assignments are not replaced by refresh. The request still sends the
selected ID through the existing API. Draft/publication coverage rules are
unchanged. One coverage status, a compact Role/Department list and secondary
Refresh/Setup actions replace the overlapping warnings; lists over five Roles
use the existing View/Hide pattern.

Setup links retain the current origin and use only validated configuration and
period IDs. Saved Draft context is included; an unsaved new period has no ID and
does not invent a period context. The linked Published edition is displayed,
never an unrelated global Draft. Open/Closed saved assignments remain
authoritative. An unsaved Published selection for a saved Draft is explicitly
distinguished from that Draft's saved selection; opening Setup writes nothing.

The authentication reproduction found that `/auth/me` returns identity/roles,
not a token: the old frontend erased the token just restored by refresh. Parallel
401s also rotated the same HttpOnly refresh cookie concurrently. AuthService now
retains the in-memory token, joins refreshes within a tab and, where available,
uses native same-origin Web Locks to serialise cookie rotation between tabs.
No tokens are copied to browser storage, URLs or other tabs. Session revisions
prevent delayed refresh responses restoring logged-out/replaced sessions.
Failed auth endpoints never recursively refresh. APP_INITIALIZER and both guards
are unchanged. The guard's pre-existing `/access-denied` destination lacked a
route and fell through to Organisation Chart; a small translated ng-zorro Result
view now supplies that destination inside the authenticated route tree.

Verification: 223 focused component/service/auth/permission/navigation tests
passed, as did the spec type-check and scoped whitespace check. A separate broad
navigation run had 68 passes and one pre-existing failure: `kpi-navigation.spec`
still assumes My KPI Plan/Team Reviews are the first menu entries rather than the
existing dashboards. Both that test and the menu match HEAD and are untouched.
Reproduction, test/build logs and the mocked production-browser check are under
`frontend/docs/attitude-selection-review`. No backend, API, database, permission,
deployment, commit or push changes are included in this slice.

The final production-mode UAT build passed with existing Sass, 5.21 MB initial
bundle and panzoom CommonJS warnings. The isolated production-browser harness
used only fictional mocked HTTP responses: four sequential and two simultaneous
Setup tabs passed, with eight successful refreshes, zero rejected refreshes and
peak refresh concurrency of one. The unsaved parent input and selected Published
edition were retained, saved Draft context was correct, and unauthorised access
reached Access Denied without loading Setup. No mock writes or token storage
occurred. Wrapping passed at 1440/768/360px; preview images and
`browser-results.json` are in the same review folder. The temporary review server
and browser are closed; the deployed UAT artifacts were not replaced.

## Selection Refinement UAT Deployment - 9 November 2026

Separate deployment approval was received after the frontend review. The exact
reviewed build is now served on 127.0.0.1:4201 from
`frontend/docs/attitude-selection-uat/20261109` (frontend PID 42104). All 1,741
files matched their staged SHA-256 hashes; served routes, translations and
changed lazy bundles were verified. Backend 8082 and development 8081/4200
process identities stayed unchanged. The previous frontend artifact is retained.
No database, backend, permission or authenticated workflow changes were made.
Manual checks and deployment evidence are documented in that deployment folder.
Reload existing UAT tabs to use the new authentication code. No commit or push.

## PLAN (51): Deferred Correction and Draft Guidance - 10 November 2026

`AttitudeAssignmentService` now checks the server-only
`attitude.configuration.reassignment-enabled` property, default false when
absent. Existing authority and active-account checks remain. Both state and
update reject disabled use with the standard HTTP 400 message before period
reads/locks or audit lookup, including replay of an earlier successful request.
Enabled-mode code/tests remain for later reconsideration; no runtime profile or
UAT setting enables it. Existing legacy initial assignment is unaffected.

The Setup component now reads period context only, with no correction readiness
request, update handler, controls or audit-history disclosure. Typed HTTP
adapters/DTOs, backend endpoints/entities/repositories and translation keys are
retained, not exposed through the deferred UI. V56 and its append-only audit
table/trigger remain intact; all 56 migration files match their baseline hashes.
No migration, backfill, rollback or audit deletion was introduced.

Draft-linked Setup uses a compact period/Published-selection summary. Missing
Roles come only from the corresponding saved context. A different unsaved
Published choice is labelled, with verification deferred to the Review Period
form rather than borrowing another edition's coverage. The period selection
stays separate from the editor: Copy selects/focuses an editable Draft, and
successful publication shows the return/refresh/explicit-selection reminder.
Saving or failed publication does not show that reminder. The Review Period
form adds one conditional resolution instruction below its missing-Role list.
Existing default selection, version pinning, publication checks, permissions,
unsaved-input guards, cross-tab sessions and closure workflow are unchanged.

Verification: 322 focused backend unit/controller/security tests plus nine real
PostgreSQL transaction/audit tests passed (331 total); 220 focused frontend tests,
spec type-check, backend package and production-mode UAT frontend build passed.
PostgreSQL used only a fresh loopback test cluster at 55461 and new disposable
schemas; disabled calls, including replay, preserved existing fixture assignment,
assessment and audit rows exactly. The cluster is stopped, not connected to UAT.

The production-browser harness used mocked data only: four sequential/two
simultaneous tabs, correct saved/unsaved context, editable Draft Copy, publication,
retained parent inputs, explicit refresh/selection, denied access and
1440/768/360px layouts passed. It made zero correction requests; its four writes
were mocked configuration-copy/save/publication calls. All external requests were
blocked. Logs, screenshots and verification artifacts are in
`frontend/docs/attitude-workflow-review`. Existing mapper/Sass/bundle/CommonJS
and test-agent warnings remain. The first runs exposed only mock-message and
NgModel-settling test-fixture issues, both corrected before passing reruns.

The manual UAT guide now covers new-period coverage and closure only, and clearly
marks correction as deferred. Nothing was deployed: UAT keeps its previously
deployed frontend/backend, with existing records, credentials and permissions
untouched. No commits or pushes. Stop for manual review.

## PLAN (51) UAT Deployment - 10 November 2026

Separate deployment approval was received. The exact reviewed frontend/backend
are served from `frontend/docs/attitude-workflow-uat/20261110-verified` on
4201/8082 (PIDs 45384/57828). All 1,741 frontend files matched the build hashes.
A fresh verified backup preceded restart; all 109 UAT tables and all development
table contents matched before reopening the public frontend. Correction GET/POST
returned 400 under the missing-property default; access checks remained 401/403,
and closure readiness remained available. No migration, restore, permission,
correction, closure or provider operation was performed. Development processes
stayed unchanged. The initial attempt's refresh-token-only change and artifact
rollback are documented with the retained backups; no session data was restored.
The manual guide now identifies the deployed state. No commit or push.
