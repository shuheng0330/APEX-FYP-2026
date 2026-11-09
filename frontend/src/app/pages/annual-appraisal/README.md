# Full-Page Annual Appraisals

## What Changed

Superior preparation and HR review now open as normal full-width APEX pages, not
drawers. Queue URLs and business rules are unchanged. Both detail pages share one
component; their permission and action differences remain explicit.

The page order now follows the viewer and appraisal status. Superior Draft,
Pending and Returned pages keep results and supporting history before the
recommendation. HR pages put the recorded recommendation immediately after the
scores, before supporting history. Approved pages put one read-only Appraisal
Decision Summary above the results, comparing the original recommendation with
the recorded HR decision, justification, override reason and relevant dates.
Superior primary actions remain in the header. HR decision actions appear once
in a normal-flow section after activity, not in a fixed footer.
Recommendation choices use four native radio options; history uses a compact
table opening a read-only historical drawer. Existing missing-score, snapshot, overdue,
return/override and concurrency behaviour is retained.

The Superior queue starts with **Performance Results Pending**, followed by
**Ready for Appraisal**. The default remains Ready for Appraisal (or the user's
remembered filter); only the display wording and tab order changed.

### Status-Based Sections (Plan 42)

`annual-appraisal-detail.component.html` uses Angular status checks and one shared
`recommendationSection` template. `ngTemplateOutlet` places that section once in
the correct DOM order for the viewer; there are no hidden duplicate forms or
CSS-only reordering. The existing component state and API service still own all
loading, validation, unsaved-input protection and workflow actions.

For Returned appraisals, the current HR Return Reason appears prominently below
the header and system notices, before performance results, for both viewers.
The backend intentionally retains `latestReturnReason` after resubmission and
approval. The template therefore checks `status === 'RETURNED'` before showing
the active notice. It never clears that stored value or changes Activity events;
previous reasons remain in the collapsed timeline.

For Approved appraisals, the shared summary replaces the separate recommendation
and final-decision cards. It displays stored values only; missing information
stays `-`, and performance scores remain in their existing score cards. The
original justification is retained, and HR cannot edit scores. The SCSS reuses
APEX cards/badges and stacks the comparison on narrow screens. Two translation
keys provide the summary title and appropriate read-only recommendation help.

No Controller, backend Service, Repository, database, model or API changes are
needed: existing response -> Angular component state -> status-based template
is the complete data flow for this presentation refinement.

## Files to Learn First

| File under `frontend/src/app/` | Responsibility |
| --- | --- |
| `pages/annual-appraisal/annual-appraisals.component.ts` | Loads the authorised employee/submission queue and navigates to a detail URL. |
| `pages/annual-appraisal/annual-appraisal-detail.component.ts` | Resolves the selected record, controls the form, and calls existing Save/Submit/HR actions. Its HTML and SCSS provide the full-page layout. |
| `pages/annual-appraisal/annual-appraisal-navigation-state.service.ts` | Remembers list period, filters, pagination and scroll position, scoped to user and workspace. Never stores recommendation or HR input. |
| `services/annual-appraisal.service.ts` | Existing action APIs plus the approved read-only `performance-history` GET. |
| `pages/annual-appraisal/annual-appraisal-performance.component.*` | Existing score cards and performance-detail expansion, including missing values and submitted snapshots. |
| `pages/annual-appraisal/annual-performance-trend.component.*` | Completed annual performance chart, shared year-column tooltips, 3/5-year selection and keyboard exploration. |
| `app.routes.ts` and `components/app-top-header/app-top-header.component.ts` | Independently guarded detail routes and their page titles. |

## Example Request Flow

```text
Alice clicks Prepare appraisal in the queue
  -> Router opens /team-performance/team-appraisals/7/24
  -> shared detail component checks the available periods and scoped team queue
  -> Angular service loads the existing Draft, or readiness if none exists
  -> existing Controller -> Service -> Repository -> Database returns permitted data
  -> component displays the employee's actual results and recommendation form

Alice selects a recommendation and clicks Submit to HR
  -> existing create/update call saves the Draft if needed
  -> existing confirmation and submit call use the fresh server lock version
  -> backend validates and saves Pending Review
  -> David opens the same submission through /appraisals/review/{appraisalId}
```

