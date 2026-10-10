import { AttitudeFormat, AttitudeRating } from './attitude-configuration.model';
import { KpiPeriodContext } from './kpi-plan.model';

export interface AttitudeAssessmentAnswer {
  criterionId: number;
  selfPoint: number | null;
  selfComment: string | null;
}

export interface AttitudeAssessmentItem extends AttitudeAssessmentAnswer {
  id: number | null;
  name: string;
  description: string;
  criterionType: 'SHARED_CORE_VALUE' | 'FORMAT_SPECIFIC';
  displayOrder: number;
  superiorPoint?: number | null;
  superiorComment?: string | null;
}

export interface AttitudeAssessmentRequest {
  reviewPeriodId: number;
  items: AttitudeAssessmentAnswer[];
}

export interface AttitudeAssessment {
  id: number | null;
  participantId: number;
  employeeName: string;
  roleName: string | null;
  departmentName: string | null;
  reviewPeriodId: number;
  reviewPeriodName: string;
  reviewPeriodStatus: KpiPeriodContext['status'];
  configurationId: number | null;
  configurationName: string | null;
  evaluationFormat: AttitudeFormat | null;
  status: 'DRAFT' | 'PENDING_REVIEW' | 'REVIEWED' | null;
  createdAt: string | null;
  updatedAt: string | null;
  submittedAt: string | null;
  submittedBy: string | null;
  submittedToSuperiorId: string | null;
  submittedLate: boolean | null;
  reviewedAt?: string | null;
  reviewedBy?: string | null;
  reviewedLate?: boolean | null;
  attitudeScore?: number | null;
  superiorDraftSaved?: boolean;
  canSaveSuperiorDraft?: boolean;
  canCompleteReview?: boolean;
  superiorOverdue?: boolean;
  reviewBlockers?: string[];
  selfAssessmentDeadline: string | null;
  superiorEvaluationDeadline: string | null;
  available: boolean;
  availabilityTitle: string | null;
  availabilityMessage: string | null;
  canSaveDraft: boolean;
  canSubmit: boolean;
  overdue: boolean;
  submissionBlockers: string[];
  ratingDefinitions: AttitudeRating[];
  items: AttitudeAssessmentItem[];
}

export interface AttitudeAssessmentReview {
  id: number;
  employeeId: string;
  employeeName: string;
  roleName: string | null;
  departmentName: string | null;
  reviewPeriodId: number;
  reviewPeriodName: string;
  reviewPeriodStatus: KpiPeriodContext['status'];
  evaluationFormat: AttitudeFormat;
  status: 'PENDING_REVIEW' | 'REVIEWED';
  superiorEvaluationDeadline: string | null;
  submittedAt: string | null;
  submittedLate: boolean | null;
  reviewedAt: string | null;
  reviewedLate: boolean | null;
  attitudeScore: number | null;
  canReview: boolean;
  superiorDraftSaved: boolean;
  superiorOverdue: boolean;
}

export interface SuperiorAttitudeAssessmentRequest {
  items: { itemId: number; superiorPoint: number | null; superiorComment: string | null }[];
}
