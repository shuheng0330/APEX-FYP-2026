import { KpiItem, KpiLevel, KpiPeriodContext } from './kpi-plan.model';

export type KpiAssessmentStatus = 'DRAFT' | 'PENDING_REVIEW' | 'REVIEWED';
export interface KpiAssessmentCheckpoint {
  id: number; reviewFrequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY'; sequenceNumber: number;
  startDate: string; endDate: string; availableFrom: string;
  selfAssessmentDeadline: string; superiorAssessmentDeadline: string;
  available: boolean; overdue: boolean; assessmentId: number | null; assessmentStatus: KpiAssessmentStatus | null;
}
export interface KpiAssessmentEvidence {
  id: number; originalFilename: string; contentType: string; sizeBytes: number;
  uploadedBy: string; uploadedAt: string;
}
export interface KpiAssessmentItem {
  id: number | null; assignmentId: number; level: KpiLevel; kpi: KpiItem;
  selfPoint: number | null; selfComment: string | null;
  superiorPoint: number | null; superiorComment: string | null;
  evidence: KpiAssessmentEvidence[];
}
export interface KpiAssessment {
  id: number | null; participantId: number; employeeName: string;
  roleName?: string | null;
  departmentName?: string | null;
  reviewPeriodId: number; reviewPeriodName: string; reviewPeriodStatus: KpiPeriodContext['status'];
  checkpoint: KpiAssessmentCheckpoint; status: KpiAssessmentStatus; items: KpiAssessmentItem[];
  kpiAllocation: KpiPeriodContext['kpiAllocation']; missingLevels: KpiLevel[]; submissionBlockers: string[];
  canSaveDraft: boolean; canSubmit: boolean; overdue: boolean;
  canSaveSuperiorDraft?: boolean; canCompleteReview?: boolean; superiorOverdue?: boolean; reviewBlockers?: string[];
  createdAt: string | null; updatedAt: string | null; submittedAt: string | null; submittedBy: string | null;
  submittedToSuperiorId: string | null; submittedLate: boolean | null;
  reviewedAt: string | null; reviewedBy: string | null; reviewedLate: boolean | null; checkpointScore: number | null;
}
export interface KpiAssessmentRequest {
  checkpointId: number;
  items: { assignmentId: number; selfPoint: number | null; selfComment: string | null }[];
}
export type KpiAssessmentReviewStatus = Exclude<KpiAssessmentStatus, 'DRAFT'>;
export interface KpiAssessmentReview {
  id: number; employeeId: string; employeeName: string; roleName: string | null; departmentName: string | null;
  reviewPeriodId: number; reviewPeriodName: string; reviewPeriodStatus: KpiPeriodContext['status'];
  checkpoint: KpiAssessmentCheckpoint; status: KpiAssessmentReviewStatus;
  submittedAt: string; submittedLate: boolean; reviewedAt: string | null; reviewedLate: boolean | null;
  checkpointScore: number | null; canReview: boolean; superiorOverdue: boolean;
}
export interface KpiSuperiorAssessmentRequest {
  items: { itemId: number; superiorPoint: number | null; superiorComment: string | null }[];
}
export function superiorAssessmentItemError(item: KpiAssessmentItem, completing = false): string | null {
  return assessmentItemError({ ...item, selfPoint: item.superiorPoint, selfComment: item.superiorComment }, completing);
}
export function assessmentItemError(item: KpiAssessmentItem, submitting = false): string | null {
  if (item.selfPoint === null || item.selfPoint === undefined) {
    if (submitting) return 'MY_ASSESSMENTS.POINT_REQUIRED';
  } else if (!Number.isInteger(item.selfPoint) || item.selfPoint < 1 || item.selfPoint > 5) {
    return 'MY_ASSESSMENTS.INVALID_POINT';
  }
  return (item.selfComment?.length ?? 0) > 10000 ? 'MY_ASSESSMENTS.COMMENT_TOO_LONG' : null;
}
