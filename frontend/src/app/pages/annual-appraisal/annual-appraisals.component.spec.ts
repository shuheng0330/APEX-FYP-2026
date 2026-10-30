import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { DownOutline, LeftOutline, RightOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject } from 'rxjs';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { AnnualAppraisalCandidate } from '../../models/annual-appraisal.model';
import { AnnualAppraisalsComponent } from './annual-appraisals.component';
import { AnnualAppraisalNavigationState } from './annual-appraisal-navigation-state.service';
import { appraisal, candidate, period, clearAppraisalNavigationState } from './annual-appraisal-test-fixtures';

describe('Annual appraisal queues', () => {
  let fixture: ComponentFixture<AnnualAppraisalsComponent>;
  let component: AnnualAppraisalsComponent;
  let api: jasmine.SpyObj<AnnualAppraisalService>;
  let router: Router;
  let state: AnnualAppraisalNavigationState;
  async function setup(hr = false, grants = [hr ? 'CAN_REVIEW_ANNUAL_APPRAISAL' : 'CAN_SUBMIT_ANNUAL_APPRAISAL']) {
    api = jasmine.createSpyObj('AnnualAppraisalService', ['periods', 'team', 'reviews']);
    api.periods.and.returnValue(of([period])); api.team.and.returnValue(of([candidate()])); api.reviews.and.returnValue(of([]));
    await TestBed.configureTestingModule({ imports: [AnnualAppraisalsComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), provideNoopAnimations(), { provide: ActivatedRoute, useValue: { snapshot: { data: { hrReview: hr } } } },
      { provide: AnnualAppraisalService, useValue: api },
      { provide: AuthService, useValue: { userId: hr ? 'david' : 'alice', hasRole: (p: string) => grants.includes(p) } },
      { provide: NZ_ICONS, useValue: [DownOutline, LeftOutline, RightOutline, LoadingOutline] }
    ] }).compileComponents();
    router = TestBed.inject(Router); spyOn(router, 'navigate').and.resolveTo(true);
    state = TestBed.inject(AnnualAppraisalNavigationState);
    fixture = TestBed.createComponent(AnnualAppraisalsComponent); component = fixture.componentInstance; fixture.detectChanges();
  }
  beforeEach(clearAppraisalNavigationState);
  afterEach(() => { fixture?.destroy(); clearAppraisalNavigationState(); });
  it('loads only the permitted queue and does not infer business authority from titles', async () => {
    await setup(false, ['Super Admin', 'ROLE_USER']); expect(api.periods).not.toHaveBeenCalled();
    expect(component.authorised).toBeFalse();
  });
  it('uses only the HR queue API for HR review', async () => {
    await setup(true); expect(api.reviews).toHaveBeenCalledOnceWith(2); expect(api.team).not.toHaveBeenCalled();
    expect(component.statuses).toEqual(['PENDING_REVIEW', 'RETURNED', 'APPROVED']);
  });
  it('places pending performance results before ready appraisals without changing the default filter', async () => {
    await setup();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { ANNUAL_APPRAISAL: { STATUS: { NOT_READY: 'Performance Results Pending', READY: 'Ready for Appraisal' } } });
    translate.use('en'); fixture.detectChanges();
    expect(component.statuses.slice(0, 2)).toEqual(['NOT_READY', 'READY']);
    expect(component.filter).toBe('READY');
    const buttons: HTMLButtonElement[] = [...fixture.nativeElement.querySelectorAll('.review-status-tab')];
    expect(buttons[0].textContent).toContain('Performance Results Pending');
    expect(buttons[1].textContent).toContain('Ready for Appraisal');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    buttons[0].click(); fixture.detectChanges(); expect(component.filter).toBe('NOT_READY');
  });
  it('keeps search, Department filtering, counts and pagination client-side', async () => {
    await setup(); component.candidates = Array.from({ length: 12 }, (_, i) => candidate({ participantId: i + 1, employeeName: 'Employee ' + i }));
    component.filter = 'DRAFT'; expect(component.visibleRows.length).toBe(10); component.pageIndex = 2; expect(component.visibleRows.length).toBe(2);
    component.search = 'Executive'; component.department = 'IT'; expect(component.count('DRAFT')).toBe(12);
    component.search = 'missing'; expect(component.filteredRows.length).toBe(0); expect(api.team).toHaveBeenCalledTimes(1);
    expect(component.queueScore(candidate({ status: 'PENDING_REVIEW' }))).toBeNull();
  });
  it('persists queue context and opens a Superior detail URL without loading or creating a Draft', async () => {
    await setup(); component.filter = 'DRAFT'; component.search = 'Bob'; component.department = 'IT'; component.pageSize = 20;
    await component.open(candidate());
    expect(router.navigate).toHaveBeenCalledOnceWith(['/team-performance/team-appraisals', 2, 7]);
    expect(state.read('alice', false)).toEqual(jasmine.objectContaining({ periodId: 2, filter: 'DRAFT', search: 'Bob', department: 'IT', pageSize: 20 }));
    expect(component.navigating).toBeFalse();
  });
  it('opens HR review by appraisal ID', async () => {
    await setup(true); await component.open({ ...appraisal(), appraisalId: 10 });
    expect(router.navigate).toHaveBeenCalledOnceWith(['/appraisals/review', 10]);
  });
  it('restores a valid scoped queue state, refreshes server data and clamps pagination', async () => {
    await setup(); state.save('alice', false, { periodId: 2, filter: 'DRAFT', search: 'Executive', department: 'IT', pageIndex: 5, pageSize: 10, scrollTop: 0 });
    api.team.and.returnValue(of(Array.from({ length: 12 }, (_, i) => candidate({ participantId: i + 1 }))));
    component.loadPeriods();
    expect(component.filter).toBe('DRAFT'); expect(component.search).toBe('Executive'); expect(component.pageIndex).toBe(2);
    expect(component.visibleRows.length).toBe(2); expect(api.team).toHaveBeenCalledTimes(2);
  });
  it('preserves existing remembered period defaults and clears filters only on period change', async () => {
    await setup(); api.periods.and.returnValue(of([period, { ...period, id: 3, status: 'CLOSED' }]));
    sessionStorage.setItem('annual-appraisal-period:team:alice', '3'); component.periodId = null; component.loadPeriods();
    expect<number | null>(component.periodId).toBe(3); component.search = 'Bob'; component.changePeriod(2);
    expect<number | null>(component.periodId).toBe(2); expect(component.search).toBe(''); expect(api.team).toHaveBeenCalledWith(2);
  });
  it('restores the application scroll container after the queue has reloaded', async () => {
    await setup();
    const scroll = document.createElement('div'); scroll.className = 'main-content';
    scroll.style.cssText = 'height:100px;width:200px;overflow:auto';
    const content = document.createElement('div'); content.style.height = '1000px'; scroll.appendChild(content); document.body.appendChild(scroll);
    try {
      state.save('alice', false, { periodId: 2, filter: 'DRAFT', search: '', department: null, pageIndex: 1, pageSize: 10, scrollTop: 240 });
      component.loadPeriods(); await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
      expect(scroll.scrollTop).toBe(240);
    } finally { scroll.remove(); }
  });
  it('blocks queue actions during data loading', async () => {
    await setup(); const pending = new Subject<AnnualAppraisalCandidate[]>(); api.team.and.returnValue(pending);
    component.loadQueue(); await component.open(candidate()); component.changePeriod(3);
    expect(component.canLeave()).toBeFalse(); expect(router.navigate).not.toHaveBeenCalled();
    pending.next([]); pending.complete(); expect(component.canLeave()).toBeTrue();
  });
  it('whitelists queue state and isolates users and workspaces', async () => {
    await setup(); state.save('alice', false, { periodId: 2, filter: 'READY', search: '', department: null, pageIndex: 1, pageSize: 10, scrollTop: 42 });
    expect(state.read('alice', false)?.scrollTop).toBe(42); expect(state.read('david', false)).toBeNull(); expect(state.read('alice', true)).toBeNull();
    sessionStorage.setItem('annual-appraisal-queue:team:alice', '{"periodId":2,"justification":"never restore this"}');
    expect(state.read('alice', false)).toBeNull();
  });
  it('wraps filters and status buttons at desktop, tablet and narrow widths', async () => {
    await setup();
    const frame = document.createElement('iframe'); frame.title = 'Responsive appraisal queue'; frame.style.cssText = 'position:absolute;left:-10000px;height:1000px;border:0';
    document.body.appendChild(frame);
    try {
      const doc = frame.contentDocument!;
      for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
      doc.body.style.margin = '0'; doc.body.appendChild(fixture.nativeElement.cloneNode(true));
      for (const [width, columns] of [[1440, 4], [768, 2], [360, 1]]) {
        frame.style.width = width + 'px';
        const filters = doc.querySelector<HTMLElement>('.appraisal-filters')!;
        expect(frame.contentWindow!.getComputedStyle(filters).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(columns);
        const tabs = doc.querySelector<HTMLElement>('.appraisal-status-tabs')!;
        expect(tabs.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(tabs.clientWidth + 1);
      }
    } finally { frame.remove(); }
  });
});
