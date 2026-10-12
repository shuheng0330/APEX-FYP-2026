import { revisionNextStep, revisionSteps } from './kpi-revision-progress';
import { revisionFixture } from './kpi-revision.test-data';

describe('Revision presentation progress', () => {
  it('keeps Company confirmation as the final step with no additional approver', () => {
    expect(revisionSteps(revisionFixture()).map(s => s.state)).toEqual(['current', 'future']);
    expect(revisionSteps(revisionFixture({ status: 'EFFECTIVE' })).every(s => s.state === 'complete')).toBeTrue();
  });
  it('shows Department MD approval followed by HOD confirmation', () => {
    const revision = revisionFixture({ level: 'DEPARTMENT', status: 'PENDING_MD_APPROVAL' });
    expect(revisionSteps(revision).map(s => s.state)).toEqual(['complete', 'complete', 'current', 'future']);
    expect(revisionNextStep(revision)).toBe('KPI_REVISIONS.NEXT.MD');
    revision.status = 'PENDING_HOD_CONFIRMATION'; expect(revisionSteps(revision)[3].state).toBe('current');
  });
  it('does not show invalidated approvals as completed after a return', () => {
    const revision = revisionFixture({ level: 'DEPARTMENT', status: 'RETURNED', approvedBy: 'md', approvedAt: '2026-10-01' });
    expect(revisionSteps(revision).map(s => s.state)).toEqual(['complete', 'current', 'future', 'future']);
  });
  it('supports both Individual preparation paths with exactly one approval', () => {
    for (const preparationMode of ['SUPERIOR', 'HR_MD'] as const) {
      const revision = revisionFixture({ level: 'INDIVIDUAL', preparationMode, status: 'DRAFT' });
      expect(revisionSteps(revision).length).toBe(3); expect(revisionSteps(revision)[2].label).toBe('KPI_REVISIONS.STEP.SUPERIOR_APPROVAL');
      expect(revisionNextStep(revision)).toBe('KPI_REVISIONS.NEXT.' + (preparationMode === 'SUPERIOR' ? 'SUPERIOR_PREPARES' : 'HR_MD'));
    }
  });
  it('shows cancelled stages as stopped and replaced versions as previously completed', () => {
    expect(revisionSteps(revisionFixture({ level: 'DEPARTMENT', status: 'CANCELLED', cancelledFromState: 'PENDING_MD_APPROVAL' }))
      .map(s => s.state)).toEqual(['complete', 'complete', 'stopped', 'future']);
    expect(revisionSteps(revisionFixture({ status: 'SUPERSEDED' })).every(s => s.state === 'complete')).toBeTrue();
  });
});
