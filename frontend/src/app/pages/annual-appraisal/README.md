# Full-Page Annual Appraisals

## What Changed

Superior preparation and HR review now open as normal full-width APEX pages, not
drawers. Queue URLs and business rules are unchanged. Both detail pages share one
component; their permission and action differences remain explicit.

The page order is: employee/context header, Annual Performance Results, Historical
Performance Trend, collapsed Appraisal History, recommendation/HR decision, and
collapsed activity. The header contains the single set of primary actions.
Recommendation choices use four native radio options; history uses a compact
table opening a read-only historical drawer. Existing missing-score, snapshot, overdue,
return/override and concurrency behaviour is retained.

The Superior queue starts with **Performance Results Pending**, followed by
**Ready for Appraisal**. The default remains Ready for Appraisal (or the user's
remembered filter); only the display wording and tab order changed.

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