Merely opening or refreshing an unstarted detail page does not create a Draft.
The backend still controls scope, readiness, permissions and permitted decisions.
An invalid or inaccessible URL shows an unavailable message, never another record.

## Scores and Decisions Are Separate

### HR Decision Controls

`annual-appraisal-detail.component.html` uses existing APEX buttons, click/tap
popovers and `nz-modal`. Only backend-permitted decisions for Pending HR Review
appear at the bottom. Approve, Override and Approve, and Return for Revision each
open one modal; there is no inline decision form or second confirmation popup.
The SCSS keeps coloured actions readable and stacks the tiles on smaller screens.

In `annual-appraisal-detail.component.ts`, `beginDecision()` opens the modal and
`confirmDecision()` checks the existing reason/decision rules, then calls
`AnnualAppraisalService.approve()`, `overrideApprove()` or `returnForRevision()`.
Those methods still send the same HTTP requests and lock version to the existing
backend. Scores come from the submitted snapshot and cannot be edited. HR is
approving the recommendation/final decision, not approving performance scores.

Cancel/X/Escape discard only modal input. During a request, closing and duplicate
confirmation are disabled. Failures keep input in the modal; access/concurrency
errors require closing and refreshing, never an automatic retry. Existing route
guards protect unsaved reasons and decision selections, including browser Back.
APEX's existing modal traps keyboard focus and restores it when the modal closes.
Initial-focus hints favour Return Reason or the safe Cancel action for approvals.

For HR only, `showRecordedSubmission()` filters duplicate snapshot expansions,
not events. Earlier genuine submissions stay accessible. The latest is hidden
only when its recommendation, justification and structurally equal snapshot are
already displayed in a Pending/Approved page. Returned or uncertain matches stay
available. Superior activity, recorded snapshots and historical drawers are
unchanged; no database writes occur while displaying activity.

The detail component loads two independent read-only lists for the selected
participant. `AnnualAppraisalService.performanceHistory()` calls
`/api/annual-appraisals/participants/{id}/performance-history`; the backend checks
scope, reads existing records through repositories, and returns completed annual
scores or valid recorded submission snapshots. A later correction is reported
as metadata, never used to overwrite historical recorded scores. This chart does
not require HR appraisal approval. The existing `history()` call still returns
actual Approved appraisal records and HR decisions for the separate history table.

The `AnnualPerformanceHistory` model in `models/annual-appraisal.model.ts` mirrors
the backend's lightweight DTO. Both requests have independent loading, retry and
stale-response protection. A history error does not discard form input or block
the existing submission workflow.

The chart uses actual period start/end dates and period IDs, not checkpoint dates
or approval timestamps. Same-year periods appear as distinct entries such as
`2025 (1)` and `2025 (2)`; tooltips give their full names and date ranges. Each
tooltip shows current/historical context, three percentages, configured weights,
and explicit `[UAT SIMULATED]` labelling where present. Saved snapshots with later
score differences show a short recorded-scores indicator. There is no duplicate
annual score table or suggestion that HR must approve performance scores.

Hover or tap near a year anywhere within the plot to see all three scores.
Keyboard users focus the canvas and use Left/Right or Home/End; a screen-reader
summary reports the selected period and its exact scores without a visible table.

Approved historical appraisals open through the existing APEX `nz-drawer`,
reusing the performance component for recorded scores and optional breakdown.
Only recorded recommendations, HR decisions, justification/reasons and relevant
dates appear. Closing by button, mask or Escape restores focus and does not
change the parent recommendation or unsaved HR input. No Phase 5 dashboard or
new appraisal write endpoint is introduced.

The results component reuses existing neutral `.status` pills for frequency
and annual consolidation labels; these are metadata, not approval statuses.

