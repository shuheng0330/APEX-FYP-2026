import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { KpiRevision } from '../../models/kpi-revision.model';
import { KpiRevisionActivityComponent } from './kpi-revision-activity.component';
import { revisionFixture } from './kpi-revision.test-data';

describe('Compact revision activity timeline', () => {
  let fixture: ComponentFixture<KpiRevisionActivityComponent>;
  const event = (action: string, at: string, actor = 'md', reason: string | null = null): KpiRevision['decisionHistory'][number] =>
    ({ action, at, actor, reason, from: 'DRAFT', to: action === 'CONFIRMED' || action === 'SUPERIOR_APPROVED' ? 'EFFECTIVE' : 'DRAFT' });
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [KpiRevisionActivityComponent, TranslateModule.forRoot()] }).compileComponents();
    fixture = TestBed.createComponent(KpiRevisionActivityComponent);
  });
  function render(overrides: Partial<KpiRevision> = {}) {
    fixture.componentRef.setInput('revision', revisionFixture(overrides)); fixture.detectChanges();
    return fixture.componentInstance;
  }
  it('orders recorded events oldest first without changing the response or losing repeated decisions', () => {
    const history = [event('MD_APPROVED', '2026-10-12T12:00:00Z'),
      event('RETURNED', '2026-10-11T12:00:00Z', 'hod', 'First return'),
      event('RETURNED', '2026-10-12T10:00:00Z', 'hod', 'Second return'),
      event('MD_APPROVED', '2026-10-11T10:00:00Z')];
    const original = structuredClone(history);
    const page = render({ decisionHistory: history });
    expect(page.events.map(e => e.action)).toEqual(['MD_APPROVED', 'RETURNED', 'RETURNED', 'MD_APPROVED']);
    expect(history).toEqual(original);
    const rows = fixture.nativeElement.querySelectorAll('.activity-timeline li');
    expect(rows.length).toBe(4);
    expect(rows[1].textContent).toContain('Department Head'); expect(rows[1].textContent).toContain('First return');
    expect(rows[2].textContent).toContain('Second return');
    expect(rows[0].querySelector('time').getAttribute('datetime')).toBe('2026-10-11T10:00:00Z');
  });
  it('keeps source order for events with equal timestamps and renders reasons as text', () => {
    const page = render({ decisionHistory: [event('RETURNED', '2026-10-11T12:00:00Z', 'hod', '<b>Clarify target</b>'),
      event('MD_APPROVED', '2026-10-11T12:00:00Z')] });
    expect(page.events.map(e => e.action)).toEqual(['RETURNED', 'MD_APPROVED']);
    expect(fixture.nativeElement.querySelector('.event-reason').textContent).toBe('<b>Clarify target</b>');
    expect(fixture.nativeElement.querySelector('.event-reason b')).toBeNull();
  });
  it('does not repeat actor/date facts already recorded in events', () => {
    const page = render({ createdAt: '2026-10-11T10:00:00.001Z', preparedAt: '2026-10-11T10:00:00.002Z',
      approvedBy: 'md', approvedAt: '2026-10-11T12:00:00.001Z', confirmedBy: 'md', confirmedAt: '2026-10-11T12:00:00.001Z',
      effectiveAt: '2026-10-11T12:00:00.001Z', updatedAt: '2026-10-11T12:00:00.001Z',
      decisionHistory: [event('CREATED', '2026-10-11T10:00:00.003Z'), event('SUPERIOR_APPROVED', '2026-10-11T12:00:00.003Z')] });
    expect(page.additionalDetails).toEqual([]);
    expect(fixture.nativeElement.querySelector('.additional-details')).toBeNull();
  });
  it('preserves missing legacy metadata as facts without inventing any events', () => {
    const page = render({ preparedBy: 'hr', preparedAt: null, approvedBy: 'md', approvedAt: '2026-10-12T12:00:00Z',
      effectiveAt: '2026-10-12T13:00:00Z', revisionSuperiorId: 'hod',
      latestReturnedBy: 'hod', latestReturnedAt: '2026-10-11T12:00:00Z', latestReturnReason: 'Check scoring' });
    expect(page.events).toEqual([]); expect(fixture.nativeElement.querySelector('.activity-timeline')).toBeNull();
    expect(page.additionalDetails.find(d => d.label === 'KPI_REVISIONS.PREPARER')?.at).toBeNull();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('HR Reviewer'); expect(text).toContain('KPI_REVISIONS.APPROVER');
    expect(text).toContain('KPI_REVISIONS.EFFECTIVE_ON'); expect(text).toContain('Check scoring');
    expect(text).not.toContain('KPI_REVISIONS.DECISION.');
  });
  it('does not repeat latest-return feedback already visible in the workspace', () => {
    fixture.componentRef.setInput('returnFeedbackVisible', true);
    const page = render({ latestReturnedBy: 'hod', latestReturnedAt: '2026-10-11T12:00:00Z', latestReturnReason: 'Check scoring' });
    expect(page.additionalDetails.some(d => d.label === 'DEPARTMENT_KPI.RETURN_REASON')).toBeFalse();
  });
  it('preserves a different approval fact even if another approval event exists', () => {
    const page = render({ approvedBy: 'md', approvedAt: '2026-10-12T12:00:00Z',
      decisionHistory: [event('MD_APPROVED', '2026-10-11T12:00:00Z')] });
    expect(page.additionalDetails.some(d => d.label === 'KPI_REVISIONS.APPROVER')).toBeTrue();
  });
  it('shows a missing actor as a dash and refreshes when selecting another version', () => {
    render({ decisionHistory: [event('CONFIRMED', '2026-10-11T12:00:00Z', 'unknown')] });
    expect(fixture.nativeElement.querySelector('.event-actor').textContent).toBe('-');
    render({ id: 5, decisionHistory: [] });
    expect(fixture.nativeElement.querySelector('.activity-timeline')).toBeNull();
  });
  it('shows a neutral empty state when neither events nor additional metadata are supplied', () => {
    const page = render({ requestedBy: null, preparedBy: null, createdBy: '', createdAt: '', updatedBy: '', updatedAt: '' });
    expect(page.events).toEqual([]); expect(page.additionalDetails).toEqual([]);
    expect(fixture.nativeElement.querySelector('.activity-empty').textContent).toContain('KPI_REVISIONS.NO_ACTIVITY');
  });
});
