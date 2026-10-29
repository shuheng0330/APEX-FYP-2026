import { PerformanceApprovedAppraisal, PerformanceCheckpointDetails, PerformancePeriod, StaffPerformance } from '../../models/staff-performance.model';
import { AnnualAppraisalSnapshot } from '../../models/annual-appraisal.model';

export const performanceEmployee = '11111111-1111-1111-1111-111111111111';
export function staffPeriod(id = 1): PerformancePeriod {
  return { id, participantId: id + 10, name: '[UAT SIMULATED] ' + (2025 + id) + ' Annual Review',
    startDate: `${2025 + id}-01-01`, endDate: `${2025 + id}-12-31`, status: 'OPEN', reviewFrequency: 'QUARTERLY',
    frequencyPolicy: 'DEPARTMENT_BASED', assessmentModel: 'CENTRAL_RESULTS' };
}
export function staffAppraisal(): PerformanceApprovedAppraisal {
  return { id: 31, period: staffPeriod(), status: 'APPROVED', finalDecision: 'SALARY_INCREMENT',
    approvedAt: '2026-10-29T09:00:00+08:00', recordedScores: { annualKpiScore: 70, attitudeScore: 80, finalScore: 75,
      kpiWeight: 50, attitudeWeight: 50, consolidationMethod: 'AVERAGE' }, comparisonStatus: 'DIFFERENT' };
}
export function staffDashboard(period = staffPeriod()): StaffPerformance {
  return { employee: { id: performanceEmployee, name: 'Bob Smith', departmentName: 'IT', roleName: 'Engineer' }, period,
    scores: { annualKpiScore: 0, attitudeScore: null, finalScore: null, kpiWeight: 50, attitudeWeight: 50, consolidationMethod: 'AVERAGE' },
    defaultCheckpointId: 101, checkpoints: [{ id: 101, sequenceNumber: 1, startDate: period.startDate, endDate: period.endDate,
      assessmentId: 201, status: 'REVIEWED', score: 0, submittedAt: null, reviewedAt: null }],
    attitude: { assessmentId: 21, configurationId: 20, configurationName: 'Annual Values', format: 'OTHERS', status: 'PENDING_REVIEW',
      score: null, submittedAt: null, reviewedAt: null, ratingDefinitions: [], criteria: [{ id: 22, name: 'Integrity',
        description: 'Work honestly', criterionType: 'SHARED_CORE_VALUE', selfPoint: 4, selfComment: 'Self reflection', superiorPoint: null, superiorComment: null }] },
    approvedAppraisal: null, appraisalHistory: [], trend: [{ period, scores: { annualKpiScore: 0, attitudeScore: null, finalScore: null,
      kpiWeight: 50, attitudeWeight: 50, consolidationMethod: 'AVERAGE' }, sourceType: 'LATEST_COMPLETED_RESULTS' }],
    capabilities: { selfView: true, canViewInternalAppraisal: false } };
}
export function staffCheckpoint(period = staffPeriod()): PerformanceCheckpointDetails {
  return { period, checkpoint: staffDashboard(period).checkpoints[0], assessmentModel: 'CENTRAL_RESULTS',
    breakdown: { assessmentId: 201, checkpointScore: 0, levels: [{ level: 'INDIVIDUAL', allocation: 70,
      versionNumber: 1, resultEditionNumber: null, publishedAt: null, status: 'AVAILABLE', contribution: 56,
      items: [{ kpi: { id: 500, name: 'Support Resolution', description: null, perspective: null, kra: null, target: 'Resolve 90% within SLA',
        measurementUnit: null, weightage: 100, scoringDefinitions: { 4: 'Meets expectation' } }, actualAchievement: null, point: 4 }] }] },
    answers: [{ id: 501, level: 'INDIVIDUAL', kpi: { id: 500, name: 'Support Resolution', description: null, perspective: null, kra: null,
      target: 'Resolve 90% within SLA', measurementUnit: null, weightage: 100, scoringDefinitions: { 4: 'Meets expectation' } },
      selfPoint: 3, selfComment: 'Submitted self comment', superiorPoint: 4, superiorComment: 'Submitted feedback' }] };
}

export function staffRecordedAppraisal(period = staffPeriod()): PerformanceApprovedAppraisal {
  const individual = staffCheckpoint(period).breakdown!.levels[0];
  const year = period.endDate.slice(0, 4);
  const levels = [
    { ...individual, level: 'COMPANY' as const, allocation: 15, contribution: 6, versionNumber: 3, resultEditionNumber: 2,
      publishedAt: `${year}-07-01T10:00:00Z`, reportingCheckpoint: { id: 91, reviewFrequency: 'MONTHLY' as const, startDate: `${year}-06-01`, endDate: `${year}-06-30` },
      items: [{ kpi: { ...individual.items[0].kpi, id: 510, name: 'Recorded Revenue' }, actualAchievement: 'Recorded Company achievement', point: 2 }] },
    { ...individual, level: 'DEPARTMENT' as const, allocation: 15, contribution: 9, versionNumber: 4, resultEditionNumber: 1,
      items: [{ kpi: { ...individual.items[0].kpi, id: 511, name: 'Recorded Department Service' }, actualAchievement: 'Recorded Department achievement', point: 3 }] },
    { ...individual, versionNumber: 5 }
  ];
  const recordedPerformance: AnnualAppraisalSnapshot = { participantId: period.participantId, reviewPeriodId: period.id, employeeId: performanceEmployee,
    reviewFrequency: period.reviewFrequency, consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50,
    annualKpiScore: 71, attitudeScore: 80, finalScore: 75.5,
    checkpoints: [{ checkpointId: 301, sequenceNumber: 1, startDate: period.startDate, endDate: period.endDate,
      assessmentId: 201, status: 'REVIEWED', score: 71, reviewedAt: null,
      versions: [{ level: 'COMPANY', versionId: 903, versionNumber: 3 }, { level: 'DEPARTMENT', versionId: 904, versionNumber: 4 },
        { level: 'INDIVIDUAL', versionId: 905, versionNumber: 5 }], breakdown: { assessmentId: 201, checkpointScore: 71, levels } }],
    attitude: { assessmentId: 21, configurationId: 20, format: 'OTHERS', status: 'REVIEWED', reviewedAt: null,
      criteria: [{ criterionId: 22, name: 'Recorded Integrity', description: 'Recorded criterion', superiorPoint: 4 }] } };
  return { ...staffAppraisal(), period, finalDecision: 'PROMOTION', recordedScores: { annualKpiScore: 71, attitudeScore: 80, finalScore: 75.5,
    kpiWeight: 50, attitudeWeight: 50, consolidationMethod: 'FINAL_CHECKPOINT' },
    internalDetails: { recommendation: 'SALARY_INCREMENT', justification: 'Recorded recommendation justification', overrideReason: 'Recorded HR override',
      reviewedLate: false, recordedPerformance } };
}
