export const ANNUAL_REVIEW_PERMISSION = 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD';
export type ReviewPeriodStatus = 'DRAFT' | 'UPCOMING' | 'OPEN' | 'CLOSED';
export type ReviewFrequency = 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
export type ConsolidationMethod = 'FINAL_CHECKPOINT' | 'AVERAGE';
export const REVIEW_FREQUENCIES: ReviewFrequency[] = ['MONTHLY', 'QUARTERLY', 'ANNUALLY'];

export interface ReviewRoleConfiguration {
  roleId: number;
  roleName: string;
  departmentName: string | null;
  reviewFrequency: ReviewFrequency;
  employeeLevelId: number | null;
  employeeLevelName: string | null;
}

export interface EmployeeLevelConfiguration {
  id?: number | null;
  employeeLevelId: number;
  employeeLevelName?: string;
  employeeLevelCode?: string;
  companyKpiWeight: number | null;
  departmentKpiWeight: number | null;
  individualKpiWeight: number | null;
}

export const REVIEW_DATE_FIELDS = [
  'startDate', 'endDate', 'kpiSetupDeadline',
  'attitudeSelfAssessmentDeadline', 'superiorAttitudeEvaluationDeadline',
  'appraisalRecommendationDeadline', 'hrFinalisationDeadline'
] as const;
export type ReviewDateField = typeof REVIEW_DATE_FIELDS[number];

export type AnnualReviewPeriodRequest = Record<ReviewDateField, string | null> & {
  name: string | null;
  employeeLevelConfigurations: EmployeeLevelConfiguration[] | null;
  roleConfigurations: { roleId: number; reviewFrequency: ReviewFrequency | null }[];
  kpiPerformanceWeight: number | null;
  attitudeEvaluationWeight: number | null;
  annualKpiConsolidationMethod: ConsolidationMethod | null;
  selfAssessmentDaysAfterCheckpoint: number | null;
  superiorAssessmentDaysAfterSelfDeadline: number | null;
};

export interface ReviewCheckpoint {
  id: number | null;
  reviewFrequency: ReviewFrequency;
  sequenceNumber: number;
  startDate: string;
  endDate: string;
  selfAssessmentDeadline: string;
  superiorAssessmentDeadline: string;
}

export interface AnnualReviewPeriod extends Omit<AnnualReviewPeriodRequest, 'roleConfigurations' | 'employeeLevelConfigurations'> {
  id: number | null;
  referenceNumber: string | null;
  status: ReviewPeriodStatus;
  roleConfigurations: ReviewRoleConfiguration[];
  employeeLevelConfigurations: EmployeeLevelConfiguration[];
  checkpoints: ReviewCheckpoint[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface AnnualReviewCreationDefaults {
  sourceReviewPeriodId: number | null;
  sourceReviewPeriodName?: string | null;
  employeeLevelConfigurations: EmployeeLevelConfiguration[];
  roleConfigurations: ReviewRoleConfiguration[];
}

// Date pickers use local calendar dates. Do not serialize them via UTC toISOString().
export function calendarDate(value: Date | null): string | null {
  if (!value || Number.isNaN(value.getTime())) return null;
  return `${value.getFullYear().toString().padStart(4, '0')}-${(value.getMonth() + 1).toString().padStart(2, '0')}-${value.getDate().toString().padStart(2, '0')}`;
}

export function pickerDate(value: string | null): Date | null {
  if (!value) return null;
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function editableReviewPeriod(period: Pick<AnnualReviewPeriod, 'status' | 'startDate'>, today = calendarDate(new Date())!): boolean {
  return period.status === 'DRAFT' || (period.status === 'UPCOMING' && !!period.startDate && period.startDate > today);
}

export function weightTotal(...values: (number | null)[]): number | null {
  return values.some(value => value === null) ? null : Math.round(values.reduce<number>((sum, value) => sum + (value ?? 0), 0) * 100) / 100;
}
