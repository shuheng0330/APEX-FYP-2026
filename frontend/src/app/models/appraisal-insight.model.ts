export interface InsightScenario { id: string; title: string; }
export interface InsightScores { kpi: number | null; attitude: number | null; finalAppraisal: number | null; }
export interface InsightStatement { text: string; evidenceIds: string[]; }
export interface InsightEvidence {
  periodName: string; startDate: string; endDate: string; requiredResultsComplete: boolean;
  frequency: string; consolidationMethod: string; kpiWeight: number; attitudeWeight: number;
  scores: InsightScores;
  facts: { id: string; scope: string; text: string }[];
  history: { id: string; periodName: string; startDate: string; endDate: string; scores: InsightScores; comparable: boolean; comparisonNote: string | null }[];
  informationGaps: string[];
}
export interface InsightSummary {
  performanceSummary: InsightStatement[]; keyStrengths: InsightStatement[];
  areasForImprovement: InsightStatement[]; performanceTrend: InsightStatement[];
  appraisalConsiderations: { promotionDiscussion: InsightStatement; salaryIncrementDiscussion: InsightStatement };
  informationGaps: InsightStatement[];
}
export interface AppraisalInsight {
  id: string; scenario: string; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'; synthetic: boolean;
  templateVersion: string; model: string; createdAt: string; completedAt: string | null;
  errorCode: string | null; evidence: InsightEvidence; summary: InsightSummary | null; disclaimer: string;
}

export interface EmployeeInsightLimits {
  serverTime: string; dailyAllowance: number; remainingAttempts: number;
  resetAt: string; cooldownUntil: string | null; providerRetryAt: string | null;
}
export interface EmployeeInsight {
  id: string; version: number | null; status: AppraisalInsight['status']; outdated: boolean; fictional: boolean;
  model: string; templateVersion: string; createdAt: string; completedAt: string | null;
  errorCode: string | null; retryAt: string | null; evidence: InsightEvidence; summary: InsightSummary | null; disclaimer: string;
}
export interface EmployeeInsightState {
  participantId: number; eligible: boolean; unavailableReason: string | null; currentInsight: EmployeeInsight | null;
  latestAttempt: EmployeeInsight | null; history: EmployeeInsight[]; limits: EmployeeInsightLimits;
}
export interface EmployeeInsightResult {
  disposition: 'REUSED' | 'NEWLY_QUEUED' | 'ALREADY_IN_PROGRESS' | null;
  generation: EmployeeInsight; limits: EmployeeInsightLimits;
}
export interface SubmittedEmployeeInsight {
  appraisalId: number; submissionActivityId: number | null; insight: EmployeeInsight | null;
}
