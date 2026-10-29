import { AnnualRecommendation } from './annual-appraisal.model';
import { TeamChange, TeamPerformance, TeamPeriod, TeamSummary, TeamTrendPoint } from './team-performance.model';

export interface OrganisationDepartment {
  departmentId: number | null;
  name: string;
  summary: TeamSummary;
  assessmentProgress: TeamPerformance['attention']['scopeProgress'];
  pendingHrReview: number;
  yearOnYear: TeamChange;
}
export interface DecisionSeries {
  disclosure: 'AVAILABLE' | 'NO_DATA' | 'SUPPRESSED';
  total: number | null;
  counts: { decision: AnnualRecommendation; count: number }[];
}
export interface OrganisationPerformance {
  period: TeamPeriod;
  historyYears: 3 | 5;
  summary: TeamSummary;
  yearOnYear: TeamChange;
  trend: TeamTrendPoint[];
  distribution: TeamPerformance['distribution'];
  departments: OrganisationDepartment[];
  departmentTrends: { departmentId: number | null; name: string; periods: TeamTrendPoint[] }[];
  appraisalDistribution: {
    minimumGroupSize: number;
    superiorRecommendations: DecisionSeries;
    hrFinalDecisions: DecisionSeries;
  };
}
