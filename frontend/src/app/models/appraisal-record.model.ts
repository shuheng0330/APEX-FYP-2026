export type AppraisalDecisionType = 'PROMOTION' | 'SALARY_INCREMENT' | 'BOTH';
export type AppraisalDecisionSelection = AppraisalDecisionType | 'NONE';
export type AppraisalCategory = 'READY' | 'BORDERLINE' | 'NEEDS_IMPROVEMENT';
export type AppraisalStatus = 'DRAFT' | 'PENDING_REVIEW' | 'RETURNED' | 'APPROVED';

export interface AppraisalReadinessDto {
  readinessScore: number;
  systemCategory: AppraisalCategory;
}

export interface HrAppraisalActionDto {
  promotionHrOverrideCategory?: AppraisalCategory | null;
  promotionHrOverrideReason?: string | null;
  salaryHrOverrideCategory?: AppraisalCategory | null;
  salaryHrOverrideReason?: string | null;
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
  promotionManagerCategory?: AppraisalCategory | null;
  salaryManagerCategory?: AppraisalCategory | null;
  promotionManagerOverrideReason?: string | null;
  salaryManagerOverrideReason?: string | null;
  managerComment?: string | null;
  aiInsight?: string | null;
  status?: AppraisalStatus;
  hrReviewerId?: string | null;
  hrReviewerName?: string | null;
  promotionHrOverrideCategory?: AppraisalCategory | null;
  promotionHrOverrideReason?: string | null;
  salaryHrOverrideCategory?: AppraisalCategory | null;
  salaryHrOverrideReason?: string | null;
  promotionEffectiveCategory?: AppraisalCategory | null;
  salaryEffectiveCategory?: AppraisalCategory | null;
  hrReturnReason?: string | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}
