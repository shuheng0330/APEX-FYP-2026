import { OrganisationPerformance } from '../../models/organisation-performance.model';
import { teamDashboard, teamPeriod } from '../team-performance/team-performance-test-fixtures';

export function organisationDashboard(period = teamPeriod()): OrganisationPerformance {
  const team = teamDashboard(undefined, period);
  return {
    period, historyYears: 5, summary: team.summary, yearOnYear: team.yearOnYear, trend: team.trend, distribution: team.distribution,
    departments: [
      { departmentId: 8, name: 'IT', summary: team.summary, assessmentProgress: team.attention.scopeProgress,
        pendingHrReview: 2, yearOnYear: { status: 'AVAILABLE', percentagePoints: 0, previousPeriodId: 10 } },
      { departmentId: null, name: 'Unassigned Department', summary: { ...team.summary, finalScore: { score: null, contributingEmployees: 0 } },
        assessmentProgress: { ...team.attention.scopeProgress }, pendingHrReview: 0, yearOnYear: team.yearOnYear }
    ],
    departmentTrends: [{ departmentId: 8, name: 'IT', periods: team.trend }, { departmentId: null, name: 'Unassigned Department', periods: team.trend }],
    appraisalDistribution: {
      minimumGroupSize: 5,
      superiorRecommendations: { disclosure: 'AVAILABLE', total: 15, counts: [
        { decision: 'PROMOTION', count: 5 }, { decision: 'SALARY_INCREMENT', count: 10 }, { decision: 'BOTH', count: 0 }, { decision: 'NO_RECOMMENDATION', count: 0 }
      ] },
      hrFinalDecisions: { disclosure: 'SUPPRESSED', total: null, counts: [] }
    }
  };
}
