export type KpiLevel = 'COMPANY' | 'DEPARTMENT' | 'INDIVIDUAL';
export type KpiPlanStatus = 'DRAFT' | 'PUBLISHED' | 'PENDING_APPROVAL' | 'RETURNED' | 'APPROVED';
export interface KpiItem {
  id?: number;
  name: string | null;
  description: string | null;
  perspective: string | null;
  kra: string | null;
  target: string | null;
  measurementUnit: string | null;
  weightage: number | null;
  scoringDefinitions: Record<number, string>;
}
export interface KpiPeriodContext {
  id: number; name: string | null; status: 'DRAFT' | 'UPCOMING' | 'OPEN' | 'CLOSED';
  startDate: string | null; endDate: string | null; kpiSetupDeadline: string | null;
  participantsSnapshottedAt?: string | null;
}
export interface KpiPlanRequest {
  reviewPeriodId: number; departmentId?: number; ownerParticipantId?: number; items: KpiItem[];
}
export interface KpiPlan extends KpiPlanRequest {
  id: number; reviewPeriodName: string; reviewPeriodStatus: KpiPeriodContext['status'];
  level: KpiLevel; status: KpiPlanStatus; departmentName?: string; employeeName?: string;
  kpiSetupDeadline: string | null; totalWeightage: number; overdue: boolean;
  createdAt: string; updatedAt: string;
  publishedAt?: string; publishedLate?: boolean;
}
export function emptyKpiItem(): KpiItem {
  return { name: null, description: null, perspective: null, kra: null, target: null, measurementUnit: null, weightage: null, scoringDefinitions: {} };
}
export type KpiItemErrors = Record<string, string>;
export function kpiItemErrors(item: KpiItem): KpiItemErrors {
  const errors: KpiItemErrors = {};
  for (const field of ['perspective', 'kra', 'name', 'target'] as const) {
    const value = item[field];
    if (!value?.trim()) errors[field] = 'KPI_PLAN.REQUIRED';
    else if (value.length > (field === 'target' ? 10000 : 255)) errors[field] = 'KPI_PLAN.TOO_LONG';
  }
  const weight = item.weightage;
  if (weight === null || weight === undefined) errors['weightage'] = 'KPI_PLAN.REQUIRED';
  else if (!Number.isFinite(weight) || weight < 0 || weight > 100 ||
    Math.abs(weight * 100 - Math.round(weight * 100)) > 1e-7) errors['weightage'] = 'KPI_PLAN.INVALID_WEIGHT';
  for (const point of [1, 2, 3, 4, 5]) {
    const criterion = item.scoringDefinitions[point];
    if (!criterion?.trim()) errors['point' + point] = 'KPI_PLAN.REQUIRED_CRITERION';
    else if (criterion.length > 10000) errors['point' + point] = 'KPI_PLAN.TOO_LONG';
  }
  return errors;
}
export function kpiPlanComplete(items: KpiItem[]): boolean {
  const names = items.map(i => i.name?.trim().toLowerCase());
  return items.length > 0 && new Set(names).size === names.length && items.every(i => Object.keys(kpiItemErrors(i)).length === 0) &&
    items.reduce((n, i) => n + Math.round((i.weightage ?? 0) * 100), 0) === 10000;
}