## Navigation and Unsaved Work

- Existing queues remain `/team-performance/team-appraisals` and
  `/appraisals/review`; the new routes add identifiers below those paths.
- Superior detail uses both Review Period and participant ID; HR detail uses the
  appraisal ID. Direct navigation and browser refresh reload authorised server
  data instead of trusting a record carried in navigation state.
- Queue context is restored from the current user's session storage and refreshed
  from the server. Pagination is clamped if the refreshed result set is smaller.
- Detail routes retain `canDeactivate` protection, including navigation to another
  record. Cancelling keeps the URL, employee and input. Busy requests prevent
  competing actions; browser reload/close retains the existing unsaved warning.
- Refresh Details asks before discarding unsaved work and discovers a competing
  first Draft through the scoped queue rather than retrying duplicate creation.
- The sticky toolbar measures the existing application header height so that it
  remains underneath it when the header wraps. Mobile controls and radio choices
  wrap; wide history/detail tables scroll within their own containers.

## Future Phase 5 Dashboard

**View performance details** still expands the working Phase 4 breakdown. There is
no placeholder/dead Full Performance Profile link and no redirect to the legacy
competency dashboard.

Once Phase 5 defines its authorised employee dashboard route, its entry action
should pass `employeeId`, `reviewPeriodId`, `participantId` and the current detail
URL as return context. Use Angular Router and the same unsaved-change guard; the
future dashboard backend must recheck scope. Do not substitute the Superior's ID
or discard the selected period. No Phase 5 route/API is introduced here.

## Manual UAT

1. As Alice, select **UAT Attitude Opening Ready** in Team Appraisals. Open Bob.
   Expect a full page, scores **85.60 / 86.67 / 86.13**, and the labelled simulated
   history. Opening alone must not create a Draft.
2. Hover anywhere in a year's plot column, not only its dots. Expect one tooltip
   with the full period name/dates, current/historical context, three percentages
   and actual composition weights. Simulated 2023/2024 history is explicitly
   labelled. Use 3/5 years and keyboard arrows/Home/End; test tap on a touch device.
3. Expand Appraisal History and open View appraisal details. Expect a separate
   read-only drawer with recorded scores, recommendation, HR decision, reasons
   and dates. Close with X, Escape or the mask; parent recommendation/HR input
   must remain. The chart has no detailed score table or HR approval disclaimer.
4. Check neutral frequency/calculation pills, four recommendation options and
   the justification field. Make an edit, use Back, then cancel discarding.
   Input and employee context must remain unchanged.
5. Save/submit through the existing flow. Back to the queue should preserve its
   filters and refresh statuses. Repeat with a direct detail URL and browser refresh.
6. As David, open a submitted appraisal from Appraisal Review. Check the full-page
   HR view, return/override reasons and read-only Approved state.
7. At desktop, tablet and narrow-mobile widths, check wrapping, sticky actions,
   keyboard focus and table scrolling. The main page must not scroll sideways.

No database, backend, permission, legacy-route or fixture changes are part of
this slice. Stop for manual UAT after affected frontend tests and the production
build; no commit or push.

## Full-Page Baseline Verification

- **55 affected frontend tests passed** in Chrome Headless: queues, shared detail
  workflows, direct routes, permissions, cancelled navigation, browser
  Back/Forward, history, radio choices/focus, and existing performance/trend/API
  service tests. Responsive DOM checks use 1440px, 768px and 360px widths.
- The final queue-state lifecycle change was followed by a separate successful
  run of its **11 directly affected queue tests**, including scroll restoration.
- `ng build --configuration=production` passed. Existing Sass deprecations,
  initial-bundle budget warning and the `panzoom` CommonJS warning remain.
- The named `uat` configuration could not build because its existing
  `src/environments/environment.uat.ts` replacement file is absent from this
  checkout. No deployment environment file was invented or changed; the existing
  production configuration was used for build verification instead.
