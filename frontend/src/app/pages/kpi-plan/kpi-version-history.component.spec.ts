import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { KpiVersionHistoryComponent } from './kpi-version-history.component';
import { KpiRevisionService } from '../../services/kpi-revision.service';
import { revisionFixture } from './kpi-revision.test-data';

describe('Read-only KPI version history', () => {
  let fixture: ComponentFixture<KpiVersionHistoryComponent>; let page: KpiVersionHistoryComponent; let api: jasmine.SpyObj<KpiRevisionService>;
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiRevisionService>('api', ['history']); api.history.and.returnValue(of([]));
    await TestBed.configureTestingModule({ imports: [KpiVersionHistoryComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: KpiRevisionService, useValue: api }] }).compileComponents();
    fixture = TestBed.createComponent(KpiVersionHistoryComponent); page = fixture.componentInstance; page.planId = 1; fixture.detectChanges();
  });
  it('does not load or mutate data before opening history', () => { expect(api.history).not.toHaveBeenCalled(); page.open(); expect(api.history).toHaveBeenCalledWith(1); });
  it('selects the effective version rather than an unfinished or cancelled proposal', () => {
    const current = revisionFixture({ id: 3, versionNumber: 3, status: 'EFFECTIVE', confirmedBy: 'md', baseVersionId: 1 });
    api.history.and.returnValue(of([revisionFixture({ id: 5, versionNumber: 5, status: 'CANCELLED' }), current,
      revisionFixture({ id: 1, versionNumber: 1, status: 'SUPERSEDED', baseVersionId: null })]));
    page.open(); expect(page.selected?.id).toBe(3); expect(page.baseNumber(current)).toBe(1); expect(page.actorName(current)).toBe('Managing Director');
  });
  it('retains only server-authorised history without requesting hidden versions', () => {
    api.history.and.returnValue(of([revisionFixture({ status: 'EFFECTIVE', allowedActions: [], decisionHistory: [] })]));
    page.open(); expect(page.versions.length).toBe(1); expect(api.history).toHaveBeenCalledTimes(1);
  });
  it('shows only confirmed history in My KPI Plan even for users with additional operator permissions', () => {
    page.effectiveOnly = true;
    api.history.and.returnValue(of([revisionFixture({ id: 2, status: 'DRAFT' }), revisionFixture({ id: 3, status: 'CANCELLED' }),
      revisionFixture({ id: 1, versionNumber: 1, status: 'SUPERSEDED' }), revisionFixture({ id: 4, versionNumber: 4, status: 'EFFECTIVE' })]));
    page.open(); expect(page.versions.map(v => v.id)).toEqual([4, 1]);
    expect(page.selected?.id).toBe(4);
  });
  it('shows a friendly API failure without stale prior history', () => {
    page.selected = revisionFixture(); api.history.and.returnValue(throwError(() => ({ error: { message: 'You cannot view this plan.' } })));
    page.open(); expect(page.selected).toBeNull(); expect(page.versions).toEqual([]); expect(page.error).toBe('You cannot view this plan.');
  });
  it('shows the approved Individual version and approval date without employee access to pending proposals', () => {
    page.effectiveOnly = true;
    api.history.and.returnValue(of([revisionFixture({ level: 'INDIVIDUAL', id: 5, status: 'PENDING_APPROVAL' }),
      revisionFixture({ level: 'INDIVIDUAL', id: 4, versionNumber: 4, status: 'EFFECTIVE', employeeName: 'Bob Smith',
        approvedBy: 'superior', approvedAt: '2026-10-11T12:00:00Z', effectiveAt: '2026-10-11T12:00:00Z', actorNames: { superior: 'Alice Johnson' } })]));
    page.open(); fixture.detectChanges();
    expect(page.versions.map(v => v.id)).toEqual([4]);
    const body = document.querySelector('.ant-modal-body')!;
    expect(body.textContent).toContain('Bob Smith'); expect(body.textContent).toContain('Alice Johnson');
    expect(body.textContent).toContain('KPI_REVISIONS.APPROVER');
  });
});
