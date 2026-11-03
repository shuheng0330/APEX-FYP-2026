import { KpiPeriodContext } from './kpi-plan.model';

export type KpiAssistanceStatus = 'REQUESTED' | 'AUTHORIZED' | 'REJECTED' | 'CONSUMED';
export interface KpiAssistanceEmployee {
  ownerParticipantId: number; employeeId: string; employeeName: string; departmentName: string | null;
  reviewPeriodId: number; reviewPeriodName: string;
}
export interface KpiAssistance extends KpiAssistanceEmployee {
  id: number; superiorId: string; superiorName: string; status: KpiAssistanceStatus;
  reviewPeriodStatus: KpiPeriodContext['status']; requestedAt: string; requestReason: string | null;
  authorizedAt: string | null; authorizedById: string | null; authorizedByName: string | null;
  rejectedAt: string | null; rejectedById: string | null; rejectedByName: string | null;
  rejectionReason: string | null; consumedAt: string | null; planId: number | null;
}