- Tests use mocked APIs and do not write UAT data. No backend suites or database
  migrations were rerun. Live account-based visual acceptance remains manual UAT.

Commands, from `frontend/`:

```powershell
node node_modules/@angular/cli/bin/ng.js test --watch=false --browsers=ChromeHeadless --include="**/annual-appraisal/*.spec.ts" --include="**/annual-appraisal.service.spec.ts"
node node_modules/@angular/cli/bin/ng.js build --configuration=production
```

## History Refinement Verification

- **62 affected frontend tests passed** in Chrome Headless, including actual
  Chart.js x-column hit-testing away from points, calendar boundaries, same-year
  periods, zeroes, simulation/correction labels and keyboard summaries.
- The production build passed. Existing Sass/template warnings, the 5.31 MB
  initial-bundle warning and the `panzoom` CommonJS warning remain. No environment
  configuration or deployment files were changed; the named UAT environment
  replacement is still absent, as noted in the baseline verification above.
- Separate API loading, stale responses, error/retry, Approved-only appraisal
  history, drawer read-only content, Escape/close focus return and preservation
  of Superior/HR inputs were checked. Responsive DOM checks cover 1440px, 768px
  and 360px, including the historical drawer and score-card stacking.
- Test fixtures now clear all known Alice/David team/HR navigation-state keys
  before and after each test. This prevents random-order test leakage; no
  production storage rules were changed.
- Touch-device behaviour and live account acceptance remain manual UAT. No
  assessment submissions, fixture edits or permission changes were made by tests.
- Logs: `.angular/test-appraisal-history-refinement.log` and
  `.angular/build-appraisal-history-refinement.log` (generated/ignored).

### UAT Runtime Repair

The previously affected backend `-review.jar` was replaced with the verified
executable performance-history build and restarted using its exact existing
arguments and isolated UAT database connection. Flyway remains disabled and
schema validation remains enabled. The endpoint rejects anonymous access with
HTTP 401. No database switch, migration, fixture or permission change was made.
Future backend builds must not target a JAR currently used by a running process.

## HR Decision Refinement Verification

- **90 affected frontend tests passed** in Chrome Headless. They cover the three
  single-modal decisions, unchanged payloads/scores, required reasons, one write
  per confirmation, cancellations, access/concurrency failures, and retained input.
- Route tests cover cancelled sidebar/record navigation and browser Back with an
  open HR modal. Activity tests cover initial/repeated submissions, Returned and
  unmatched snapshots, and unchanged Superior expansion access.
- Responsive DOM checks cover 1440px, 768px and 360px widths. Keyboard focus,
  focus return, popovers, modal scrolling and action contrast are checked.
- The production build passed with existing Sass/template, initial-bundle budget
  and `panzoom` warnings. No environment configuration was changed.
- Logs: `.angular/test-hr-appraisal-refinement.log` and
  `.angular/build-hr-appraisal-refinement.log` (generated/ignored).

For manual UAT, open a Pending HR Review as David. Expect Refresh Details and the
deadline in the header, then the three permitted coloured actions after activity.
Open each action and cancel it first: input must not be submitted. On disposable
submissions, test Return/resubmit, Approve and a different-decision override.
Earlier submission details must remain available while duplicate latest details
are hidden. Check touch interactions and the final layout on a real device.

No backend or database changes, permission grants, fixture writes, commit or push
were performed for this refinement. Stop here for manual UAT.

## Plan 42 Verification and Manual UAT

- **111 affected frontend tests passed** in Chrome Headless, including the
  existing workflow/navigation/modal tests and the new status-specific DOM order,
  current-versus-historical return reasons, resubmission, normal/override decision
  summaries, missing values, zero scores and Closed-period checks.
- The stale-return regression was reproduced before the template fix: all four
  Superior/HR Pending/Approved cases failed, then passed without clearing data.
- Responsive checks cover 1440px, 768px and 360px, including long reasons and
  justifications, two-column/mobile summaries, wrapping and the prominent notice.
