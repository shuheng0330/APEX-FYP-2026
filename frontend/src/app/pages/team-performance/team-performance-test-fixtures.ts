import { TeamPerformance, TeamPeriod, TeamScope } from '../../models/team-performance.model';
export const teamScope: TeamScope = { type: 'TEAM', departmentId: null, name: 'My Direct Reports' };
export const departmentScope: TeamScope = { type: 'DEPARTMENT', departmentId: 8, name: 'IT' };
export function teamPeriod(id = 1): TeamPeriod {
  return { id, name: `Annual Review ${2025 + id}`, startDate: `${2025 + id}-01-01`, endDate: `${2025 + id}-12-31`,
    status: 'OPEN', frequencyPolicy: 'DEPARTMENT_BASED', assessmentModel: 'CENTRAL_RESULTS', consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50 };
}
export function teamDashboard(scope = teamScope, period = teamPeriod()): TeamPerformance {
  const summary = { totalEmployees: 2, employeesWithResults: 1, kpi: { score: 0, contributingEmployees: 1 },
    attitude: { score: 80, contributingEmployees: 2 }, finalScore: { score: 40, contributingEmployees: 1 } };
  return { scope, period, summary, trend: [{ period, summary }],
    yearOnYear: { percentagePoints: null, status: 'NO_PREVIOUS_PERIOD', previousPeriodId: null },
    attention: { yourActions: { pendingKpiReviews: 1, pendingAttitudeReviews: 0, pendingIndividualPlanApprovals: 0, overdueReviews: 1, returnedIndividualRevisions: 0,
      links: [{ task: 'KPI_REVIEWS', path: '/team-performance/team-reviews', parameters: { tab: 'assessments', reviewPeriodId: String(period.id) } }] },
      scopeProgress: { kpiSelfAssessments: 3, kpiSuperiorReviews: 4, attitudeSelfAssessments: 1, attitudeSuperiorReviews: 2, returnedIndividualPlans: 1 } },
    distribution: { metric: 'FINAL', employeesWithResults: 1, missingResults: 1, bins: [
      { lowerInclusive: 0, upper: 60, upperInclusive: false, employees: 1 }, { lowerInclusive: 60, upper: 70, upperInclusive: false, employees: 0 },
      { lowerInclusive: 70, upper: 80, upperInclusive: false, employees: 0 }, { lowerInclusive: 80, upper: 90, upperInclusive: false, employees: 0 },
      { lowerInclusive: 90, upper: 100, upperInclusive: true, employees: 0 }] },
    employees: [
      { employeeId: '11111111-1111-4111-8111-111111111111', participantId: 11, name: 'Bob', roleName: 'Engineer', departmentName: 'IT',
        scores: { annualKpiScore: 0, attitudeScore: 80, finalScore: 40, consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50 },
        yearOnYear: { percentagePoints: 0, status: 'AVAILABLE', previousPeriodId: 9 } },
      { employeeId: '22222222-2222-4222-8222-222222222222', participantId: 12, name: 'Carol', roleName: 'Analyst', departmentName: 'IT',
        scores: { annualKpiScore: null, attitudeScore: 80, finalScore: null, consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50 },
        yearOnYear: { percentagePoints: null, status: 'INCOMPATIBLE_CONFIGURATION', previousPeriodId: 9 } }
    ] };
}
