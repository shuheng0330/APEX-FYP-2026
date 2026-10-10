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
