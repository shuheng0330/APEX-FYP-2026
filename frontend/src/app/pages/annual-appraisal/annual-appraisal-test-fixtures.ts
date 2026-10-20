import { AnnualAppraisal, AnnualAppraisalCandidate, AnnualAppraisalReadiness, AnnualAppraisalSnapshot } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';

export function clearAppraisalNavigationState() {
  for (const user of ['alice', 'david']) for (const mode of ['team', 'hr']) {
    sessionStorage.removeItem(`annual-appraisal-period:${mode}:${user}`);
    sessionStorage.removeItem(`annual-appraisal-queue:${mode}:${user}`);
  }
}

export function performance(): AnnualAppraisalSnapshot {
  return { participantId: 7, reviewPeriodId: 2, employeeId: 'bob', reviewFrequency: 'MONTHLY',
    consolidationMethod: 'AVERAGE', kpiWeight: 80, attitudeWeight: 20, annualKpiScore: 75,
    attitudeScore: 100, finalScore: 80, checkpoints: [], attitude: null };
}
export function readiness(ready = true): AnnualAppraisalReadiness {
  return { ready, blockers: ready ? [] : ['Waiting for Company results'], performance: performance() };
}
export function appraisal(overrides: Partial<AnnualAppraisal> = {}): AnnualAppraisal {
  return { id: 10, participantId: 7, reviewPeriodId: 2, employeeId: 'bob', superiorId: 'alice', employeeName: 'Bob',
    departmentName: 'IT', roleName: 'Executive', reviewPeriodName: '2026', reviewPeriodStatus: 'OPEN', status: 'DRAFT',
    recommendation: 'SALARY_INCREMENT', finalDecision: null, justification: 'Consistent delivery', overrideReason: null, latestReturnReason: null,
    snapshot: null, createdAt: '2026-10-20T10:00:00Z', updatedAt: '2026-10-20T10:00:00Z', submittedAt: null,
    reviewedAt: null, createdBy: 'alice', updatedBy: 'alice', reviewedBy: null, submittedLate: null, reviewedLate: null,
    lockVersion: 1, recommendationDeadline: '2026-10-31', hrReviewDeadline: '2026-11-10', recommendationOverdue: false,
    hrReviewOverdue: false, sourceChanged: false, readiness: readiness(), submissionBlockers: [], reviewBlockers: [],
    allowedActions: ['SAVE_DRAFT', 'SUBMIT'], activity: [], ...overrides };
}
export const period: KpiPeriodContext = { id: 2, name: '2026', status: 'OPEN', startDate: '2026-01-01', endDate: '2026-12-31', kpiSetupDeadline: null };
export function candidate(overrides: Partial<AnnualAppraisalCandidate> = {}): AnnualAppraisalCandidate {
  return { participantId: 7, employeeId: 'bob', employeeName: 'Bob', departmentName: 'IT', roleName: 'Executive',
    appraisalId: 10, status: 'DRAFT', readiness: readiness(), ...overrides };
}
