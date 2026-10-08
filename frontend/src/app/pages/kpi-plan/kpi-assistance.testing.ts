import { KpiAssistance, KpiAssistanceEmployee } from '../../models/kpi-assistance.model';
import { KpiItem, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';

export function assistanceCase(status: KpiAssistance['status'] = 'REQUESTED', id = 8): KpiAssistance {
  return { id, ownerParticipantId: 7, employeeId: 'employee', employeeName: 'Amir', departmentName: 'Retail Sales',
    reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', superiorId: 'superior', superiorName: 'Sales Superior',
    status, requestedAt: '2028-01-01T10:00:00Z', authorizedAt: null, authorizedById: null, authorizedByName: null,
    rejectedAt: null, rejectedById: null, rejectedByName: null, rejectionReason: null, consumedAt: null, planId: null };
}
export const assistanceEmployee: KpiAssistanceEmployee = { ownerParticipantId: 7, employeeId: 'employee', employeeName: 'Amir',
  departmentName: 'Retail Sales', reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review' };
export function assistedItem(name = 'Customers', weightage = 100): KpiItem {
  return { ...emptyKpiItem(), name, perspective: 'Customer', kra: 'Customer growth', target: '10 customers', weightage,
    scoringDefinitions: { 1: 'Very low', 2: 'Low', 3: 'On target', 4: 'Above target', 5: 'Excellent' } };
}
export function assistedPlan(status: KpiPlan['status'] = 'DRAFT'): KpiPlan {
  return { id: 10, reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', ownerParticipantId: 7,
    employeeName: 'Amir', departmentName: 'Retail Sales', level: 'INDIVIDUAL', status, assistanceAuthorizationId: 8,
    items: [assistedItem()], totalWeightage: 100, kpiSetupDeadline: '2028-01-31', overdue: false, createdAt: '2028-01-01', updatedAt: '2028-01-01' };
}
