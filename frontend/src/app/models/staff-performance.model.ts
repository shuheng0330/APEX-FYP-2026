import { AnnualAppraisalSnapshot, AnnualRecommendation } from './annual-appraisal.model';
import { AttitudeFormat, AttitudeRating } from './attitude-configuration.model';
import { KpiAssessmentModel, KpiAssessmentStatus, KpiScoreBreakdown } from './kpi-assessment.model';
import { KpiItem, KpiLevel, KpiPeriodContext } from './kpi-plan.model';

export interface PerformancePeriod {
  participantId: number; id: number; name: string; startDate: string; endDate: string;
  status: KpiPeriodContext['status']; reviewFrequency: 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
  frequencyPolicy: 'ROLE_BASED' | 'DEPARTMENT_BASED'; assessmentModel: KpiAssessmentModel;
}
export interface PerformanceScores {
  annualKpiScore: number | null; attitudeScore: number | null; finalScore: number | null;
  kpiWeight: number | null; attitudeWeight: number | null;
  consolidationMethod: 'AVERAGE' | 'FINAL_CHECKPOINT' | null;
}
export interface PerformanceCheckpoint {
  id: number; sequenceNumber: number; startDate: string; endDate: string;
  assessmentId: number | null; status: KpiAssessmentStatus | null; score: number | null;
  submittedAt: string | null; reviewedAt: string | null;
}
export interface PerformanceKpiAnswer {
  id: number; level: KpiLevel; kpi: KpiItem; selfPoint: number | null; selfComment: string | null;
  superiorPoint: number | null; superiorComment: string | null;
}
export interface PerformanceCheckpointDetails {
  period: PerformancePeriod; checkpoint: PerformanceCheckpoint; assessmentModel: KpiAssessmentModel;
  breakdown: KpiScoreBreakdown | null; answers: PerformanceKpiAnswer[];
}
export interface PerformanceAttitude {
  assessmentId: number | null; configurationId: number | null; configurationName: string | null;
  format: AttitudeFormat | null; status: KpiAssessmentStatus | null; score: number | null;
  submittedAt: string | null; reviewedAt: string | null; ratingDefinitions: AttitudeRating[];
  criteria: { id: number; name: string; description: string | null; criterionType: 'SHARED_CORE_VALUE' | 'FORMAT_SPECIFIC';
    selfPoint: number | null; selfComment: string | null; superiorPoint: number | null; superiorComment: string | null }[];
}
export interface PerformanceApprovedAppraisal {
  id: number; period: PerformancePeriod; status: 'APPROVED'; finalDecision: AnnualRecommendation;
  approvedAt: string; recordedScores: PerformanceScores | null;
  comparisonStatus: 'UNCHANGED' | 'DIFFERENT' | 'CURRENT_RESULTS_UNAVAILABLE' | 'NOT_APPLICABLE';
  internalDetails?: { recommendation: AnnualRecommendation | null; justification: string | null;
    overrideReason: string | null; reviewedLate: boolean | null; recordedPerformance: AnnualAppraisalSnapshot | null } | null;
}
export interface StaffPerformance {
  employee: { id: string; name: string | null; departmentName: string | null; roleName: string | null };
  period: PerformancePeriod; scores: PerformanceScores; defaultCheckpointId: number | null;
  checkpoints: PerformanceCheckpoint[]; attitude: PerformanceAttitude;
  approvedAppraisal: PerformanceApprovedAppraisal | null; appraisalHistory: PerformanceApprovedAppraisal[];
  trend: { period: PerformancePeriod; scores: PerformanceScores; sourceType: 'LATEST_COMPLETED_RESULTS' }[];
  capabilities: { selfView: boolean; canViewInternalAppraisal: boolean };
}
