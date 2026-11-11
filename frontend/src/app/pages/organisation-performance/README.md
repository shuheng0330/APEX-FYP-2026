# Organisation Performance: UI Refinement

## Score-Band Update: 30 October 2026

This subsequent, explicitly approved backend/frontend change replaces the original five equal-width bands. The remaining sections document the earlier frontend-only delivery.

| Histogram Label | Actual Score Range |
| --- | --- |
| Below 60% | 0 <= score < 60 |
| 60%-69% | 60 <= score < 70 |
| 70%-79% | 70 <= score < 80 |
| 80%-89% | 80 <= score < 90 |
| 90%-100% | 90 <= score <= 100 |

- `backend/.../service/PerformanceSummaryCalculator.java` uses one boundary list for decimal comparisons and returned bin metadata. No integer conversion or display rounding is used before grouping. Both Team and Organisation dashboards use this calculator for KPI, Attitude and Final metrics. Employee scores, averages, inclusion rules and missing-result handling are unchanged.
- Flow: existing repositories/results -> shared distribution calculator -> existing Team/Organisation services/controllers -> unchanged Angular response types -> histogram. The histogram reads returned boundaries/counts, uses compact labels, and exposes exact exclusive upper bounds in tooltips and keyboard announcements. It does not regroup or invent employee counts in the browser.
- `OrganisationPerformanceExportService` already exports the same authorised distribution. Its existing XLSX Score Distribution sheet now receives the new numeric lower/upper bounds, inclusivity flags and counts automatically. An export regression test reads the workbook and checks all five bins. No CSV endpoint exists; this change does not introduce a new export format or alter privacy suppression.
- Verification: **72 focused backend tests passed**, including per-score decimal boundary checks across all three metrics, 19.5, zero, 100, missing/empty results, independent metric availability, Team/Organisation services/controllers and XLSX consistency. **110 focused frontend tests passed**, including exact labels, tooltips/count denominators, metric selection, older-response metadata, keyboard access, responsive containment and navigation regressions.
- Backend review package and frontend production build passed. Existing frontend bundle/Sass/CommonJS/baseline warnings and backend coverage-data warnings remain. Logs: `backend/target/score-band-tests.log`, `backend/target/score-band-build.log`, `frontend/dist/score-band-tests.log`, `frontend/dist/score-band-build.log`.
- Review outputs: `backend/target/career-path-learning-backend-0.0.1-SNAPSHOT-score-band-review.jar` and `frontend/dist/score-band-review`. The running isolated-UAT backend was not replaced or restarted. No live data, permissions, schema, underlying scores or historical appraisal records were modified. No commit or push.

Manual review after the updated backend is deployed: switch among all three metrics; confirm 59.9999 stays below 60, 60 starts the next band, 69.9999 stays below 70, and 90/100 are in the final band. Check counts against Team and the XLSX numeric boundaries. Null results stay outside every band and its percentage denominator. Check narrow-screen labels and keyboard/tap tooltips.

## Score-Band UAT Rollout: 30 October 2026

The subsequent screenshot showed the new chart reading old equal-width bins. A GET-only probe reproduced the outdated metadata, and process inspection confirmed that UAT still ran the preceding Phase 5C JAR. The reviewed `score-band-review.jar` has now replaced that process on port 8081, preserving every original runtime argument, the marked `apex_central_results_uat` database, Hibernate validation, disabled automatic Flyway and local SMTP capture. The frontend remains on port 4200; its dashboard shell returns HTTP 200.

The same probe now passes: period 12 Final Score bins are `[0,60)`, `[60,70)`, `[70,80)`, `[80,90)`, `[90,100]`, with counts `[0,0,0,1,0]`. GET-only verification passed for all three Organisation metrics, six Department reports and three XLSX exports, comparing counts with unrounded employee scores. All non-distribution Organisation values, all 92 UAT table fingerprints and all development table fingerprints match the fresh pre-restart baseline. No login, permission grant, fixture/workflow action, database mutation, commit or push was performed. Local ignored verification source/evidence and private runtime logs are under `frontend/dist/score-band-uat`.

