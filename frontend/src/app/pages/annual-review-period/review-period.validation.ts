import { AnnualReviewPeriodRequest, ReviewDateField, ReviewRoleConfiguration, weightTotal, pickerDate, calendarDate } from '../../models/annual-review-period.model';

export type ReviewSection = 'basic' | 'roles' | 'levels' | 'composition' | 'consolidation' | 'setup' | 'assessment' | 'attitude';
export interface ReviewValidationIssue { section: ReviewSection; field: string; key: string; params?: Record<string, string | number>; }
export const SETUP_DEADLINES: ReviewDateField[] = ['companyKpiCreationDeadline', 'departmentKpiCreationDeadline', 'individualKpiSubmissionDeadline', 'individualKpiApprovalDeadline'];
export const FINAL_DEADLINES: ReviewDateField[] = ['attitudeSelfAssessmentDeadline', 'superiorAttitudeEvaluationDeadline', 'appraisalRecommendationDeadline', 'hrFinalisationDeadline'];

export function validateReviewConfiguration(request: AnnualReviewPeriodRequest, publish: boolean,
  levelIds: number[], roles: ReviewRoleConfiguration[]): ReviewValidationIssue[] {
  const issues: ReviewValidationIssue[] = [];
  const add = (section: ReviewSection, field: string, key: string, params?: Record<string, string | number>) => issues.push({ section, field, key: `REVIEW_PERIOD.ERROR.${key}`, params });
  const required = (section: ReviewSection, field: string, value: unknown) => {
    if (publish && (value === null || value === undefined || value === '')) add(section, field, 'REQUIRED');
  };
  required('basic', 'name', request.name?.trim());
  if (request.name !== null && (!request.name.trim() || request.name.length > 255)) add('basic', 'name', 'NAME');
  required('basic', 'startDate', request.startDate); required('basic', 'endDate', request.endDate);
  if (request.startDate && request.endDate && request.startDate >= request.endDate) add('basic', 'endDate', 'DATE_ORDER');
  if (publish && !request.roleConfigurations.length) add('roles', 'roles', 'ROLES');
  for (const item of request.roleConfigurations) {
    const role = roles.find(role => role.roleId === item.roleId);
    if (publish && !role?.employeeLevelId) add('roles', `role-${item.roleId}`, 'ROLE_LEVEL', { role: role?.roleName ?? item.roleId });
  }
  const checkWeight = (section: ReviewSection, field: string, value: number | null) => {
    required(section, field, value);
    if (value !== null && (!Number.isFinite(value) || value < 0 || value > 100 || Math.abs(value * 100 - Math.round(value * 100)) > 0.000001)) add(section, field, 'WEIGHT');
  };
  const weights = request.employeeLevelConfigurations ?? [];
  if (publish && (weights.length !== levelIds.length || levelIds.some(id => !weights.some(w => w.employeeLevelId === id)))) add('levels', 'levels', 'LEVELS');
  weights.forEach(w => {
    checkWeight('levels', `company-${w.employeeLevelId}`, w.companyKpiWeight);
    checkWeight('levels', `department-${w.employeeLevelId}`, w.departmentKpiWeight);
    checkWeight('levels', `individual-${w.employeeLevelId}`, w.individualKpiWeight);
    if (publish && weightTotal(w.companyKpiWeight, w.departmentKpiWeight, w.individualKpiWeight) !== 100) add('levels', `total-${w.employeeLevelId}`, 'TOTAL');
  });
  checkWeight('composition', 'kpiPerformanceWeight', request.kpiPerformanceWeight);
  checkWeight('composition', 'attitudeEvaluationWeight', request.attitudeEvaluationWeight);
  if (publish && weightTotal(request.kpiPerformanceWeight, request.attitudeEvaluationWeight) !== 100) add('composition', 'composition', 'TOTAL');
  required('consolidation', 'annualKpiConsolidationMethod', request.annualKpiConsolidationMethod);
  SETUP_DEADLINES.forEach(field => {
    required('setup', field, request[field]);
    if (request[field] && request.startDate && request[field]! > request.startDate) add('setup', field, 'SETUP_DATE');
  });
  for (const field of ['selfAssessmentDaysAfterCheckpoint', 'superiorAssessmentDaysAfterSelfDeadline'] as const) {
    const days = request[field];
    required('assessment', field, days);
    if (days !== null && (!Number.isInteger(days) || days <= 0 || days > 2147483647)) add('assessment', field, 'DAYS');
  }
  FINAL_DEADLINES.forEach(field => required('attitude', field, request[field]));
  const after = (first: ReviewDateField, next: ReviewDateField) => {
    if (request[first] && request[next] && request[first]! >= request[next]!) add('attitude', next, 'DEADLINE_ORDER');
  };
  after('attitudeSelfAssessmentDeadline', 'superiorAttitudeEvaluationDeadline');
  after('appraisalRecommendationDeadline', 'hrFinalisationDeadline');
  if (publish && request.startDate && request.attitudeSelfAssessmentDeadline && request.attitudeSelfAssessmentDeadline < request.startDate) add('attitude', 'attitudeSelfAssessmentDeadline', 'ATTITUDE_DATE');
  if (publish && request.appraisalRecommendationDeadline) {
    const end = pickerDate(request.endDate);
    const offsets = (request.selfAssessmentDaysAfterCheckpoint ?? 0) + (request.superiorAssessmentDaysAfterSelfDeadline ?? 0);
    if (end) end.setDate(end.getDate() + offsets);
    const finalDeadline = calendarDate(end);
    if ((finalDeadline && request.appraisalRecommendationDeadline < finalDeadline) ||
      (request.superiorAttitudeEvaluationDeadline && request.appraisalRecommendationDeadline < request.superiorAttitudeEvaluationDeadline)) add('attitude', 'appraisalRecommendationDeadline', 'APPRAISAL_DATE');
  }
  return issues;
}
