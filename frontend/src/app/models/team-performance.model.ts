import { PerformancePeriod, PerformanceScores } from './staff-performance.model';

export type TeamMetric = 'KPI' | 'ATTITUDE' | 'FINAL';
export interface TeamScope { type: 'TEAM' | 'DEPARTMENT'; departmentId: number | null; name: string; }
export interface TeamPeriod extends Omit<PerformancePeriod, 'participantId' | 'reviewFrequency'> {
  consolidationMethod: PerformanceScores['consolidationMethod']; kpiWeight: number | null; attitudeWeight: number | null;
}
export interface TeamAverage { score: number | null; contributingEmployees: number; }
export interface TeamSummary { totalEmployees: number; employeesWithResults: number; kpi: TeamAverage; attitude: TeamAverage; finalScore: TeamAverage; }
export interface TeamChange {
  percentagePoints: number | null; previousPeriodId: number | null;
  status: 'AVAILABLE' | 'NO_PREVIOUS_PERIOD' | 'AMBIGUOUS_PREVIOUS_PERIOD' | 'INCOMPATIBLE_CONFIGURATION' | 'NO_COMPARABLE_RESULTS';
}
export interface TeamActionLink { task: string; path: string; parameters: Record<string, string>; }
export interface TeamEmployee {
  employeeId: string; participantId: number; name: string | null; departmentName: string | null; roleName: string | null;
  scores: PerformanceScores; yearOnYear: TeamChange;
}
export interface TeamTrendPoint { period: TeamPeriod; summary: TeamSummary; }
export interface TeamPerformance {
  scope: TeamScope; period: TeamPeriod; summary: TeamSummary; trend: TeamTrendPoint[]; employees: TeamEmployee[]; yearOnYear: TeamChange;
  attention: {
    yourActions: { pendingKpiReviews: number; pendingAttitudeReviews: number; pendingIndividualPlanApprovals: number;
      overdueReviews: number; returnedIndividualRevisions: number; links: TeamActionLink[]; };
    scopeProgress: { kpiSelfAssessments: number; kpiSuperiorReviews: number; attitudeSelfAssessments: number;
      attitudeSuperiorReviews: number; returnedIndividualPlans: number; };
  };
  distribution: { metric: TeamMetric; bins: { lowerInclusive: number; upper: number; upperInclusive: boolean; employees: number }[];
    employeesWithResults: number; missingResults: number; };
}
