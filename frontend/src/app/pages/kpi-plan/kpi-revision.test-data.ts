import { KpiItem, KpiPlan, emptyKpiItem } from '../../models/kpi-plan.model';
import { KpiRevision } from '../../models/kpi-revision.model';

export function revisionItem(overrides: Partial<KpiItem> = {}): KpiItem {
  return { ...emptyKpiItem(), id: 20, predecessorId: 10, name: 'Revenue', perspective: 'Financial', kra: 'Growth',
    target: '100 units', weightage: 100, scoringDefinitions: { 1: 'Low', 2: 'Below', 3: 'Partial', 4: 'Meets', 5: 'Exceeds' }, ...overrides };
}
export function revisionFixture(overrides: Partial<KpiRevision> = {}): KpiRevision {
  return { id: 2, planId: 1, reviewPeriodId: 4, reviewPeriodName: 'Annual Review', reviewPeriodStatus: 'OPEN', level: 'COMPANY',
    departmentId: null, departmentName: null, versionNumber: 2, baseVersionId: 1, status: 'DRAFT', revisionReason: 'Market change',
    items: [revisionItem()], totalWeightage: 100, changes: [], targetChangedItemIds: [], blockers: [],
    allowedActions: ['EDIT', 'CONFIRM', 'CANCEL'], lockVersion: 3,
    createdAt: '2026-10-11T10:00:00Z', createdBy: 'md', updatedAt: '2026-10-11T10:00:00Z', updatedBy: 'md',
    requestedBy: 'md', preparedBy: 'md', preparedAt: null, submittedAt: null, approvedBy: null, approvedAt: null,
    confirmedBy: null, confirmedAt: null, effectiveAt: null, cancelledBy: null, cancelledAt: null, cancellationReason: null,
    cancelledFromState: null, latestReturnReason: null, latestReturnedBy: null, latestReturnedAt: null, revisionRequired: false,
    decisionHistory: [], actorNames: { md: 'Managing Director', hr: 'HR Reviewer', hod: 'Department Head' }, ...overrides };
}
export function revisionPlan(overrides: Partial<KpiPlan> = {}): KpiPlan {
  return { id: 1, reviewPeriodId: 4, reviewPeriodName: 'Annual Review', reviewPeriodStatus: 'OPEN', level: 'COMPANY', status: 'PUBLISHED',
    currentVersionId: 1, currentVersionNumber: 1, items: [revisionItem({ id: 10, predecessorId: null })],
    kpiSetupDeadline: null, totalWeightage: 100, overdue: false, createdAt: '', updatedAt: '', ...overrides };
}