- The production build passed with existing Sass/template, initial-bundle budget
  and `panzoom` warnings. No environment or deployment changes were made.
- Logs: `.angular/test-appraisal-hierarchy-before.log`,
  `.angular/test-appraisal-hierarchy.log` and
  `.angular/build-appraisal-hierarchy.log` (generated/ignored).

Manual UAT: in both authorised views, open a Returned appraisal and check the
current reason immediately below the header/system notices. After a normal
resubmission, that notice should disappear while Activity retains its recorded
reason. HR should see the recommendation before supporting history, with actions
still at the bottom. On an Approved record, both viewers should see the same
read-only comparison above performance results, including any recorded override
reason, without duplicate recommendation/final-decision cards. Check mobile and
keyboard navigation. Use only disposable UAT records for workflow actions.

No backend/API/model changes, database operations, permission changes, commit or
push were performed for Plan 42. Stop for manual UAT.

## Stage 2 Employee AI Insight Frontend

The detail page now embeds `EmployeeAppraisalInsightComponent` immediately after
the recommendation/justification card. Approved views place it below the existing
recorded decision summary. The existing six-section `AppraisalInsightSummaryComponent`,
preview score styling, APEX card styles and ng-zorro buttons are reused. No new
dependency or route is introduced; the standalone scenario preview is unchanged
apart from shared-presenter compatibility (numeric emphasis defaults off there).

Data flow: authorised appraisal detail context -> typed `AppraisalInsightService`
employee/submitted endpoints -> inline state controller -> existing summary
presenter. Models match `EmployeeInsightDto`. GET state never generates or saves
a Draft. POST sends no evidence/prompt/recommendation body, just the existing
participant path and UUID idempotency header. Appraisal submission keeps its
existing payload; the backend alone attaches the eligible exact insight version.

Superior Draft/Returned views use backend eligibility, valid cached insights,
remaining allowance and server-relative cooldown/backoff times. Matching active
work is followed without a second POST. Explicit generation/regeneration/retry,
same-key uncertain-request recovery, 3-second polling with a 2-minute cutoff,
Check Status and identifier-only account/participant session storage are retained.
Leaving/changing records cancels local requests/timers, not backend jobs. AI work
does not alter form inputs, dirty checks, submission availability or HR actions.

HR and submitted/approved Superior views request only the selected submission's
immutable insight. The activity ID is the latest Submitted/Resubmitted event by
recorded event ID, not timestamp or later Draft/Return events. HR cannot generate.
A read-only Draft uses the state GET instead, so an existing unsubmitted insight
is not confused with a historical submission. Missing/unapproved scopes and
disabled environments do not show a fictional card; no absent HR link is replaced
with a newer unsubmitted insight. No historical insight is rewritten by the UI.

The inline card keeps employee/period context, fictional-UAT label, version,
timestamp, three recorded scores and the AI disclaimer. There is no Supporting
Evidence UI on detail pages; backend evidence, references and snapshots remain
intact. Percentages and points-out-of-five/100 can be bolded through controlled
text fragments, never `innerHTML` or AI markup. Line breaks and explicit evidence
gaps remain untouched; zero scores are distinct from missing results.

Changed files: `employee-appraisal-insight.component.{ts,html,scss,spec.ts}`,
`annual-appraisal-detail.component.{ts,html,spec.ts}`, its routing spec/test
fixtures, `appraisal-insight-summary.component.ts`, `appraisal-insight.model.ts`,
`appraisal-insight.service.{ts,spec.ts}` and `src/assets/i18n/en.json`.

Verification covers mocked employee and HR APIs, cached/active work, duplicate
clicks, uncertain recovery/page return, polling cutoff/cleanup, explicit retries,
cooldown/quota/provider limits, stale evidence, context/account isolation,
submission identity and read-only views. Existing appraisal forms, decisions,
navigation guards and standalone preview regressions are included. Responsive DOM
checks use the actual styles at 1440/768/360px, with native keyboard-operable
buttons, visible headings and polite status announcements. Final results are
recorded in `.angular/test-employee-ai-integration.log` and
`.angular/build-employee-ai-integration.log`.

