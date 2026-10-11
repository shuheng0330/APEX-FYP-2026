import { KpiItem, KpiLevel, KpiPeriodContext, KpiPlanChange } from './kpi-plan.model';

export type KpiVersionStatus = 'REQUESTED' | 'DRAFT' | 'RETURNED' | 'PENDING_APPROVAL' | 'PENDING_MD_APPROVAL' |
  'PENDING_HOD_CONFIRMATION' | 'EFFECTIVE' | 'SUPERSEDED' | 'CANCELLED';
export type KpiRevisionAction = 'EDIT' | 'PREPARE' | 'SUBMIT' | 'APPROVE' | 'RETURN' | 'CONFIRM' | 'CANCEL';
export type KpiRevisionDecision = Exclude<KpiRevisionAction, 'EDIT'>;
export type KpiRevisionPreparationMode = 'SUPERIOR' | 'HR_MD';
export interface KpiRevision {
  id: number; planId: number; reviewPeriodId: number; reviewPeriodName: string;
  reviewPeriodStatus: KpiPeriodContext['status']; level: KpiLevel;
  departmentId: number | null; departmentName: string | null;
  ownerParticipantId?: number | null; employeeId?: string | null; employeeName?: string | null;
  preparationMode?: KpiRevisionPreparationMode | null; revisionSuperiorId?: string | null;
  versionNumber: number; baseVersionId: number | null; status: KpiVersionStatus;
  revisionReason: string | null; items: KpiItem[]; totalWeightage: number;
  changes: KpiPlanChange[]; targetChangedItemIds: number[];
  blockers: string[]; allowedActions: KpiRevisionAction[]; lockVersion: number;
  createdAt: string; createdBy: string; updatedAt: string; updatedBy: string;
  requestedBy: string | null; preparedBy: string | null; preparedAt: string | null;
  submittedAt: string | null; approvedBy: string | null; approvedAt: string | null;
  confirmedBy: string | null; confirmedAt: string | null; effectiveAt: string | null;
  cancelledBy: string | null; cancelledAt: string | null; cancellationReason: string | null;
  cancelledFromState: KpiVersionStatus | null; latestReturnReason: string | null;
  latestReturnedBy: string | null; latestReturnedAt: string | null; revisionRequired: boolean;
  decisionHistory: { action: string; actor: string; at: string; from: string; to: string; reason: string | null }[];
  actorNames: Record<string, string>;
}
export interface KpiAmendmentAccess {
  reviewPeriodId: number; reviewPeriodName: string; periodStatus: KpiPeriodContext['status'];
  enabled: boolean; closeDate: string | null; available: boolean; expired: boolean;
  changedAt: string | null; changedBy: string | null;
}
export const revisionAuthorities = ['CAN_MANAGE_KPI_AMENDMENTS', 'CAN_REVISE_COMPANY_KPI',
  'CAN_PREPARE_DEPARTMENT_KPI_REVISION', 'CAN_APPROVE_DEPARTMENT_KPI', 'CAN_MANAGE_DEPARTMENT_KPI',
  'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'];
export function unfinishedRevision(version: KpiRevision): boolean {
  return !['EFFECTIVE', 'SUPERSEDED', 'CANCELLED'].includes(version.status);
}
export function validRevisionReason(reason: string): boolean {
  return !!reason.trim() && reason.trim().length <= 10000;
}
export function targetChanged(item: KpiItem, baseline?: KpiItem): boolean {
  const normalize = (text: string | null | undefined) => (text ?? '').trim().replace(/\s+/g, ' ');
  return !!baseline && normalize(item.target) !== normalize(baseline.target);
}