Refresh the existing dashboard to load the new API response. Manual visual acceptance remains with the user.

## Investigation

- The Organisation dashboard and export require `CAN_VIEW_ORGANISATION_PERFORMANCE`. HR appraisal review permission is not required to view aggregate distributions and does not bypass suppression.
- `OrganisationPerformanceService.disclose` protects each selected period's Superior and HR series independently. A zero-record series is `NO_DATA`. A nonzero series is `SUPPRESSED` when its total is below **5**, only one outcome is populated, or any populated outcome has fewer than **5** records. The entire series, including its denominator, is hidden. Zero-count categories do not themselves trigger suppression.
- Only submitted/resubmitted Superior values and recorded approved HR decisions qualify. Drafts are excluded. Department search and comparison-chart selections do not narrow these Organisation-only statistics.
- David's previous isolated-UAT verification returned `SUPPRESSED` for both period-12 series. That is a data privacy decision, not a missing grant. The response deliberately does not reveal which individual condition triggered suppression, so the UI must not infer a hidden total or claim every suppression means fewer than five records overall.
- The Excel writer consumes the same authorised/suppressed report. It does not expose suppressed counts or denominators. The clearer on-screen privacy wording does not change the workbook's disclosure rules.
- Department sample counts come from each independent `summary.kpi`, `summary.attitude` and `summary.finalScore` contributor count. `employeesWithResults` is the Final Score count only.
- `2025 (1)`, `(2)`, etc. were generated by the shared Team trend component to distinguish separate review-period IDs ending in the same year. These were not duplicate records or annual-score calculations. Organisation now opts into actual period-end-date labels; full period names and start/end dates remain in tooltips and keyboard announcements.
- Organisation export is currently **Excel .xlsx only**, with seven aggregate sheets: Summary, Department Performance, Assessment Progress, Organisation Trend, Department Trends, Score Distribution and Appraisal Decisions. Parameters are selected period, 3/5-year range and metric. Existing SOP CSV code is local to that page, not an Organisation export contract or shared converter.

## Frontend Changes

- `organisation-score-distribution.component.ts/.scss`: existing Chart.js/ng2-charts vertical bars, using the returned bins/counts unchanged. Integer employee axis, full-range/count/percentage tooltips, keyboard/tap exploration, screen-reader data and a short included/pending caption. Missing results are excluded from the percentage denominator; zero scores remain in the first band. Empty distributions do not fabricate counts.
- `organisation-performance.component.html/.scss`: compact Department score cells with metric-specific count tooltips available on hover, keyboard focus and tap; aligned summary cards; numeric progress alignment and shorter grouped headers. Search, sorting, Department chart selection, View Team and return navigation remain intact.
- `team-performance-trend.component.ts`: an opt-in Organisation axis mode and metric-labelled Department tooltips. Team's default labels, calculations, ordering, same-year period identities, gaps and chart interactions remain unchanged.
- `en.json`: simpler privacy/no-data messages, Distribution Type label, metric-specific samples, compact summary counts and histogram guidance.

Missing score values remain **Pending**, as the contract provides no separate reason for distinguishing incomplete from permanently unavailable scores. Existing request/unavailable states and **Not Comparable** YoY metadata remain separate. No new score-status rule was invented.

## Export Decision

**CSV is deferred pending backend approval.** The button remains **Export Excel**, matching the actual response, MIME type and filename. No attempt is made to rename XLSX bytes to CSV or silently export a different subset in the browser.

A later approved CSV change should keep existing Excel clients compatible, serialise the same authorised aggregate report, retain privacy suppression and define how the seven report sections map to CSV. Department search/chart selection are visual controls, not backend report filters; the UI explicitly states that they do not filter the current export. No employee-level decisions, justifications or override reasons are added.

## Request Flow

Existing database/repositories -> existing Organisation service/controller -> unchanged Angular API service -> dashboard -> histogram and score tooltips. This slice has no backend, DTO, database, permission, scoring or historical-data changes. No UAT process restart or fixture modification is required for the frontend refinements.

## Verification