Final verification: **207 focused tests passed** with no failed/skipped tests;
`tsc --project tsconfig.spec.json --noEmit` passed; the production build passed.
Existing Sass, unrelated template/import, initial-bundle budget (5.21 MB versus
5 MB warning threshold) and `panzoom` CommonJS warnings remain. An isolated
ignored Karma launcher uses the installed stable headless shell because the newer
Chromium build intermittently failed capture. Production output is isolated at
`dist/ai-appraisal-insight-review`; no artifact was deployed. The initial font
download sandbox failure was resolved by rerunning the unchanged production
configuration with network access. `git diff --check` passed.

No backend, Gemini prompt, schema, permission or environment changes; no live
provider calls, UAT activation, commit or push. Real employee data remains excluded
by the unchanged backend safeguards. Live UAT/provider acceptance needs separate
approval; stop for frontend review.

### Stage 2 Isolated UAT Integration

After separate approval, the reviewed backend and UAT frontend were deployed on
loopback ports 8082/4201. V53/V54 were applied to the backed-up, marker-verified
disposable UAT database only. A normal-workflow fictional Bob fixture was created
for 2022 with separate 2020/2021 history; exact participant/period/source roots
were registered server-side. Existing accounts and permissions were unchanged.

Data flow: trusted scope -> existing readiness/checkpoint/history services ->
identity-free evidence and fingerprint -> shared PostgreSQL Gemini admission ->
immutable generation -> normal submission event link -> submitted HR insight ->
existing inline frontend. Company sources use exact checkpoint-end matching.
One controlled live generation, idempotent replay/cache reuse and two immutable
submission links passed; recorded appraisal snapshots were unchanged. All 1,729
pre-existing rows across 101 tables remained identical. No live load testing.

Confirmed operator budgets are 5 RPM, 250,000 input TPM and 20 RPD for Gemini 2.5
Flash. Two shared appraisal workers/eight waiting jobs and per-context 60-second
cooldown/five fresh attempts remain technical UAT safeguards, not TBM policy.
The new appraisal is Returned for manual testing. Deployment details, backup,
fixture provenance and verification are kept uncommitted under
`frontend/docs/ai-appraisal-insight-uat/20261107-stage2/`. Development services were
left running unchanged. No commit or push; stop for stakeholder acceptance.

### Employee-Only AI Placement

The shared Team Appraisals/HR review list no longer imports or renders the
standalone fictional scenario preview. Lists load no scenario catalogue and
focus on employee selection and appraisal tasks. Employee-specific insights
remain in `AnnualAppraisalDetailComponent` below Recommendation Justification,
or in its recorded recommendation area for submitted/approved views. The
Superior's generation controls and HR's exact submitted read-only version are
unchanged. The six standalone scenarios, backend endpoints, stored evidence and
component regression tests are retained; only their list-page placement is removed.

The two queue regressions were observed failing against the old markup before
removing it. Detail, navigation, AI and closure regressions plus the production
build are rerun before deploying this frontend-only update to existing UAT.

Final verification: 307 focused tests, spec type-check, production UAT build and
diff whitespace check passed. Existing Sass/5.21 MB bundle/CommonJS warnings
remain. One initial suite failure exposed an in-flight AI ticket surviving from
a prior test; the shared test cleanup now clears only the known fictional test
account/participant storage keys. Application recovery/storage behaviour is
unchanged. Red, initial and passing logs are retained under `.angular/`.

On 8 November, UAT ports 4201/8082 initially had no listeners. The existing backend
was restarted unchanged and the updated frontend deployed separately. Login HTTP
200, proxy authentication HTTP 401 and exact served-build hashes passed. No
database migration, account/permission changes or live provider calls were made.
The manual guide and current read-only fixture state are under
`frontend/docs/employee-ai-list-refinement-uat/20261108/`. No commit or push.
