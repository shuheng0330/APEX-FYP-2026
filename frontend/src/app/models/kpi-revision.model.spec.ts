import { targetChanged, unfinishedRevision, validRevisionReason } from './kpi-revision.model';
import { revisionFixture, revisionItem } from '../pages/kpi-plan/kpi-revision.test-data';

describe('KPI revision helpers', () => {
  it('requires a nonblank reason within the existing limit', () => {
    expect(validRevisionReason('   ')).toBeFalse(); expect(validRevisionReason(' Market change ')).toBeTrue();
    expect(validRevisionReason('x'.repeat(10001))).toBeFalse();
  });
  it('ignores target whitespace, detects changes and clears the advisory on reversion', () => {
    const baseline = revisionItem();
    expect(targetChanged(revisionItem({ target: ' 100   units ' }), baseline)).toBeFalse();
    expect(targetChanged(revisionItem({ target: '200 units' }), baseline)).toBeTrue();
    expect(targetChanged(revisionItem(), baseline)).toBeFalse();
    expect(targetChanged(revisionItem(), undefined)).toBeFalse();
  });
  it('does not reuse terminal revisions as working drafts', () => {
    for (const status of ['EFFECTIVE', 'SUPERSEDED', 'CANCELLED'] as const) expect(unfinishedRevision(revisionFixture({ status }))).toBeFalse();
    for (const status of ['DRAFT', 'REQUESTED', 'RETURNED', 'PENDING_MD_APPROVAL', 'PENDING_HOD_CONFIRMATION'] as const) expect(unfinishedRevision(revisionFixture({ status }))).toBeTrue();
  });
});
