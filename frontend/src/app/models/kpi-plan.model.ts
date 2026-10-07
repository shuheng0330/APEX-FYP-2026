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
}
export interface KpiPlanRequest {
  reviewPeriodId: number; departmentId?: number; ownerParticipantId?: number; items: KpiItem[];
}
export interface KpiPlan extends KpiPlanRequest {
  id: number; reviewPeriodName: string; reviewPeriodStatus: KpiPeriodContext['status'];
  level: KpiLevel; status: KpiPlanStatus; departmentName?: string; employeeName?: string;
  kpiSetupDeadline: string | null; totalWeightage: number; overdue: boolean;
  createdAt: string; updatedAt: string;
}
export function emptyKpiItem(): KpiItem {
  return { name: null, description: null, perspective: null, kra: null, target: null, measurementUnit: null, weightage: null, scoringDefinitions: {} };
}
export function kpiPlanComplete(items: KpiItem[]): boolean {
  const names = items.map(i => i.name?.trim().toLowerCase());
  return items.length > 0 && new Set(names).size === names.length && items.every(i =>
    !!i.name?.trim() && !!i.target?.trim() && !!i.measurementUnit?.trim() && i.weightage !== null &&
    i.weightage >= 0 && i.weightage <= 100 && Math.abs(i.weightage * 100 - Math.round(i.weightage * 100)) < 1e-7 &&
    [1, 2, 3, 4, 5].every(p => !!i.scoringDefinitions[p]?.trim())) &&
    items.reduce((n, i) => n + Math.round((i.weightage ?? 0) * 100), 0) === 10000;
}
