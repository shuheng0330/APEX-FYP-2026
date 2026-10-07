import { AnnualReviewPeriod, AnnualReviewPeriodRequest } from '../../models/annual-review-period.model';
import { EmployeeLevel } from '../../models/role.model';

export const reviewLevels: EmployeeLevel[] = ['Top Management', 'Middle Management', 'Junior Management', 'Executive', 'Admin', 'General'].map((name, index) => ({
  id: index + 1, code: `LEVEL_${index + 1}`, name, displayOrder: index + 1,
  defaultCompanyKpiWeight: 15, defaultDepartmentKpiWeight: 25, defaultIndividualKpiWeight: 60
}));

export function validReviewRequest(): AnnualReviewPeriodRequest {
  return {
    name: '2028 Annual KPI Review', startDate: '2028-01-01', endDate: '2028-12-31',
    employeeLevelConfigurations: reviewLevels.map(level => ({ employeeLevelId: level.id, employeeLevelName: level.name,
      companyKpiWeight: 15, departmentKpiWeight: 25, individualKpiWeight: 60 })),
    roleConfigurations: [{ roleId: 7, reviewFrequency: 'MONTHLY' }],
    kpiPerformanceWeight: 50, attitudeEvaluationWeight: 50, annualKpiConsolidationMethod: 'FINAL_CHECKPOINT',
    kpiSetupDeadline: '2027-12-15',
    selfAssessmentDaysAfterCheckpoint: 5, superiorAssessmentDaysAfterSelfDeadline: 5,
    attitudeSelfAssessmentDeadline: '2028-12-20', superiorAttitudeEvaluationDeadline: '2028-12-27',
    appraisalRecommendationDeadline: '2029-01-10', hrFinalisationDeadline: '2029-01-20'
  };
}

export function savedReview(): AnnualReviewPeriod {
  const request = validReviewRequest();
  return { ...request, id: 29, referenceNumber: 'review-reference', status: 'UPCOMING',
    employeeLevelConfigurations: request.employeeLevelConfigurations ?? [],
    roleConfigurations: [{ roleId: 7, roleName: 'Retail Sales Executive', departmentName: 'Sales', reviewFrequency: 'MONTHLY', employeeLevelId: 4, employeeLevelName: 'Executive' }],
    checkpoints: [{ id: 1, reviewFrequency: 'MONTHLY', sequenceNumber: 1, startDate: '2028-01-01', endDate: '2028-01-31', selfAssessmentDeadline: '2028-02-05', superiorAssessmentDeadline: '2028-02-10' }],
    createdAt: null, updatedAt: null };
}
