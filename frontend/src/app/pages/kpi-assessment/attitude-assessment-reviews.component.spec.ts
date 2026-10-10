import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AttitudeAssessment, AttitudeAssessmentItem, AttitudeAssessmentReview } from '../../models/attitude-assessment.model';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';
import { AttitudeAssessmentReviewsComponent } from './attitude-assessment-reviews.component';

describe('Superior Attitude Evaluations', () => {
  let fixture: ComponentFixture<AttitudeAssessmentReviewsComponent>;
  let page: AttitudeAssessmentReviewsComponent;
  let api: jasmine.SpyObj<AttitudeAssessmentService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const item = (id = 44): AttitudeAssessmentItem => ({ id, criterionId: id + 1, name: id === 44 ? 'Respect' : 'Leadership',
    description: 'Treat others with dignity', criterionType: id === 44 ? 'SHARED_CORE_VALUE' : 'FORMAT_SPECIFIC', displayOrder: id,
    selfPoint: 5, selfComment: 'Employee reflection', superiorPoint: null, superiorComment: null });
  const assessment = (overrides: Partial<AttitudeAssessment> = {}): AttitudeAssessment => ({ id: 20, participantId: 7, employeeName: 'Amir',
    roleName: 'Sales Executive', departmentName: 'Retail', reviewPeriodId: 1, reviewPeriodName: '2026 Annual Review', reviewPeriodStatus: 'OPEN',
    configurationId: 2, configurationName: 'Preserved configuration', evaluationFormat: 'SALES', status: 'PENDING_REVIEW',
    createdAt: '2026-12-21', updatedAt: '2026-12-21', submittedAt: '2026-12-21', submittedBy: 'employee', submittedToSuperiorId: 'superior', submittedLate: true,
    selfAssessmentDeadline: '2026-12-20', superiorEvaluationDeadline: '2026-12-27', available: true, availabilityTitle: null, availabilityMessage: null,
    canSaveDraft: false, canSubmit: false, overdue: false, submissionBlockers: [],
    canSaveSuperiorDraft: true, canCompleteReview: false, superiorDraftSaved: false, superiorOverdue: false,
    reviewBlockers: ['Select a Superior Assessment Point for Respect'], reviewedAt: null, reviewedBy: null, reviewedLate: null, attitudeScore: null,
    ratingDefinitions: [1, 2, 3, 4, 5].map(point => ({ point, label: `Configured rating ${point}`, description: `Expected behaviour ${point}` })),
    items: [item()], ...overrides });
  const row = (id = 20, status: AttitudeAssessmentReview['status'] = 'PENDING_REVIEW'): AttitudeAssessmentReview => ({ id,
    employeeId: 'employee', employeeName: 'Amir', roleName: 'Sales Executive', departmentName: 'Retail', reviewPeriodId: 1,
    reviewPeriodName: '2026 Annual Review', reviewPeriodStatus: 'OPEN', evaluationFormat: 'SALES', status,
    superiorEvaluationDeadline: '2026-12-27', submittedAt: '2026-12-21', submittedLate: true, reviewedAt: null, reviewedLate: null,
    attitudeScore: null, canReview: status === 'PENDING_REVIEW', superiorDraftSaved: false, superiorOverdue: false });
  beforeEach(async () => {
    api = jasmine.createSpyObj<AttitudeAssessmentService>('api', ['reviews', 'get', 'saveSuperiorDraft', 'completeReview']);
    api.reviews.and.returnValue(of([row(), row(21, 'REVIEWED')])); api.get.and.returnValue(of(assessment()));
    api.saveSuperiorDraft.and.callFake((_id, request) => {
      const items = request.items.map(answer => ({ ...item(answer.itemId), superiorPoint: answer.superiorPoint, superiorComment: answer.superiorComment }));
      const complete = items.length > 0 && items.every(answer => answer.superiorPoint != null);
      return of(assessment({ items, superiorDraftSaved: true, canCompleteReview: complete,
        reviewBlockers: complete ? [] : ['Select a Superior Assessment Point for Respect'] }));
    });
    api.completeReview.and.returnValue(of(assessment({ status: 'REVIEWED', items: [{ ...item(), superiorPoint: 4, superiorComment: 'Verified' }],
      canSaveSuperiorDraft: false, canCompleteReview: false, reviewBlockers: [], attitudeScore: 83.3333,
      reviewedAt: '2026-12-28T04:00:00Z', reviewedLate: true })));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [AttitudeAssessmentReviewsComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: AttitudeAssessmentService, useValue: api }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(AttitudeAssessmentReviewsComponent); page = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  function drawer() { return document.querySelector('.ant-drawer-open .ant-drawer-body')!; }
  it('loads a scoped queue without writes, with employee, department, period and progress filters', () => {
    expect(api.reviews).toHaveBeenCalledTimes(1); expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
    expect(page.visibleReviews.length).toBe(1); page.filter = 'REVIEWED'; expect(page.visibleReviews[0].id).toBe(21);
    page.search = 'retail'; expect(page.count('REVIEWED')).toBe(1); page.search = 'other'; expect(page.visibleReviews).toEqual([]);
    page.search = ''; page.periodId = 2; expect(page.visibleReviews).toEqual([]);
    expect(page.statuses).toEqual(['PENDING_REVIEW', 'DRAFT', 'REVIEWED']);
  });
  it('explains the page purpose above its status filters', () => {
    const intro: HTMLElement = fixture.nativeElement.querySelector('.team-review-intro');
    expect(intro.querySelector('.help')?.textContent).toContain('ATTITUDE_REVIEW.HELP');
    expect(getComputedStyle(intro).marginBottom).toBe('20px');
    expect(fixture.nativeElement.querySelectorAll('.review-status-tab').length).toBe(3);
  });
  it('shows the bound format, deadline, shared and specific criteria with immutable Self answers', () => {
    api.get.and.returnValue(of(assessment({ items: [item(), item(45)], evaluationFormat: 'MANAGER' })));
    page.open(20); fixture.detectChanges();
    expect(drawer().textContent).toContain('ATTITUDE_SETUP.FORMAT.MANAGER'); expect(drawer().textContent).toContain('27 Dec 2026');
    expect(drawer().textContent).toContain('Respect'); expect(drawer().textContent).toContain('Leadership');
    expect(drawer().querySelector('.self-answers')?.textContent).toContain('Employee reflection');
    expect(drawer().querySelector('.self-answers input')).toBeNull(); expect(drawer().querySelector('.self-answers textarea')).toBeNull();
    expect(drawer().querySelectorAll('.superior-answers input[type=radio]').length).toBe(10);
    expect(drawer().textContent).not.toContain('DEPARTMENT_REVIEW.RETURN');
  });
  it('uses the preserved configured ratings in both answers and the reusable guide, not fixed KPI labels', () => {
    page.open(20); page.items[0].superiorPoint = 3; fixture.detectChanges();
    expect(drawer().querySelector('.superior-answers')?.textContent).toContain('Configured rating 3');
    expect(drawer().querySelector('.superior-answers')?.textContent).not.toContain('Expected behaviour 3');
    expect(drawer().querySelector('.self-answers')?.textContent).toContain('Configured rating 5');
    expect(drawer().querySelector('.self-answers')?.textContent).not.toContain('Expected behaviour 5');
    expect(drawer().textContent).toContain('Treat others with dignity');
    const guide = fixture.debugElement.query(By.directive(KpiScoringGuideComponent)).componentInstance as KpiScoringGuideComponent;
    page.viewScoringGuide();
    expect(guide.visible).toBeTrue();
    guide.visible = false; fixture.detectChanges();
    expect(guide.ratings).toEqual(page.selected!.ratingDefinitions); expect(guide.titleKey).toBe('ATTITUDE_ASSESSMENT.GUIDE_TITLE');
    expect(drawer().textContent).not.toContain('KPI_SCORING_GUIDE.RATING');
    expect(drawer().querySelector('app-kpi-scoring-guide')).toBeNull();
    expect(document.querySelector('.ant-drawer-open .ant-drawer-header app-kpi-scoring-guide')).toBeNull();
    expect(guide.showButton).toBeFalse();
  });
  it('loads the configured guide before opening a review without creating a Draft', () => {
    page.viewScoringGuide();
    expect(api.get).toHaveBeenCalledOnceWith(20); expect(page.guideRatings).toEqual(assessment().ratingDefinitions);
    expect(page.scoringGuide?.visible).toBeTrue(); expect(page.selected).toBeNull(); expect(page.drawerVisible).toBeFalse();
    expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
  });
  it('requires a period selection when available reviews use different periods', () => {
    page.reviews = [row(), { ...row(21), reviewPeriodId: 2, reviewPeriodName: '2027' }];
    page.viewScoringGuide(); expect(api.get).not.toHaveBeenCalled(); expect(page.error).toBe('FORM_VALIDATION.SELECT_PERIOD');
    page.periodId = 2; page.viewScoringGuide(); expect(api.get).toHaveBeenCalledOnceWith(21);
  });
  it('binds larger Superior rating controls and comments without changing Self answers', async () => {
    page.open(20); fixture.detectChanges();
    const input: HTMLInputElement = drawer().querySelectorAll('input[type=radio]')[2] as HTMLInputElement;
    input.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(page.items[0].superiorPoint).toBe(3); expect(page.items[0].selfPoint).toBe(5);
    const textarea = drawer().querySelector('textarea')!; textarea.value = 'Observed progress'; textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges(); await fixture.whenStable(); expect(page.items[0].superiorComment).toBe('Observed progress');
    expect(getComputedStyle(drawer().querySelector('.point-option span')!).width).toBe('40px');
  });
  it('allows incomplete Draft saves and then shows inline required warnings with Draft progress', () => {
    page.open(20); fixture.detectChanges(); expect(drawer().querySelector('.field-error')).toBeNull();
    expect(drawer().querySelector('.required-mark')).not.toBeNull();
    page.saveDraft(); fixture.detectChanges();
    expect(api.saveSuperiorDraft).toHaveBeenCalledOnceWith(20, { items: [{ itemId: 44, superiorPoint: null, superiorComment: null }] });
    expect(page.draftWarnings).toBeTrue(); expect(drawer().querySelector('.field-error')?.textContent).toContain('MY_ASSESSMENTS.POINT_REQUIRED');
    expect(page.reviewProgress(page.selected!)).toBe('DRAFT'); expect(page.count('DRAFT')).toBe(1); expect(page.dirty).toBeFalse();
    expect(page.canComplete).toBeFalse();
  });
  it('shows persisted drafts on reload without treating merely opening a review as a save', () => {
    api.reviews.and.returnValue(of([{ ...row(), superiorDraftSaved: true }])); page.load(); page.filter = 'DRAFT';
    expect(page.visibleReviews.length).toBe(1); page.open(20);
    expect(page.reviewProgress(page.selected!)).toBe('PENDING_REVIEW'); expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
  });
  it('blocks completion until every criterion is rated without blocking Draft saves', () => {
    api.get.and.returnValue(of(assessment({ items: [item(), item(45)] }))); page.open(20);
    page.items[0].superiorPoint = 4; page.confirmComplete(); fixture.detectChanges();
    expect(page.completionReasons).toContain('ATTITUDE_REVIEW.POINTS_REMAINING');
    expect(page.pointError(page.items[1])).toBe('MY_ASSESSMENTS.POINT_REQUIRED'); expect(modal.confirm).not.toHaveBeenCalled();
    expect(api.completeReview).not.toHaveBeenCalled(); page.items[1].superiorPoint = 3; expect(page.canComplete).toBeTrue();
  });
  it('rejects supplied invalid points/comments and highlights them inline', () => {
    page.open(20);
    for (const point of [0, 6, 2.5]) {
      page.items[0].superiorPoint = point; page.saveDraft(); expect(page.pointError(page.items[0])).toBe('MY_ASSESSMENTS.INVALID_POINT');
      expect(page.canComplete).toBeFalse();
    }
    page.items[0].superiorPoint = 4; page.items[0].superiorComment = 'x'.repeat(10001); page.saveDraft(); fixture.detectChanges();
    expect(drawer().querySelector('textarea')?.getAttribute('aria-invalid')).toBe('true');
    expect(drawer().textContent).toContain('MY_ASSESSMENTS.COMMENT_TOO_LONG'); expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
  });
  it('saves current answers before completion and displays only the server official score with two decimals', () => {
    page.open(20); page.items[0].superiorPoint = 4; page.confirmComplete(); expect(api.completeReview).not.toHaveBeenCalled(); confirm(); fixture.detectChanges();
    expect(api.saveSuperiorDraft).toHaveBeenCalledBefore(api.completeReview); expect(api.completeReview).toHaveBeenCalledOnceWith(20);
    expect(page.readonly).toBeTrue(); expect(page.count('REVIEWED')).toBe(2); expect(drawer().querySelector('.checkpoint-score')?.textContent).toContain('83.33%');
    expect(drawer().querySelector('.score-reviewed-at')?.textContent).toContain('28 Dec 2026');
    expect(drawer().querySelector('input[type=radio]')).toBeNull();
  });
  it('rechecks backend readiness after saving and does not complete a changed evaluation', () => {
    page.open(20); page.items[0].superiorPoint = 4;
    api.saveSuperiorDraft.and.returnValue(of(assessment({ canCompleteReview: false, reviewBlockers: ['Period changed'] })));
    page.complete(); expect(api.completeReview).not.toHaveBeenCalled(); expect(page.error).toBe('Period changed'); expect(page.busy).toBeFalse();
  });
  it('keeps Reviewed, non-Open and non-actionable evaluations read-only', () => {
    for (const overrides of [{ status: 'REVIEWED' as const }, { reviewPeriodStatus: 'CLOSED' as const },
      { reviewPeriodStatus: 'UPCOMING' as const }, { canSaveSuperiorDraft: false }]) {
      page.drawerVisible = false; api.get.and.returnValue(of(assessment(overrides))); page.open(20); page.saveDraft(); page.complete(); fixture.detectChanges();
      expect(page.readonly).toBeTrue(); expect(drawer().querySelector('textarea')).toBeNull();
    }
    expect(api.saveSuperiorDraft).not.toHaveBeenCalled(); expect(api.completeReview).not.toHaveBeenCalled();
  });
  it('allows late evaluation in an Open period and shows an overdue notice', () => {
    api.get.and.returnValue(of(assessment({ superiorOverdue: true }))); page.open(20); page.items[0].superiorPoint = 4; fixture.detectChanges();
    expect(drawer().textContent).toContain('ATTITUDE_REVIEW.LATE_HELP'); expect(page.canComplete).toBeTrue();
  });
  it('keeps inputs on failed saves and switches to read-only if relationship/permission access changes', () => {
    page.open(20); page.items[0].superiorPoint = 3;
    api.saveSuperiorDraft.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } }))); page.saveDraft();
    expect(page.items[0].superiorPoint).toBe(3); expect(page.dirty).toBeTrue(); expect(page.error).toBe('Please retry');
    api.saveSuperiorDraft.and.returnValue(throwError(() => ({ status: 403 }))); page.saveDraft();
    expect(page.readonly).toBeTrue(); expect(page.error).toBe('ATTITUDE_REVIEW.ACCESS_CHANGED'); expect(page.items[0].superiorPoint).toBe(3);
  });
  it('retains a successfully saved Draft if final completion fails', () => {
    page.open(20); page.items[0].superiorPoint = 4; api.completeReview.and.returnValue(throwError(() => ({ error: { message: 'Try again' } })));
    page.complete(); expect(page.selected?.superiorDraftSaved).toBeTrue(); expect(page.items[0].superiorPoint).toBe(4);
    expect(page.dirty).toBeFalse(); expect(page.readonly).toBeFalse(); expect(page.error).toBe('Try again');
  });
  it('prevents duplicate clicks and route/close actions while a save is in flight', () => {
    page.open(20); const operation = new Subject<AttitudeAssessment>(); api.saveSuperiorDraft.and.returnValue(operation);
    page.saveDraft(); page.saveDraft(); page.close(); expect(api.saveSuperiorDraft).toHaveBeenCalledTimes(1);
    expect(page.canLeave()).toBeFalse(); expect(page.drawerVisible).toBeTrue();
    operation.next(assessment({ superiorDraftSaved: true })); operation.complete(); expect(page.busy).toBeFalse();
  });
  it('confirms unsaved close and route changes and protects browser unload', async () => {
    page.open(20); page.items[0].superiorPoint = 3; page.close(); expect(page.drawerVisible).toBeTrue();
    const event = { preventDefault: jasmine.createSpy(), returnValue: 'before' };
    page.warnUnsaved(event as unknown as BeforeUnloadEvent); expect(event.preventDefault).toHaveBeenCalled();
    const leaving = page.canLeave(); confirm(); expect(await leaving).toBeTrue();
    page.close(); confirm(); expect(page.drawerVisible).toBeFalse(); expect(page.dirty).toBeFalse();
  });
  it('shows helpful empty states and retries a failed queue load', () => {
    api.reviews.and.returnValue(throwError(() => ({ error: { message: 'Unavailable' } }))); page.load(); fixture.detectChanges();
    expect(page.loading).toBeFalse(); expect(fixture.nativeElement.textContent).toContain('Unavailable');
    api.reviews.and.returnValue(of([])); page.load(); fixture.detectChanges();
    expect(page.error).toBe(''); expect(fixture.nativeElement.textContent).toContain('ATTITUDE_REVIEW.EMPTY.PENDING_REVIEW');
  });
});
