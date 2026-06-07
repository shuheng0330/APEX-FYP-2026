export type AppraisalDecisionType = 'PROMOTION' | 'SALARY_INCREMENT' | 'BOTH';
export type AppraisalDecisionSelection = AppraisalDecisionType | 'NONE';
export type AppraisalCategory = 'READY' | 'BORDERLINE' | 'NEEDS_IMPROVEMENT';
export type AppraisalStatus = 'DRAFT' | 'PENDING_REVIEW' | 'RETURNED' | 'APPROVED';

export interface AppraisalReadinessDto {
  readinessScore: number;
  systemCategory: AppraisalCategory;
}

export interface HrAppraisalActionDto {
  hrOverrideCategory?: AppraisalCategory | null;
  hrOverrideReason?: string | null;
  hrReturnReason?: string | null;
}

export interface AppraisalRecordDto {
  id?: string;
  staffId: string;
  staffName?: string;
  departmentName?: string;
  roleName?: string;
  managerId?: string;
  managerName?: string;
  evaluationCycleId: number;
  evaluationCycleEndDate?: string;
  reviewPeriodYears: number;
  decisionType: AppraisalDecisionType;
  promotionReadinessScore?: number | null;
  salaryReadinessScore?: number | null;
  promotionSystemCategory?: AppraisalCategory | null;
  salarySystemCategory?: AppraisalCategory | null;
  promotionFinalCategory?: AppraisalCategory | null;
  salaryFinalCategory?: AppraisalCategory | null;
  promotionOverrideReason?: string | null;
  salaryOverrideReason?: string | null;
  managerComment?: string | null;
  aiInsight?: string | null;
  status?: AppraisalStatus;
  hrReviewerId?: string | null;
  hrReviewerName?: string | null;
  hrOverrideCategory?: AppraisalCategory | null;
  hrOverrideReason?: string | null;
  hrReturnReason?: string | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}
