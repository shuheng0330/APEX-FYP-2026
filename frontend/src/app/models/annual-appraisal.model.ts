import { KpiScoreBreakdown, KpiAssessmentStatus } from './kpi-assessment.model';
import { KpiLevel, KpiPeriodContext } from './kpi-plan.model';

export const annualRecommendations = ['PROMOTION', 'SALARY_INCREMENT', 'BOTH', 'NO_RECOMMENDATION'] as const;
export type AnnualRecommendation = typeof annualRecommendations[number];
export type AnnualAppraisalStatus = 'DRAFT' | 'PENDING_REVIEW' | 'RETURNED' | 'APPROVED';
export type AnnualAppraisalAction = 'SAVE_DRAFT' | 'SUBMIT' | 'RETURN' | 'APPROVE' | 'OVERRIDE_AND_APPROVE';
export interface AnnualAppraisalSnapshot {
  participantId: number; reviewPeriodId: number; employeeId: string;
  reviewFrequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
  consolidationMethod: 'FINAL_CHECKPOINT' | 'AVERAGE' | null;
  kpiWeight: number | null; attitudeWeight: number | null;
  annualKpiScore: number | null; attitudeScore: number | null; finalScore: number | null;
  checkpoints: {
    checkpointId: number; sequenceNumber: number; startDate: string; endDate: string;
    assessmentId: number | null; status: KpiAssessmentStatus | null; score: number | null;
    reviewedAt: string | null;
    versions: { level: KpiLevel; versionId: number; versionNumber: number }[];
    breakdown: KpiScoreBreakdown | null;
  }[];
  attitude: {
    assessmentId: number; configurationId: number; format: 'MANAGER' | 'SALES' | 'OTHERS';
    status: KpiAssessmentStatus; reviewedAt: string | null;
    criteria: { criterionId: number; name: string; description: string | null; superiorPoint: number | null }[];
  } | null;
}
export interface AnnualAppraisalReadiness {
  ready: boolean; blockers: string[]; performance: AnnualAppraisalSnapshot;
}
export interface AnnualPerformanceHistory {
  employeeId: string; participantId: number; reviewPeriodId: number;
  periodName: string; startDate: string; endDate: string;
  annualKpiScore: number; attitudeScore: number; finalScore: number;
  kpiWeight: number; attitudeWeight: number;
  consolidationMethod: NonNullable<AnnualAppraisalSnapshot['consolidationMethod']>;
  sourceType: 'SUBMISSION_SNAPSHOT' | 'CALCULATED_RESULTS';
  comparisonStatus: 'UNCHANGED' | 'DIFFERENT' | 'CURRENT_RESULTS_UNAVAILABLE' | 'NOT_APPLICABLE';
}
export interface AnnualAppraisalCandidate {
  participantId: number; employeeId: string; employeeName: string | null;
  departmentName: string | null; roleName: string | null;
  appraisalId: number | null; status: AnnualAppraisalStatus | null; readiness: AnnualAppraisalReadiness;
}
export interface AnnualAppraisalActivity {
  id: number; action: 'DRAFT_CREATED' | 'DRAFT_SAVED' | 'SUBMITTED' | 'RESUBMITTED' | 'APPROVED' | 'OVERRIDDEN' | 'RETURNED';
  actorId: string; occurredAt: string; reason: string | null;
  recommendation: AnnualRecommendation | null; justification: string | null;
  finalDecision: AnnualRecommendation | null; snapshot: AnnualAppraisalSnapshot | null;
}
export interface AnnualAppraisal {
  id: number; participantId: number; reviewPeriodId: number; employeeId: string; superiorId: string;
  employeeName: string | null; departmentName: string | null; roleName: string | null;
  reviewPeriodName: string; reviewPeriodStatus: KpiPeriodContext['status']; status: AnnualAppraisalStatus;
  recommendation: AnnualRecommendation | null; finalDecision: AnnualRecommendation | null;
  justification: string | null; overrideReason: string | null; latestReturnReason: string | null;
  snapshot: AnnualAppraisalSnapshot | null;
  createdAt: string; updatedAt: string; submittedAt: string | null; reviewedAt: string | null;
  createdBy: string; updatedBy: string; reviewedBy: string | null;
  submittedLate: boolean | null; reviewedLate: boolean | null; lockVersion: number;
  recommendationDeadline: string | null; hrReviewDeadline: string | null;
  recommendationOverdue: boolean; hrReviewOverdue: boolean; sourceChanged: boolean;
  readiness: AnnualAppraisalReadiness; submissionBlockers: string[]; reviewBlockers: string[];
  allowedActions: AnnualAppraisalAction[]; activity: AnnualAppraisalActivity[];
}
export interface AnnualAppraisalRequest {
  participantId: number; expectedLockVersion?: number;
  recommendation: AnnualRecommendation | null; justification: string | null;
}
export interface AnnualAppraisalActionRequest {
  expectedLockVersion: number; finalDecision?: AnnualRecommendation | null; reason?: string | null;
}

export function annualJustificationError(value: string, required = false): string | null {
  if (value.length > 10000) return 'ANNUAL_APPRAISAL.TOO_LONG';
  return required && !value.trim() ? 'ANNUAL_APPRAISAL.REQUIRED' : null;
}