- **109 focused frontend tests passed**, covering Organisation component/service/navigation/histogram, shared Team chart/component/service, Staff component/service/navigation, annual appraisal trend/navigation and sidebar regressions. Tests cover bins/denominators, empty and zero results, keyboard/tap/count tooltips, same-year periods, privacy/no-data states, sample counts, selection/export consistency and existing permissions/navigation.
- Responsive containment passed at desktop/tablet/narrow-mobile widths; the histogram additionally covers 320px. A retained desktop canvas width found by the test was corrected with a component-local width constraint.
- **Production build passed** using `node node_modules/@angular/cli/bin/ng.js build --configuration production --output-path dist/organisation-refinement-review`. Existing warnings remain for the 5.24 MB initial bundle warning budget, global Sass deprecations, baseline browser mapping and `panzoom` CommonJS.
- Sandbox restrictions initially blocked the Chrome test browser and the existing public Google Fonts build download. The same tests/build passed with authorised execution outside the sandbox; no browser/build settings or dependencies were weakened or changed.
- Logs: `frontend/dist/organisation-refinement-tests.log` and `frontend/dist/organisation-refinement-build.log`. Review output: `frontend/dist/organisation-refinement-review`.

No live database checks or new workbook downloads were performed in this frontend-only slice. Existing API/export unit checks confirm the period/range/metric request and XLSX response handling remain unchanged; previous UAT workbook checks are documented separately. The null-score contract cannot identify a permanently unavailable score, so no new Pending/Not Available business distinction is inferred. Visual manual acceptance remains with the user.

Manual review: select KPI/Attitude/Final, inspect all five bands and their counts, focus/tap Department scores for contributor counts, compare period/date tooltips, switch Distribution Type, search/sort/open/return from Team, and export Excel. Check 1440px, 768px and narrow-mobile layouts. CSV, changed thresholds and permission overrides are not included.

Stop for manual review. No commit or push.

## Excel Analysis Report Refinement (30 October 2026)

This supersedes the plain seven-table export described above. The format remains XLSX and the existing controller, permissions, period/history/metric parameters and dashboard calculations are unchanged.

- `backend/src/main/java/com/tbm/careerpathlearning/service/OrganisationPerformanceExportService.java` still accepts only the authorised aggregate DTO and returns workbook bytes.
- `backend/src/main/java/com/tbm/careerpathlearning/service/OrganisationPerformanceReport.java` supplies the presentation using the existing Apache POI dependency. Executive Summary shows coverage and separate metric contributors; Department Performance and Assessment Progress add comparison/task charts; Performance Trends adds a three-metric line chart; Score Distribution adds the approved histogram; Appraisal Decisions charts only disclosed series; Department Trends retains detailed history and a compact period comparison matrix.
- Supporting tables retain IDs, comparison codes, bounds, dates and contributor counts. Technical metadata is grouped where appropriate. Filters, frozen headings, internal sheet links and landscape print areas aid analysis.
- Scores remain numeric API values, using the literal-percent format `0.00"%"`. Missing values stay Pending, valid zeroes remain numeric, and missing historical chart points remain gaps. Coverage and distribution shares are presentation ratios only; performance formulas and grouping are not recalculated.
- Small-group/homogeneous-outcome suppression remains authoritative. Neither totals nor counts from a suppressed series are exported, even if accidentally supplied in its DTO. No employee names, individual decisions, justifications or override reasons are added.

Flow: existing database/repositories -> authorised Organisation service -> existing controller -> export service -> POI report builder -> unchanged Angular download. There are no database, DTO, permission or frontend workflow changes.

Verification: **29 focused backend tests passed** across export, controller and Organisation service; the backend review build passed. Tests cover privacy including hidden cells/chart caches, zero/missing values, empty populations, independent contributors, decimal boundaries, literal text names, missing middle history points, chart references and access checks. Existing Maven duplicate-dependency, mapper and stale JaCoCo coverage warnings remain unrelated.

The sample `frontend/outputs/organisation-performance-report/Organisation Performance Analysis Report.xlsx` was generated using read-only authorised period-12 UAT aggregate data. All seven sheets were rendered and inspected. Four sample charts and a separate synthetic five-chart workbook were checked against their source cells; summary, contributors, tasks, history and bins match the API. POI/openpyxl reopen and XML parsing passed. Native Microsoft Excel opening and print preview remain manual checks; the preview renderer is not Excel.

