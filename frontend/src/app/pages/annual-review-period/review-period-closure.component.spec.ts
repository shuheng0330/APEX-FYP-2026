import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzPaginationComponent } from 'ng-zorro-antd/pagination';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { LoadingOutline, DownOutline, LeftOutline, RightOutline } from '@ant-design/icons-angular/icons';
import { en_US, provideNzI18n } from 'ng-zorro-antd/i18n';
import { of, Subject, throwError } from 'rxjs';
import { ANNUAL_REVIEW_PERMISSION, AnnualReviewPeriod, ReviewPeriodClosure, ReviewPeriodClosureBlocker } from '../../models/annual-review-period.model';
import { TablePageSizeSelectorComponent } from '../../components/table-page-size-selector/table-page-size-selector.component';
import { AnnualReviewPeriodService } from '../../services/annual-review-period.service';
import { AuthService } from '../../services/auth.service';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';
import { savedReview, closureReadiness } from './review-period.fixtures.spec';
import { ReviewPeriodClosureComponent } from './review-period-closure.component';

describe('Review period closure', () => {
  let fixture: ComponentFixture<ReviewPeriodClosureComponent>;
  let component: ReviewPeriodClosureComponent;
  let api: jasmine.SpyObj<AnnualReviewPeriodService>;
  let modal: jasmine.SpyObj<NzModalService>;
  let permitted: boolean;
  const destroyDialog = jasmine.createSpy('destroy');
  beforeEach(() => {
    permitted = true; destroyDialog.calls.reset();
    api = jasmine.createSpyObj('AnnualReviewPeriodService', ['closureReadiness', 'close']);
    api.closureReadiness.and.returnValue(of(closureReadiness()));
    api.close.and.returnValue(of(closureReadiness({ status: 'CLOSED', canClose: false, closedAt: '2029-01-02T04:05:00Z', closedByName: 'HR Reviewer' })));
    modal = jasmine.createSpyObj('NzModalService', ['confirm']); modal.confirm.and.returnValue({ destroy: destroyDialog } as never);
    TestBed.configureTestingModule({ imports: [ReviewPeriodClosureComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), provideRouter([]), provideNzI18n(en_US), { provide: NZ_ICONS, useValue: [LoadingOutline, DownOutline, LeftOutline, RightOutline] },
      { provide: AnnualReviewPeriodService, useValue: api }, { provide: NzModalService, useValue: modal },
      { provide: AuthService, useValue: { hasRole: (p: string) => permitted && p === ANNUAL_REVIEW_PERMISSION } }
    ] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { COMPONENT: { TABLE_PAGE_SIZE: { ROWS_PER_PAGE: 'Rows per page' } },
      REVIEW_PERIOD: { CLOSURE: { EMPLOYEES: 'Employees with complete results', KPI: 'Completed KPI checkpoint reviews',
      ATTITUDE: 'Completed Attitude evaluations', CLOSE: 'Close Review Period', SHOW: 'View Outstanding Requirements', HIDE: 'Hide Outstanding Requirements',
      CLOSED_HELP: 'KPI and Attitude results are now read-only. Appraisal recommendations and HR reviews can continue.',
      CLOSED_BY: 'Closed by {{name}}', NO_REOPEN: 'This review period cannot be reopened.',
      EARLIEST: 'Earliest closure:', PERIOD_REQUIREMENTS: 'Review Period Requirements', EMPLOYEE_REQUIREMENTS: 'Outstanding Employee Assessments',
      EMPLOYEE_TASK_COUNT: 'Total outstanding employee tasks: {{count}}', REQUIREMENT: 'Outstanding Requirement',
      CONFIGURATION_DETAILS_UNAVAILABLE: 'Further configuration details are unavailable.',
      REASONS: { CONFIGURATION: 'Review configuration needs attention', MEASUREMENT_NOT_ENDED: 'Review period is still ongoing',
        KPI_SELF_ASSESSMENT: 'KPI Self-Assessment Pending', KPI_SUPERIOR_REVIEW: 'Superior KPI Review Pending',
        ATTITUDE_SELF_ASSESSMENT: 'Attitude Self-Assessment Pending', ATTITUDE_SUPERIOR_REVIEW: 'Superior Attitude Evaluation Pending' }
    } } }); translate.use('en');
  });
  afterEach(() => fixture?.destroy());
  function mount(patch: Partial<AnnualReviewPeriod> = {}) {
    fixture = TestBed.createComponent(ReviewPeriodClosureComponent); component = fixture.componentInstance;
    fixture.componentRef.setInput('period', { ...savedReview(), status: 'OPEN', ...patch }); fixture.detectChanges();
  }
  function root(): HTMLElement { fixture.detectChanges(); return fixture.nativeElement; }
  function answer(ok: boolean) {
    const options = modal.confirm.calls.mostRecent().args[0];
    const callback = ok ? options?.nzOnOk : options?.nzOnCancel;
    if (callback && !(callback instanceof EventEmitter)) return callback(undefined);
  }
  function blocked(): ReviewPeriodClosure {
    return closureReadiness({ canClose: false, completedEmployees: 1, kpiReviews: { required: 8, completed: 7 }, blockers: [
      { code: 'KPI_SUPERIOR_REVIEW', participantId: 7, employeeName: 'Fictional Employee', departmentName: 'IT', checkpointId: 1,
        checkpointStartDate: '2028-01-01', checkpointEndDate: '2028-03-31', message: 'Complete Superior review.\n<script>plain text only</script>' },
      { code: 'HR_RECONCILIATION', participantId: 7, employeeName: 'Fictional Employee', departmentName: 'IT', checkpointId: null,
        checkpointStartDate: null, checkpointEndDate: null, message: 'An inactive participant still has incomplete results.' }
    ] });
  }
  function periodRequirement(patch: Partial<ReviewPeriodClosureBlocker> = {}): ReviewPeriodClosureBlocker {
    return { code: 'CONFIGURATION', participantId: null, employeeName: null, departmentName: null, checkpointId: null,
      checkpointStartDate: null, checkpointEndDate: null, message: 'KPI and Attitude weights must total 100%.', ...patch };
  }
  function employeeTasks(count: number): ReviewPeriodClosureBlocker[] {
    return Array.from({ length: count }, (_, index) => ({ ...blocked().blockers[0], code: 'KPI_SELF_ASSESSMENT',
      employeeName: `Employee ${index + 1}`, message: `Task ${index + 1}` }));
  }
  it('checks eligibility read-only and separates employee totals from checkpoint tasks', () => {
    mount(); expect(api.closureReadiness).toHaveBeenCalledOnceWith(29); expect(api.close).not.toHaveBeenCalled();
    expect([...root().querySelectorAll('dd')].map(n => n.textContent?.trim())).toEqual(['2 / 2', '8 / 8', '2 / 2']);
    expect(component.canClose).toBeTrue(); expect(root().textContent).toContain('1 Jan 2029');
  });
  it('groups identical configuration issues without discarding contexts or merging different causes', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [
      periodRequirement({ participantId: 1, employeeName: 'Employee A', message: 'Missing Attitude format' }),
      periodRequirement({ participantId: 2, employeeName: 'Employee B', message: 'Missing Attitude format' }),
      periodRequirement({ message: 'Invalid KPI allocation' })
    ] })));
    mount(); component.detailsVisible = true;
    fixture.componentRef.setInput('attitudeConfigurations', [{ id: 8, name: 'Published Attitude', publishedAt: null, coveredRoleIds: [] }]);
    expect(root().querySelectorAll('.period-requirement-list > li').length).toBe(2);
    expect(component.groupedPeriodRequirements[0].members.length).toBe(2);
    expect(root().textContent).toContain('Employee A'); expect(root().textContent).toContain('Employee B');
    expect(root().querySelector('.attitude-coverage-summary')?.textContent).toContain('Retail Sales Executive');
    expect(root().textContent).toContain('REVIEW_PERIOD.ATTITUDE_CONFIGURATION.INCOMPLETE');
    expect(api.closureReadiness).toHaveBeenCalledTimes(1); expect(component.canClose).toBeFalse();
    expect(root().querySelector('.attitude-setup-link')).toBeNull();
  });
  it('links authorised setup users to the assigned configuration and selected period', () => {
    spyOn(TestBed.inject(AuthService), 'hasRole').and.returnValue(true);
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [periodRequirement()] })));
    mount(); component.detailsVisible = true;
    const link = root().querySelector<HTMLAnchorElement>('.attitude-setup-link');
    expect(link?.getAttribute('href')).toContain('reviewPeriodId=29'); expect(link?.getAttribute('href')).toContain('configurationId=8');
  });
  for (const status of ['DRAFT', 'UPCOMING'] as const) {
    it(`does not offer closure or call readiness for ${status} configuration/preview`, () => {
      mount({ status }); component.refresh(); component.confirmClose();
      expect(root().querySelector('section')).toBeNull(); expect(api.closureReadiness).not.toHaveBeenCalled(); expect(modal.confirm).not.toHaveBeenCalled();
    });
  }
  it('does not infer closure permission from a role name and rechecks access before confirming', () => {
    permitted = false; mount(); component.refresh(); component.confirmClose();
    expect(root().querySelector('section')).toBeNull(); expect(api.closureReadiness).not.toHaveBeenCalled(); expect(api.close).not.toHaveBeenCalled();
    permitted = true; component.ngOnChanges(); component.confirmClose(); permitted = false; answer(true);
    expect(api.close).not.toHaveBeenCalled();
  });
  it('disables closing during readiness checks and after a failed refresh', () => {
    const pending = new Subject<ReviewPeriodClosure>(); api.closureReadiness.and.returnValue(pending); mount();
    component.confirmClose(); component.refresh(); expect(modal.confirm).not.toHaveBeenCalled(); expect(api.closureReadiness.calls.count()).toBe(1);
    expect([...root().querySelectorAll<HTMLButtonElement>('.closure-heading button')].every(button => button.disabled)).toBeTrue();
    expect(root().querySelector('.closure-heading .anticon-loading')).not.toBeNull();
    pending.next(closureReadiness()); pending.complete(); expect(component.canClose).toBeTrue();
    api.closureReadiness.and.returnValue(throwError(() => ({ status: 503 }))); component.refresh();
    expect(component.error).toBe('LOAD_ERROR'); expect(component.canClose).toBeFalsy(); expect(api.close).not.toHaveBeenCalled();
    api.closureReadiness.and.returnValue(of(closureReadiness())); component.refresh(); expect(component.canClose).toBeTrue();
  });
  it('displays backend blockers in a collapsed, keyboard-accessible View/Hide disclosure', () => {
    api.closureReadiness.and.returnValue(of(blocked())); mount();
    expect(component.canClose).toBeFalse(); expect(root().querySelector('table')).toBeNull();
    const button = root().querySelector<HTMLButtonElement>('.closure-toggle')!; button.focus(); expect(document.activeElement).toBe(button);
    expect(button.getAttribute('aria-expanded')).toBe('false'); button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true'); expect(root().querySelectorAll('tbody tr').length).toBe(2);
    expect(root().textContent).toContain('Fictional Employee'); expect(root().textContent).toContain('01 Jan 2028 - 31 Mar 2028');
    expect(root().textContent).toContain('<script>'); expect(root().querySelector('script')).toBeNull();
    expect(root().querySelector('.closure-message')?.textContent).toContain('\n');
    component.confirmClose(); expect(modal.confirm).not.toHaveBeenCalled();
    button.click(); fixture.detectChanges(); expect(root().querySelector('table')).toBeNull();
  });
  it('uses server eligibility rather than the browser date or locally complete counts', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [{ code: 'MEASUREMENT_NOT_ENDED',
      participantId: null, employeeName: null, departmentName: null, checkpointId: null, checkpointStartDate: null, checkpointEndDate: null,
      message: 'The End Date has not passed.' }] }))); mount();
    component.confirmClose(); expect(modal.confirm).not.toHaveBeenCalled(); expect(component.canClose).toBeFalse();
    expect(root().querySelector<HTMLButtonElement>('.closure-heading button.ant-btn-primary')?.disabled).toBeTrue();
  });
  it('keeps genuine zero completion counts and does not enable an empty roster locally', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, totalEmployees: 0, completedEmployees: 0,
      kpiReviews: { required: 0, completed: 0 }, attitudeEvaluations: { required: 0, completed: 0 } }))); mount();
    expect([...root().querySelectorAll('dd')].map(n => n.textContent?.trim())).toEqual(['0 / 0', '0 / 0', '0 / 0']); expect(component.canClose).toBeFalse();
  });
  it('requires explicit confirmation, permits cancellation, and prevents duplicate confirmation and close clicks', () => {
    const pending = new Subject<ReviewPeriodClosure>(); api.close.and.returnValue(pending); mount();
    component.confirmClose(); component.confirmClose(); expect(modal.confirm.calls.count()).toBe(1); expect(api.close).not.toHaveBeenCalled();
    answer(false); expect(component.canClose).toBeTrue(); component.confirmClose(); answer(true); answer(true);
    expect(api.close).toHaveBeenCalledOnceWith(29); expect(component.closing).toBeTrue(); component.confirmClose(); component.refresh();
    expect(modal.confirm.calls.count()).toBe(2); expect(api.closureReadiness.calls.count()).toBe(1);
    pending.next(closureReadiness({ status: 'CLOSED', canClose: false })); pending.complete(); expect(component.isClosed).toBeTrue();
  });
  it('reports the authoritative closing actor/time and never offers reopening or another close', () => {
    mount(); spyOn(component.closed, 'emit'); component.confirmClose(); answer(true);
    expect(component.closed.emit).toHaveBeenCalledWith(jasmine.objectContaining({ status: 'CLOSED', closedByName: 'HR Reviewer' }));
    expect(root().textContent).toContain('Closed by HR Reviewer'); expect(root().textContent).toContain('12:05 PM');
    expect(root().textContent).toContain('Appraisal recommendations and HR reviews can continue.');
    expect(root().querySelector('button')).toBeNull(); expect(root().querySelector('time')?.getAttribute('datetime')).toBe('2029-01-02T04:05:00Z');
    component.refresh(); component.confirmClose(); expect(api.close.calls.count()).toBe(1);
  });
  it('keeps historical Closed records clean and never invents missing actor/time metadata', () => {
    mount({ status: 'CLOSED', closedAt: null, closedByName: null });
    expect(api.closureReadiness).not.toHaveBeenCalled(); expect(root().querySelector('button, time, table')).toBeNull();
    expect(root().textContent).not.toContain('Closed by');
  });
  it('refreshes a stale Open page when readiness says another authorised user already closed it', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ status: 'CLOSED', canClose: false,
      closedByName: 'Another HR reviewer', blockers: blocked().blockers }))); mount();
    expect(component.isClosed).toBeTrue(); expect(root().querySelector('button, table, dl')).toBeNull();
    expect(root().textContent).toContain('Another HR reviewer'); expect(api.close).not.toHaveBeenCalled();
  });
  it('shows fresh requirements from a blocked 409 without losing independent completion counts', () => {
    api.close.and.returnValue(throwError(() => ({ status: 409, error: blocked() }))); mount(); component.confirmClose(); answer(true);
    expect(component.error).toBe('CHANGED'); expect(component.canClose).toBeFalse(); expect(component.detailsVisible).toBeTrue();
    expect([...root().querySelectorAll('dd')].map(n => n.textContent?.trim())).toEqual(['1 / 2', '7 / 8', '2 / 2']);
    expect(root().querySelectorAll('tbody tr').length).toBe(2);
  });
  it('accepts idempotent Closed replies without displaying stale blockers', () => {
    api.close.and.returnValue(of(closureReadiness({ status: 'CLOSED', canClose: false, blockers: blocked().blockers })));
    mount(); component.confirmClose(); answer(true); expect(component.error).toBe(''); expect(root().querySelector('.closure-toggle')).toBeNull();
  });
  for (const payload of [{ message: 'ordinary conflict' }, closureReadiness({ reviewPeriodId: 30 })]) {
    it('fails closed for an unrelated or malformed conflict response', () => {
      api.close.and.returnValue(throwError(() => ({ status: 409, error: payload }))); mount(); component.confirmClose(); answer(true);
      expect(component.error).toBe('CLOSE_ERROR'); expect(component.readiness).toBeNull(); expect(component.canClose).toBeFalsy();
    });
  }
  for (const status of [0, 500]) {
    it(`does not automatically retry an uncertain/failed close (${status})`, () => {
      api.close.and.returnValue(throwError(() => ({ status }))); mount(); component.confirmClose(); answer(true);
      expect(api.close.calls.count()).toBe(1); expect(component.canClose).toBeFalsy(); expect(component.error).toBe('CLOSE_ERROR');
      component.refresh(); expect(component.canClose).toBeTrue(); expect(api.close.calls.count()).toBe(1);
    });
  }
  it('hides actions after a forbidden response', () => {
    api.close.and.returnValue(throwError(() => ({ status: 403 }))); mount(); component.confirmClose(); answer(true);
    expect(component.error).toBe('NO_ACCESS'); expect(root().querySelector('section, button')).toBeNull();
    component.refresh(); expect(api.closureReadiness.calls.count()).toBe(1);
  });
  it('cancels stale requests and confirmations when the selected period changes', () => {
    const old = new Subject<ReviewPeriodClosure>(); api.closureReadiness.and.returnValue(old); mount();
    api.closureReadiness.and.returnValue(of(closureReadiness({ reviewPeriodId: 30 })));
    fixture.componentRef.setInput('period', { ...savedReview(), id: 30, status: 'OPEN' }); fixture.detectChanges();
    old.next(closureReadiness()); expect(component.readiness?.reviewPeriodId).toBe(30);
    component.confirmClose(); fixture.componentRef.setInput('period', { ...savedReview(), status: 'OPEN' }); fixture.detectChanges(); answer(true);
    expect(destroyDialog).toHaveBeenCalled(); expect(api.close).not.toHaveBeenCalled();
  });
  it('cancels pending subscriptions and dialogs when leaving the page', () => {
    mount(); component.confirmClose(); fixture.destroy(); answer(true); expect(api.close).not.toHaveBeenCalled(); expect(destroyDialog).toHaveBeenCalled();
  });
  it('wraps summary/actions on mobile and confines long requirement text to the table scroll region', () => {
    const value = blocked(); value.blockers[0].message = 'A'.repeat(300); value.blockers[0].employeeName = 'B'.repeat(300);
    api.closureReadiness.and.returnValue(of(value)); mount(); component.detailsVisible = true;
    for (const width of [1440, 768, 360]) {
      const view = reviewLayout(root(), width);
      try {
        expect(view.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(view.window.getComputedStyle(view.root.querySelector('.closure-counts')!).gridTemplateColumns.split(' ').length).toBe(width < 600 ? 1 : 3);
        expect(view.window.getComputedStyle(view.root.querySelector('.table-scroll')!).overflowX).toBe('auto');
      } finally { view.remove(); }
    }
  });
  it('separates period/configuration requirements from employee tasks without dropping recorded context', () => {
    const source = [periodRequirement({ code: 'MEASUREMENT_NOT_ENDED' }), ...employeeTasks(2),
      periodRequirement(), periodRequirement({ ...blocked().blockers[0], code: 'CONFIGURATION', message: 'Recorded allocation needs attention.' }),
      periodRequirement({ code: 'COMPANY_RESULT', message: 'A period-wide published result is unavailable.' }),
      { ...blocked().blockers[0], code: 'COMPANY_RESULT' as const, message: 'Waiting for a result required by this employee.' }];
    const original = structuredClone(source);
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: source })));
    mount(); component.detailsVisible = true;
    expect(root().querySelectorAll('.period-requirement-list > li').length).toBe(4);
    expect(root().querySelectorAll('tbody tr').length).toBe(3);
    expect(component.periodRequirements.length + component.employeeRequirements.length).toBe(source.length);
    expect(root().querySelector('.period-requirements')?.textContent).toContain('Fictional Employee');
    expect(root().querySelector('.requirement-context')?.textContent).toContain('IT');
    expect(root().querySelector('.requirement-context')?.textContent).toContain('01 Jan 2028 - 31 Mar 2028');
    expect(root().querySelector('.employee-requirements')?.textContent).not.toContain('Review configuration needs attention');
    expect(root().querySelector('.employee-requirements-heading')?.textContent).toContain('Total outstanding employee tasks: 3');
    expect(source).toEqual(original); expect(component.canClose).toBeFalse();
  });
  it('presents an ongoing period with an actual formatted earliest date, outside an employee table', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, earliestCloseDate: '2027-01-01',
      blockers: [periodRequirement({ code: 'MEASUREMENT_NOT_ENDED', message: 'Closure is available from 2027-01-01, after the measurement period ends' })] })));
    mount(); component.detailsVisible = true;
    expect(root().querySelector('.period-requirements')?.textContent).toContain('Review period is still ongoing');
    expect(root().querySelector('.period-requirements')?.textContent).toContain('Earliest closure: 1 Jan 2027.');
    expect(root().querySelector('.closure-state')?.textContent).toContain('Earliest closure: 1 Jan 2027');
    expect(root().querySelector('table, nz-pagination')).toBeNull(); expect(root().textContent).not.toContain('2027-01-01, after');
  });
  for (const message of [null, '', 'Review configuration needs attention', 'Review configuration needs attention.']) {
    it('does not fabricate a cause for generic or missing configuration details', () => {
      api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, earliestCloseDate: null,
        blockers: [periodRequirement({ message })] }))); mount(); component.detailsVisible = true;
      expect(root().textContent).toContain('Review configuration needs attention');
      expect(root().textContent).toContain('Further configuration details are unavailable.');
      expect(root().textContent).not.toContain('weights must total'); expect(root().textContent).not.toContain('Earliest closure:');
      expect(component.canClose).toBeFalse();
    });
  }
  it('retains exact configuration details as plain text and exposes a keyboard-operable saved-settings shortcut', () => {
    const message = 'KPI allocation is incomplete.\n<img src=x onerror=alert(1)>';
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [periodRequirement({ message })] })));
    mount(); component.detailsVisible = true; spyOn(component.viewConfiguration, 'emit');
    const shortcut = root().querySelector<HTMLButtonElement>('.configuration-link')!; shortcut.focus(); expect(document.activeElement).toBe(shortcut);
    expect(root().querySelector('.closure-message')?.textContent).toBe(message); expect(root().querySelector('img')).toBeNull();
    shortcut.click(); expect(component.viewConfiguration.emit).toHaveBeenCalledTimes(1); expect(api.close).not.toHaveBeenCalled();
  });
  it('paginates the complete employee-task list locally without changing readiness metrics or making page requests', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, totalEmployees: 7, completedEmployees: 0,
      kpiReviews: { completed: 0, required: 15 }, attitudeEvaluations: { completed: 1, required: 7 },
      blockers: [periodRequirement(), ...employeeTasks(25)] }))); mount(); component.detailsVisible = true;
    expect(root().querySelectorAll('tbody tr').length).toBe(10); expect(root().querySelector('tbody')?.textContent).toContain('Employee 10');
    expect(root().querySelector('tbody')?.textContent).not.toContain('Employee 11');
    const pagination = fixture.debugElement.query(By.directive(NzPaginationComponent)).componentInstance as NzPaginationComponent;
    expect(pagination.nzTotal).toBe(25); expect(pagination.nzPageSize).toBe(10);
    const sizes = fixture.debugElement.query(By.directive(TablePageSizeSelectorComponent)).componentInstance as TablePageSizeSelectorComponent;
    expect(sizes.sizeOptions).toEqual([10, 20, 50]);
    const next = root().querySelector<HTMLButtonElement>('.ant-pagination-next button')!;
    next.focus(); expect(document.activeElement).toBe(next); next.click(); fixture.detectChanges();
    expect(component.employeePage).toBe(2); expect(root().querySelector('tbody')?.textContent).toContain('Employee 11');
    expect(root().querySelector('tbody')?.textContent).toContain('Employee 20');
    pagination.nzPageIndexChange.emit(3); fixture.detectChanges(); expect(root().querySelectorAll('tbody tr').length).toBe(5);
    expect(root().querySelector('tbody')?.textContent).toContain('Employee 25');
    expect([...root().querySelectorAll('dd')].map(n => n.textContent?.trim())).toEqual(['0 / 7', '0 / 15', '1 / 7']);
    expect(root().querySelector('.employee-requirements-heading')?.textContent).toContain('Total outstanding employee tasks: 25');
    expect(root().querySelectorAll('.period-requirement-list li').length).toBe(1); expect(component.canClose).toBeFalse();
    sizes.pageSizeChange.emit(20); fixture.detectChanges(); expect(component.employeePage).toBe(1); expect(root().querySelectorAll('tbody tr').length).toBe(20);
    sizes.pageSizeChange.emit(50); fixture.detectChanges(); expect(root().querySelectorAll('tbody tr').length).toBe(25);
    expect(api.closureReadiness.calls.count()).toBe(1); expect(api.close).not.toHaveBeenCalled();
  });
  it('keeps no employee table for period-only blockers and never infers Ready from a visible empty page', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [periodRequirement()] })));
    mount(); component.detailsVisible = true; expect(root().querySelector('.employee-requirements, table, nz-pagination')).toBeNull();
    component.confirmClose(); expect(modal.confirm).not.toHaveBeenCalled(); expect(component.canClose).toBeFalse();
  });
  it('refreshes every requirement, resets a stale page, and preserves the expanded disclosure', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: employeeTasks(25) })));
    mount(); component.detailsVisible = true; component.changeEmployeePage(3); expect(component.visibleEmployeeRequirements.length).toBe(5);
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [periodRequirement(), ...employeeTasks(2)] })));
    component.refresh(); expect(component.employeePage).toBe(1); expect(component.detailsVisible).toBeTrue();
    expect(root().querySelectorAll('tbody tr').length).toBe(2); expect(root().querySelectorAll('.period-requirement-list li').length).toBe(1);
    expect(api.closureReadiness.calls.count()).toBe(2); expect(api.close).not.toHaveBeenCalled();
    const toggle = root().querySelector<HTMLButtonElement>('.closure-toggle')!; toggle.click(); fixture.detectChanges();
    expect(root().querySelector('.period-requirements')).toBeNull(); toggle.click(); fixture.detectChanges();
    expect(root().querySelectorAll('tbody tr').length).toBe(2);
  });
  it('clamps page events and rejects unsupported page sizes', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: employeeTasks(25) }))); mount();
    component.changeEmployeePage(999); expect(component.employeePage).toBe(3); component.changeEmployeePage(-1); expect(component.employeePage).toBe(1);
    component.changeEmployeePage(1.5); expect(component.employeePage).toBe(1); component.changeEmployeePageSize(100); expect(component.employeePageSize).toBe(10);
    component.changeEmployeePageSize(20); expect(component.employeePageSize).toBe(20); expect(component.employeePage).toBe(1);
    fixture.componentRef.setInput('period', { ...savedReview(), id: 30, status: 'CLOSED' }); fixture.detectChanges();
    expect(component.employeePage).toBe(1); expect(component.employeePageSize).toBe(10);
  });
  it('replaces stale requirements with both categories after the server rejects a close', () => {
    api.close.and.returnValue(throwError(() => ({ status: 409, error: closureReadiness({ canClose: false,
      blockers: [periodRequirement({ code: 'MEASUREMENT_NOT_ENDED' }), ...employeeTasks(15)] }) })));
    mount(); component.employeePage = 4; component.confirmClose(); answer(true);
    expect(component.employeePage).toBe(1); expect(component.detailsVisible).toBeTrue(); expect(component.error).toBe('CHANGED');
    expect(root().querySelectorAll('tbody tr').length).toBe(10); expect(root().querySelectorAll('.period-requirement-list li').length).toBe(1);
    expect(root().querySelector('.employee-requirements-heading')?.textContent).toContain('15'); expect(component.canClose).toBeFalse();
  });
  it('does not expose an unmapped internal code as a heading', () => {
    const blocker = { ...blocked().blockers[0], code: 'INTERNAL_FUTURE_CODE' as ReviewPeriodClosureBlocker['code'], message: 'A required result is unavailable.' };
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [blocker] })));
    mount(); component.detailsVisible = true; expect(root().querySelector('tbody strong')?.textContent).toBe('Outstanding Requirement');
    expect(root().textContent).not.toContain('INTERNAL_FUTURE_CODE');
  });
  it('preserves a generic configuration blocker without message text in a rejected Close response', () => {
    api.close.and.returnValue(throwError(() => ({ status: 409, error: closureReadiness({ canClose: false,
      blockers: [periodRequirement({ message: null })] }) })));
    mount(); component.confirmClose(); answer(true);
    expect(component.error).toBe('CHANGED'); expect(component.periodRequirements.length).toBe(1);
    expect(root().textContent).toContain('Further configuration details are unavailable.'); expect(component.canClose).toBeFalse();
  });
  it('contains long configuration context, employee tasks and pagination on narrow screens', () => {
    api.closureReadiness.and.returnValue(of(closureReadiness({ canClose: false, blockers: [periodRequirement({
      participantId: 7, employeeName: 'B'.repeat(250), message: 'A'.repeat(300) }), ...employeeTasks(23)] })));
    mount(); component.detailsVisible = true;
    for (const width of [1440, 768, 360]) {
      const view = reviewLayout(root(), width);
      try {
        expect(view.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        const context = view.root.querySelector<HTMLElement>('.period-requirement-list li')!;
        expect(context.scrollWidth).toBeLessThanOrEqual(context.clientWidth + 1);
        expect(view.window.getComputedStyle(view.root.querySelector('.closure-pagination')!).flexWrap).toBe('wrap');
      } finally { view.remove(); }
    }
  });
});
