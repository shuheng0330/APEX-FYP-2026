# Attitude Evaluation Configuration (UC-12)

Phase 3 Slice 3 implements database/backend configuration only. Employee/Superior attitude assessments, scoring and frontend are deferred.

## Access and model

All endpoints require `CAN_MANAGE_ATTITUDE_CONFIGURATION` and an active, non-deleted account. V40 grants the authority to the explicitly verified existing Super Admin setup Role. Runtime access uses permissions, not Role names or Employee Levels.

- `attitude_configuration`: reusable Draft/Published edition, author/update/publication metadata.
- `attitude_criterion`: ordered Active/Inactive criteria. `SHARED_CORE_VALUE` has no format and applies across formats. `FORMAT_SPECIFIC` requires `MANAGER`, `SALES`, or `OTHERS` (non-sales).
- `attitude_rating_definition`: configurable label/description for points 1-5; composite key `(configuration_id, point)`.
- `attitude_role_format_mapping`: one format per configured Job Role/edition, independently of Employee Level and permissions.
- `annual_kpi_review_period.attitude_configuration_id`: optional FK to an immutable edition; no duplicate snapshot/binding table.

No stakeholder criteria, Role mappings or rating text are invented or seeded. Administrators enter configuration content.

## APIs

Base: `/api/attitude-configurations`.

| Method | Path | Behaviour |
| --- | --- | --- |
| GET | `/` | Saved editions, newest created first. |
| GET | `/current` | Latest published edition; publication date/ID tie-break. 204 when absent. |
| GET | `/options` | Fixed formats, eligible non-deleted Job Roles (with department names), and a scoped list of Open Review Periods for initial binding. Does not grant Review Period administration. |
| GET | `/{id}` | Complete edition, including inactive criteria and metadata. |
| POST | `/` | Save an incomplete Draft (201). |
| PUT | `/{id}` | Replace a Draft's complete collections; criterion IDs must belong to it. |
| POST | `/{id}/copy` | Independently copy a Published edition into a new Draft (201). |
| POST | `/{id}/publish` | Validate, then Draft to Published. |
| GET | `/periods/{periodId}` | Bound edition, unmapped selected Roles and initial-binding availability. |
| POST | `/periods/{periodId}/bind` | Explicit initial binding, body `{"configurationId": 1}`; only unbound Open periods. |

Create/update body example (illustrative content, not defaults):

```json
{
  "name": "Attitude criteria 2027",
  "criteria": [{"name": "Integrity", "description": "Demonstrates honest conduct.", "criterionType": "SHARED_CORE_VALUE", "evaluationFormat": null, "active": true}],
  "ratingDefinitions": [{"point": 1, "label": "Administrator-defined label", "description": "Administrator-defined expectations"}],
  "roleMappings": [{"roleId": 4, "evaluationFormat": "SALES"}]
}
```

The example is an incomplete Draft; publication needs all five complete ratings. Null/omitted collections mean empty collections on full replacement, not a partial patch. Criterion order follows request order. Matching criterion IDs, rating points and Role mappings are retained; removed children are deleted only from Drafts.

## Validation and history

Drafts may omit name, criteria text, rating definitions or Role mappings. Supplied scopes/references/points must be structurally valid. Points are JSON integers 1-5, not fractional/string/Boolean values. Duplicate points, Role mappings, foreign criterion IDs, ineligible Roles and duplicate criterion names within a scope are rejected.

Publication requires a name, all five complete ratings, complete active criterion text and at least one active applicable criterion for each fixed format. Inactive criteria are preserved but excluded from future evaluation selection. Mapped Roles are rechecked for eligibility. Unmapped Roles are reported per period instead of requiring every current Role to be mapped or blocking KPI workflows. The later attitude assessment slice must block an unmapped participant Role rather than inferring its format.

Technical limits: names/labels 255 characters, descriptions 10,000. These are implementation limits, not claimed TBM policies. No criterion weighting, mandatory comments or file policy is introduced.

Published editions/children are read-only. There is no delete or replace-bound-edition action. Copies contain independent children; publishing affects future openings only. Annual period responses expose binding ID/name read-only; UC-01/UC-02 requests cannot set them.

Role mappings remain keyed by Role ID, not name. Responses include `departmentName` from the inherited `Role.orgChart` relationship, also used by Review Period Role options. Same-named Roles in separate departments remain independently configurable. Department names are display context, not a new historical snapshot or a change to configuration ownership.

Setup displays: "The latest published configuration is automatically reused for future Review Periods. Republish only when changes are needed. Existing periods remain unaffected." Automatic binding occurs at opening, not when a Draft is created or a configuration is published.

The setup page exposes the existing initial-binding action for Open, unbound periods. If no published edition exists, show: "No published Attitude Evaluation Configuration is available. Publish a configuration to enable Attitude Evaluations for this review period." Publishing alone does not retrospectively bind an already-Open period; the administrator must explicitly bind it. Bound periods cannot be rebound.

Employee attitude-assessment availability remains part of the later UC-10 slice. Missing binding must show "Attitude Evaluation Not Yet Available" and "The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later." Do not expose administrator configuration endpoints to employees, treat a globally published edition as an existing period binding, or disable KPI activities. A binding is necessary but not sufficient: the later assessment service must also validate participant Role mapping and its normal availability rules.

Actual Open transitions bind the latest published edition atomically: direct publication, Draft publication, rescheduled Upcoming opening and scheduler opening. Draft saves, previews, GETs and subsequent configuration publication do not bind existing periods. Opening is allowed without a configuration; missing configuration blocks future attitude assessments only, not KPI assessments. V40 leaves existing Open periods unbound for explicit initial binding. Upcoming/Closed periods cannot use that action, and existing bindings cannot be replaced.

Mutation/publication and binding reuse the existing annual-configuration transaction lock. No generic workflow engine or extra audit tables.

## Verification and boundaries

Apply V40 using the backed-up isolated `ApplyAnnualReviewPeriodFoundation.java` runner; partial/untracked tables/columns are refused. Stop the backend before compiling new entities. Applied migrations and global Hibernate/Flyway settings remain unchanged. PostgreSQL tests use `APEX_PHASE3_POSTGRES_TEST=true` with rollback-only schemas, never live fixture inserts.

The existing manual KPI UAT clone remains unchanged at V39. Its source fingerprints describe the pre-V40 development baseline; the binding column and confirmed permission grant change that schema/RBAC baseline. Do not bypass or silently reset its checks. Assess/migrate the disposable clone and explicitly establish a verified new baseline before later UAT use for this slice.

Deferred: attitude assessments/scoring, changing an already-bound edition, reporting changes, participant refresh, closure with pending assessments and KPI Revision. No reopening/recalculation workflow is invented.