The report was initially delivered for review without a UAT restart. Following explicit deployment approval, the reviewed `organisation-analysis-report` artifact was deployed to the existing isolated UAT service on port 8081, preserving the original runtime arguments, `apex_central_results_uat` database, Hibernate validation, disabled automatic Flyway and local SMTP capture.

Live KPI, Attitude and Final Score XLSX exports now return the seven-sheet analysis layout. Checks confirmed exact dashboard populations and histogram counts, historical charts, suppressed appraisal-series protection and denial for an unauthorised employee. All UAT/development table fingerprints and the three dashboard responses were unchanged before and after deployment. The actual Final Score response is available at `frontend/outputs/organisation-performance-report/Organisation Performance Analysis Report - UAT.xlsx`. No records, permissions, commits or pushes were changed.

Manual review: use Export Excel in UAT or open the downloaded report in Excel; check for repair warnings; inspect summary coverage, expand metadata, filter/sort tables, follow sheet links, review chart legends and Pending gaps, inspect the privacy notices and check print preview.

## Management Report Readability Refinement

This refinement follows the latest exported workbook and supersedes its presentation above. The existing authorised DTO still flows from Organisation service/controller -> export service -> `OrganisationPerformanceReport` -> XLSX download. No APIs, permissions, score formulas, historical comparisons or privacy thresholds change.

- Executive Summary places Average Final Appraisal Score beside Final Score Coverage, states the exact number of employees behind each average and uses short factual observations.
- Department Performance has six columns. One compact line below each Department name lists KPI/Attitude/Final employee result counts, while scores remain numeric and sortable. The Final Score chart uses a filtered source containing only available values, including valid zeroes.
- Assessment Progress leads with total outstanding tasks by Department and retains all six task categories plus the total in its detailed table. Counts remain tasks rather than unique employees.
- Performance Trends and Department Trends use date-derived labels such as `2025 Q1` and `2025 Jul-Aug`. Full names, dates, independent employee result counts and historical order are retained. Pending means an existing record has no completed score; Not Available means no Department record is present for that period.
- Score Distribution retains the exact decimal bands, adds employee-count labels above bars and highlights available/awaiting counts.
- Appraisal Decisions uses one privacy explanation when both series are suppressed. Mixed disclosure and available aggregate charts retain their existing protections.
- Report Data is the eighth, supporting worksheet. It retains technical reference fields, original comparison codes, IDs, score-band definitions and chart source ranges outside the management-facing sheets. Suppressed appraisal counts and totals are never included there.

Verification: **30 focused backend tests passed** (9 export, 5 controller, 16 Organisation service); the backend review build passed. Source-cell/chart-cache checks match the authorised period-12 report, including every task and metric-specific employee count. No raw enum codes appear in the seven presentation sheets.

Microsoft Excel 16.0 opened the generated XLSX read-only with normal loading. Eight worksheets and four charts were verified, with chart images exported and inspected. Native rendering exposed horizontal axis crossings that differed from the preview renderer; the crossings were corrected so positive bars extend from zero, Department names appear on the left and the value axes appear below. A regression protects these settings. Histogram count labels and literal percentage formatting were also verified in Excel.

Review sample: `frontend/outputs/organisation-performance-report/Organisation Performance Analysis Report - Refined.xlsx`.

After explicit deployment approval, the `organisation-report-refined` build was deployed to the same isolated UAT service on port 8081. Original runtime arguments and database settings were preserved. Live KPI, Attitude and Final Score downloads confirmed the eight-sheet layout, adjacent Final Score/coverage, six-column Department table, histogram count labels, single-series task chart and suppressed appraisal chart protection. UAT/development table fingerprints and all three dashboard responses matched the fresh pre-deployment baseline; unauthorised export remained denied.

The live Final Score download is `frontend/outputs/organisation-performance-report/Organisation Performance Analysis Report - Refined UAT.xlsx`. The Export Excel button now serves this refinement. No database records, permissions, commits or pushes changed. Stop for manual review.
