import { revisionAuthorities } from '../../models/kpi-revision.model';

export type OrganisationReviewTab = 'department' | 'assistance' | 'revisions';
export type TeamReviewTab = 'reviews' | 'assistance' | 'revisions' | 'assessments' | 'attitude';
type HasPermission = (permission: string) => boolean;
export const organisationReviewPath = '/kpi-management/kpi-review';
export const teamReviewPath = '/team-performance/team-reviews';

export function organisationReviewTabs(has: HasPermission): OrganisationReviewTab[] {
  return [
    ...(has('CAN_APPROVE_DEPARTMENT_KPI') ? ['department' as const] : []),
    ...(has('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE') ? ['assistance' as const] : []),
    ...(revisionAuthorities.some(has) ? ['revisions' as const] : [])
  ];
}

export function teamReviewTabs(has: HasPermission): TeamReviewTab[] {
  return [
    ...(has('CAN_REVIEW_INDIVIDUAL_KPI') ? ['reviews' as const, 'assistance' as const, 'revisions' as const] : []),
    ...(has('CAN_REVIEW_KPI_ASSESSMENT') ? ['assessments' as const] : []),
    ...(has('CAN_REVIEW_ATTITUDE_EVALUATION') ? ['attitude' as const] : [])
  ];
}

export interface ReviewTask {
  id: string;
  path: string;
  tab?: OrganisationReviewTab | TeamReviewTab;
  destination: string;
  permissions: readonly string[];
}

// Workspace visibility only: record scope and available actions still come from the backend.
const tasks: readonly ReviewTask[] = [
  { id: 'ANNUAL_RECOMMENDATION', path: '/team-performance/team-appraisals', destination: 'ANNUAL_APPRAISAL.TEAM_TITLE', permissions: ['CAN_SUBMIT_ANNUAL_APPRAISAL'] },
  { id: 'ANNUAL_REVIEW', path: '/appraisals/review', destination: 'ANNUAL_APPRAISAL.HR_TITLE', permissions: ['CAN_REVIEW_ANNUAL_APPRAISAL'] },
  { id: 'COMPANY', path: '/kpi-management/company-kpis', destination: 'KPI_PLAN.COMPANY_TITLE', permissions: ['CAN_MANAGE_COMPANY_KPI'] },
  { id: 'COMPANY_REVISION', path: organisationReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.TITLE', permissions: ['CAN_REVISE_COMPANY_KPI'] },
  { id: 'DEPARTMENT', path: '/kpi-management/department-kpis', destination: 'DEPARTMENT_KPI.TITLE', permissions: ['CAN_MANAGE_DEPARTMENT_KPI'] },
  { id: 'DEPARTMENT_APPROVAL', path: organisationReviewPath, tab: 'department', destination: 'DEPARTMENT_REVIEW.TITLE', permissions: ['CAN_APPROVE_DEPARTMENT_KPI'] },
  { id: 'ASSISTANCE_DECISION', path: organisationReviewPath, tab: 'assistance', destination: 'KPI_ASSISTANCE.HR_TITLE', permissions: ['CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'] },
  { id: 'AMENDMENTS', path: organisationReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.TITLE', permissions: ['CAN_MANAGE_KPI_AMENDMENTS'] },
  { id: 'DEPARTMENT_PREPARATION', path: organisationReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.TITLE', permissions: ['CAN_PREPARE_DEPARTMENT_KPI_REVISION'] },
  { id: 'DEPARTMENT_REVISION_APPROVAL', path: organisationReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.TITLE', permissions: ['CAN_APPROVE_DEPARTMENT_KPI'] },
  { id: 'INDIVIDUAL_INITIATION', path: organisationReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.TITLE', permissions: ['CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'] },
  { id: 'INDIVIDUAL_APPROVAL', path: teamReviewPath, tab: 'reviews', destination: 'TEAM_REVIEWS.TYPE', permissions: ['CAN_REVIEW_INDIVIDUAL_KPI'] },
  { id: 'ASSISTANCE_PREPARATION', path: teamReviewPath, tab: 'assistance', destination: 'KPI_ASSISTANCE.TITLE', permissions: ['CAN_REVIEW_INDIVIDUAL_KPI'] },
  { id: 'INDIVIDUAL_REVISION', path: teamReviewPath, tab: 'revisions', destination: 'KPI_REVISIONS.INDIVIDUAL_TITLE', permissions: ['CAN_REVIEW_INDIVIDUAL_KPI'] },
  { id: 'KPI_ASSESSMENT', path: teamReviewPath, tab: 'assessments', destination: 'KPI_ASSESSMENT_REVIEW.TITLE', permissions: ['CAN_REVIEW_KPI_ASSESSMENT'] },
  { id: 'ATTITUDE_ASSESSMENT', path: teamReviewPath, tab: 'attitude', destination: 'ATTITUDE_REVIEW.TITLE', permissions: ['CAN_REVIEW_ATTITUDE_EVALUATION'] },
  { id: 'MY_PLAN', path: '/my-performance/my-kpi-plan', destination: 'INDIVIDUAL_KPI.TITLE', permissions: ['ROLE_USER'] },
  { id: 'MY_ASSESSMENTS', path: '/my-performance/my-assessments', destination: 'MY_ASSESSMENTS.TITLE', permissions: ['ROLE_USER'] }
];

export function reviewTasks(has: HasPermission): readonly ReviewTask[] {
  return tasks.filter(task => task.permissions.some(has));
}

export function permittedTab<T extends string>(requested: string | null, available: readonly T[]): T | null {
  return available.find(tab => tab === requested) ?? available[0] ?? null;
}
