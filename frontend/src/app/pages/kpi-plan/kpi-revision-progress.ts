import { KpiRevision, KpiVersionStatus } from '../../models/kpi-revision.model';

export interface RevisionStep { label: string; state: 'complete' | 'current' | 'future' | 'stopped'; }

export function revisionSteps(revision: KpiRevision): RevisionStep[] {
  const labels = revision.level === 'COMPANY' ? ['PREPARE_COMPANY', 'APPLY_COMPANY']
    : revision.level === 'DEPARTMENT' ? ['REQUEST', 'HR_PREPARATION', 'MD_APPROVAL', 'HOD_CONFIRMATION']
    : ['REQUEST', revision.preparationMode === 'SUPERIOR' ? 'SUPERIOR_PREPARATION' : 'HR_MD_PREPARATION', 'SUPERIOR_APPROVAL'];
  const terminal = ['EFFECTIVE', 'SUPERSEDED'].includes(revision.status);
  const cancelled = revision.status === 'CANCELLED';
  const status: KpiVersionStatus = cancelled ? revision.cancelledFromState ?? 'REQUESTED' : revision.status;
  const index = revision.level === 'COMPANY' ? 0 : status === 'REQUESTED' ? 0
    : status === 'PENDING_HOD_CONFIRMATION' ? 3
    : ['PENDING_MD_APPROVAL', 'PENDING_APPROVAL'].includes(status) ? 2 : 1;
  return labels.map((label, i) => ({ label: 'KPI_REVISIONS.STEP.' + label,
    state: terminal || i < index ? 'complete' : i === index ? cancelled ? 'stopped' : 'current' : 'future' }));
}

export function revisionNextStep(revision: KpiRevision): string {
  const status = revision.status;
  if (['EFFECTIVE', 'SUPERSEDED', 'CANCELLED'].includes(status)) return 'KPI_REVISIONS.NEXT.' + status;
  if (status === 'PENDING_MD_APPROVAL') return 'KPI_REVISIONS.NEXT.MD';
  if (status === 'PENDING_HOD_CONFIRMATION') return 'KPI_REVISIONS.NEXT.HOD';
  if (status === 'PENDING_APPROVAL') return 'KPI_REVISIONS.NEXT.SUPERIOR_APPROVAL';
  if (revision.level === 'COMPANY') return 'KPI_REVISIONS.NEXT.COMPANY';
  if (revision.level === 'DEPARTMENT') return 'KPI_REVISIONS.NEXT.HR';
  return 'KPI_REVISIONS.NEXT.' + (revision.preparationMode === 'SUPERIOR' ? 'SUPERIOR_PREPARES' : 'HR_MD');
}
