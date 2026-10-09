import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { KpiAssessmentReviewsComponent } from './kpi-assessment-reviews.component';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentItem, KpiAssessmentReview,
  superiorAssessmentItemError } from '../../models/kpi-assessment.model';
import { emptyKpiItem } from '../../models/kpi-plan.model';

describe('Team KPI assessment reviews', () => {
  let fixture: ComponentFixture<KpiAssessmentReviewsComponent>;
  let page: KpiAssessmentReviewsComponent;
  let api: jasmine.SpyObj<KpiAssessmentService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const checkpoint: KpiAssessmentCheckpoint = { id: 11, reviewFrequency: 'MONTHLY', sequenceNumber: 1,
    startDate: '2028-01-01', endDate: '2028-01-31', availableFrom: '2028-02-01', selfAssessmentDeadline: '2028-02-05',
    superiorAssessmentDeadline: '2028-02-10', available: true, overdue: false, assessmentId: 20, assessmentStatus: 'PENDING_REVIEW' };
  const item = (): KpiAssessmentItem => ({ id: 44, assignmentId: 4, level: 'INDIVIDUAL',
    kpi: { ...emptyKpiItem(), name: 'Sales', perspective: 'Financial', kra: 'Growth', target: 'RM 80,000', weightage: 100,
      scoringDefinitions: { 1: 'Low', 2: 'Below target', 3: 'Partial target', 4: 'On target', 5: 'Excellent' } },
    selfPoint: 5, selfComment: 'Employee evidence', superiorPoint: null, superiorComment: null,
    evidence: [{ id: 8, originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 5, uploadedBy: 'employee', uploadedAt: '2028-02-01' }] });
  const assessment = (overrides: Partial<KpiAssessment> = {}): KpiAssessment => ({ id: 20, participantId: 7, employeeName: 'Amir',
    roleName: 'Sales Executive', departmentName: 'Retail', reviewPeriodId: 1, reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN',
    checkpoint, status: 'PENDING_REVIEW', items: [item()], kpiAllocation: null, missingLevels: [], submissionBlockers: [],
    canSaveDraft: false, canSubmit: false, overdue: false, canSaveSuperiorDraft: true, canCompleteReview: false,
    superiorOverdue: false, reviewBlockers: ['Select a Superior Assessment Point for Sales'], createdAt: '2028-02-01', updatedAt: '2028-02-01',
    submittedAt: '2028-02-01', submittedBy: 'employee', submittedToSuperiorId: 'superior', submittedLate: false,
    reviewedAt: null, reviewedBy: null, reviewedLate: null, checkpointScore: null, ...overrides });
  const row = (id = 20, status: KpiAssessmentReview['status'] = 'PENDING_REVIEW'): KpiAssessmentReview => ({ id,
    employeeId: 'employee', employeeName: 'Amir', roleName: 'Sales Executive', departmentName: 'Retail', reviewPeriodId: 1,
    reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', checkpoint, status, submittedAt: '2028-02-01', submittedLate: false,
    reviewedAt: null, reviewedLate: null, checkpointScore: null, canReview: status === 'PENDING_REVIEW', superiorOverdue: false });
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiAssessmentService>('api', ['reviews', 'get', 'saveSuperiorDraft', 'completeReview', 'download']);
    api.reviews.and.returnValue(of([row(), row(21, 'REVIEWED')])); api.get.and.returnValue(of(assessment()));
    api.saveSuperiorDraft.and.callFake((_id, request) => {
      const items = request.items.map(answer => ({ ...item(), superiorPoint: answer.superiorPoint, superiorComment: answer.superiorComment }));
      const complete = items.length > 0 && items.every(answer => answer.superiorPoint != null);
      return of(assessment({ items, superiorDraftSaved: true, canCompleteReview: complete, reviewBlockers: complete ? [] : ['Select a Superior Assessment Point for Sales'] }));
    });
    api.completeReview.and.returnValue(of(assessment({ status: 'REVIEWED', items: [{ ...item(), superiorPoint: 4, superiorComment: 'Verified' }],
      canSaveSuperiorDraft: false, canCompleteReview: false, reviewBlockers: [], checkpointScore: 87.125,
      reviewedAt: '2028-02-11', reviewedLate: true })));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [KpiAssessmentReviewsComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: KpiAssessmentService, useValue: api }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }
    ] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(KpiAssessmentReviewsComponent); page = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  function drawer() { return document.querySelector('.ant-drawer-open .ant-drawer-body')!; }
  it('loads a scoped readonly queue with status, period and employee filters', () => {
    expect(api.reviews).toHaveBeenCalledTimes(1); expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
    expect(page.visibleReviews.length).toBe(1); page.filter = 'REVIEWED'; expect(page.visibleReviews[0].id).toBe(21);
    page.search = 'retail'; expect(page.count('REVIEWED')).toBe(1); page.search = 'other'; expect(page.visibleReviews).toEqual([]);
    page.search = ''; page.periodId = 2; expect(page.visibleReviews).toEqual([]);
  });
  it('separates the assessment guidance from its three review-progress buttons', () => {
    const intro: HTMLElement = fixture.nativeElement.querySelector('.team-review-intro');
    expect(intro.querySelector('.help')?.textContent).toContain('KPI_ASSESSMENT_REVIEW.HELP');
    expect(getComputedStyle(intro).marginBottom).toBe('20px');
    expect(page.statuses).toEqual(['PENDING_REVIEW', 'DRAFT', 'REVIEWED']);
    expect(fixture.nativeElement.querySelectorAll('.review-status-tab').length).toBe(3);
  });
  it('shows the employee answers read-only and a separate five-point Superior input', () => {
    page.open(20); fixture.detectChanges();
    expect(drawer().querySelector('.self-answers')!.textContent).toContain('Employee evidence');
    expect(drawer().querySelector('.self-answers input')).toBeNull(); expect(drawer().querySelector('.self-answers textarea')).toBeNull();
    expect(drawer().querySelectorAll('.superior-answers input[type=radio]').length).toBe(5);
    expect(drawer().querySelector('input[type=file]')).toBeNull();
    expect(drawer().textContent).not.toContain('DEPARTMENT_REVIEW.RETURN');
  });
  it('uses spacious, separated answers, larger ratings and compact attachment rows without a global guide', () => {
    page.open(20); fixture.detectChanges();
    const body = drawer();
    expect(body.querySelector('app-kpi-scoring-guide')).toBeNull();
    expect(body.querySelector('.review-criteria-action')?.classList.contains('ant-btn-link')).toBeTrue();
    expect(body.querySelector('.review-kpi-heading h3')?.textContent?.trim()).toBe('Sales');
    expect(getComputedStyle(body.querySelector('.review-assessment-card')!).marginBottom).toBe('28px');
    expect(getComputedStyle(body.querySelector('.card-body')!).paddingTop).toBe('24px');
    expect(getComputedStyle(body.querySelector('.point-option span')!).width).toBe('40px');
    expect(getComputedStyle(body.querySelector('.point-option span')!).height).toBe('40px');
    expect(getComputedStyle(body.querySelector('.superior-answers')!).borderLeftWidth).toBe('1px');
    const attachment = body.querySelector('.review-evidence-row')!;
    expect(getComputedStyle(attachment).display).toBe('flex');
    expect(attachment.querySelector('.evidence-name')?.textContent).toBe('proof.pdf');
    expect(attachment.querySelectorAll('button').length).toBe(1);
    expect(getComputedStyle(body.querySelector('.assessment-review-summary')!).paddingTop).toBe('12px');
  });
  it('edits only Superior answers through the rendered controls', async () => {
    page.open(20); fixture.detectChanges(); await fixture.whenStable();
    const input = drawer().querySelectorAll<HTMLInputElement>('.superior-answers input')[3]; input.click(); fixture.detectChanges();
    expect(page.items[0].superiorPoint).toBe(4); expect(page.items[0].selfPoint).toBe(5);
    expect(page.dirty).toBeTrue(); expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
  });
  it('opens the existing readonly KPI editor with all scoring definitions', () => {
    page.open(20); page.viewCriteria(page.items[0]); fixture.detectChanges();
    expect(page.detailItem[0].kra).toBe('Growth'); expect(page.detailItem[0].scoringDefinitions[5]).toBe('Excellent');
    expect(document.querySelectorAll('.criterion-value').length).toBe(5);
    expect(page.criteriaVisible).toBeTrue(); expect(page.canComplete).toBeFalse();
  });
  it('saves an incomplete Superior Draft by item ID, leaving the Self answers unchanged', () => {
    page.open(20); page.items[0].superiorComment = 'Review in progress'; page.saveDraft();
    expect(api.saveSuperiorDraft).toHaveBeenCalledOnceWith(20, { items: [{ itemId: 44, superiorPoint: null, superiorComment: 'Review in progress' }] });
    expect(page.selected?.status).toBe('PENDING_REVIEW'); expect(page.items[0].selfComment).toBe('Employee evidence');
    expect(page.selected?.checkpointScore).toBeNull(); expect(page.dirty).toBeFalse(); expect(api.completeReview).not.toHaveBeenCalled();
    expect(page.reviewProgress(page.selected!)).toBe('DRAFT');
    expect(page.count('PENDING_REVIEW')).toBe(0); expect(page.count('DRAFT')).toBe(1);
  });
  it('keeps opening and unsaved edits Pending Review until a successful save, even if answers are empty', () => {
    page.open(20); expect(page.reviewProgress(page.selected!)).toBe('PENDING_REVIEW');
    page.items[0].superiorPoint = 4; expect(page.reviewProgress(page.selected!)).toBe('PENDING_REVIEW');
    page.items[0].superiorPoint = null; page.saveDraft();
    expect(page.reviewProgress(page.selected!)).toBe('DRAFT'); expect(page.canComplete).toBeFalse();
    expect(page.selected?.status).toBe('PENDING_REVIEW'); expect(page.readonly).toBeFalse();
    fixture.detectChanges(); expect(drawer().querySelector('.assessment-review-summary .status')?.textContent).toContain('MY_ASSESSMENTS.STATUS.DRAFT');
  });
  it('restores saved Draft progress from the API after reload and filters it separately from untouched submissions', () => {
    api.reviews.and.returnValue(of([{ ...row(), superiorDraftSaved: true }, row(22), row(21, 'REVIEWED')]));
    page.load(); expect(page.count('DRAFT')).toBe(1); expect(page.count('PENDING_REVIEW')).toBe(1);
    page.filter = 'DRAFT'; expect(page.visibleReviews[0].id).toBe(20);
    api.get.and.returnValue(of(assessment({ superiorDraftSaved: true }))); page.open(20);
    expect(page.reviewProgress(page.selected!)).toBe('DRAFT'); expect(page.readonly).toBeFalse();
    page.close(); page.load(); expect(page.count('DRAFT')).toBe(1);
    page.filter = 'REVIEWED'; expect(page.visibleReviews[0].id).toBe(21);
    expect(page.reviewProgress(assessment({ status: 'REVIEWED', superiorDraftSaved: true }))).toBe('REVIEWED');
  });
  it('blocks completion until all current Superior points are complete', () => {
    page.open(20); expect(page.canComplete).toBeFalse(); page.confirmComplete(); expect(modal.confirm).not.toHaveBeenCalled();
    expect(page.itemError(page.items[0])).toBe('MY_ASSESSMENTS.POINT_REQUIRED');
    page.items[0].superiorPoint = 4; expect(page.canComplete).toBeTrue(); expect(page.completionReasons).toEqual([]);
  });
  it('rejects fractional/out-of-range points and oversized comments, not incomplete Drafts', () => {
    page.open(20);
    for (const point of [0, 6, 3.5, NaN]) { page.items[0].superiorPoint = point; page.saveDraft(); expect(page.canComplete).toBeFalse(); }
    expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
    page.items[0].superiorPoint = 4; page.items[0].superiorComment = 'x'.repeat(10001); page.saveDraft();
    expect(api.saveSuperiorDraft).not.toHaveBeenCalled();
    expect(superiorAssessmentItemError({ ...item(), superiorComment: 'x'.repeat(10001) })).toBe('MY_ASSESSMENTS.COMMENT_TOO_LONG');
    expect(superiorAssessmentItemError(item())).toBeNull();
  });
  it('persists current points before confirmed completion and uses only the returned official score', () => {
    page.open(20); page.items[0].superiorPoint = 4; page.confirmComplete(); expect(api.completeReview).not.toHaveBeenCalled(); confirm();
    expect(api.saveSuperiorDraft).toHaveBeenCalledTimes(1); expect(api.completeReview).toHaveBeenCalledOnceWith(20);
    expect(api.saveSuperiorDraft).toHaveBeenCalledBefore(api.completeReview);
    expect(page.selected?.checkpointScore).toBe(87.125); expect(page.readonly).toBeTrue(); expect(page.count('PENDING_REVIEW')).toBe(0);
    fixture.detectChanges(); expect(drawer().querySelectorAll('.point-option').length).toBe(0);
    expect(drawer().querySelector('.checkpoint-score')?.textContent).toContain('87.13%'); page.complete(); expect(api.completeReview).toHaveBeenCalledTimes(1);
  });
  it('formats whole-number checkpoint scores as percentages with two decimals', () => {
    api.get.and.returnValue(of(assessment({ status: 'REVIEWED', canSaveSuperiorDraft: false, checkpointScore: 73 })));
    page.open(20); fixture.detectChanges();
    expect(drawer().querySelector('.checkpoint-score')?.textContent).toContain('73.00%');
    expect(page.selected?.checkpointScore).toBe(73);
  });
  it('rechecks backend completion readiness after saving rather than ignoring new blockers', () => {
    page.open(20); page.items[0].superiorPoint = 4;
    api.saveSuperiorDraft.and.returnValue(of(assessment({ items: [{ ...item(), superiorPoint: 4 }], canCompleteReview: false,
      reviewBlockers: ['The period has changed'] })));
    page.complete(); expect(api.completeReview).not.toHaveBeenCalled(); expect(page.error).toContain('The period has changed'); expect(page.busy).toBeFalse();
  });
  it('keeps Reviewed, Closed and backend-ineligible assessments read-only', () => {
    for (const details of [assessment({ status: 'REVIEWED', canSaveSuperiorDraft: false }), assessment({ reviewPeriodStatus: 'CLOSED' }),
      assessment({ canSaveSuperiorDraft: false })]) {
      api.get.and.returnValue(of(details)); page.open(20); expect(page.readonly).toBeTrue(); page.saveDraft(); page.complete(); page.close();
    }
    expect(api.saveSuperiorDraft).not.toHaveBeenCalled(); expect(api.completeReview).not.toHaveBeenCalled();
  });
  it('allows overdue review in an Open period without turning overdue into a lifecycle status', () => {
    api.get.and.returnValue(of(assessment({ superiorOverdue: true }))); page.open(20); page.items[0].superiorPoint = 5;
    expect(page.canComplete).toBeTrue(); fixture.detectChanges(); expect(drawer().textContent).toContain('KPI_ASSESSMENT_REVIEW.LATE_HELP');
    page.complete(); expect(api.completeReview).toHaveBeenCalled(); expect(page.selected?.reviewedLate).toBeTrue();
  });
  it('preserves input on a failed save and blocks further decisions after lost access', () => {
    page.open(20); page.items[0].superiorPoint = 4;
    api.saveSuperiorDraft.and.returnValue(throwError(() => ({ error: { message: 'Please retry' } }))); page.saveDraft();
    expect(page.items[0].superiorPoint).toBe(4); expect(page.dirty).toBeTrue(); expect(page.error).toBe('Please retry');
    expect(page.reviewProgress(page.selected!)).toBe('PENDING_REVIEW'); expect(page.count('DRAFT')).toBe(0);
    api.saveSuperiorDraft.and.returnValue(throwError(() => ({ status: 403 }))); page.saveDraft();
    expect(page.readonly).toBeTrue(); expect(page.dirty).toBeTrue(); expect(page.error).toBe('KPI_ASSESSMENT_REVIEW.ACCESS_CHANGED');
    page.complete(); expect(api.completeReview).not.toHaveBeenCalled();
  });
  it('keeps successfully saved progress if final completion fails', () => {
    page.open(20); page.items[0].superiorPoint = 4;
    api.completeReview.and.returnValue(throwError(() => ({ error: { message: 'Already reviewed' } }))); page.complete();
    expect(page.items[0].superiorPoint).toBe(4); expect(page.dirty).toBeFalse(); expect(page.error).toBe('Already reviewed');
  });
  it('prevents duplicate saves and completion while an operation is in flight', () => {
    const operation = new Subject<KpiAssessment>(); api.saveSuperiorDraft.and.returnValue(operation);
    page.open(20); page.items[0].superiorPoint = 4; page.saveDraft(); page.saveDraft(); page.complete();
    expect(api.saveSuperiorDraft).toHaveBeenCalledTimes(1); expect(api.completeReview).not.toHaveBeenCalled();
    expect(page.canLeave()).toBeFalse(); operation.next(assessment()); operation.complete(); expect(page.busy).toBeFalse();
  });
  it('confirms discarding unsaved answers on drawer close and route navigation', async () => {
    page.open(20); page.items[0].superiorPoint = 4; page.close(); expect(page.drawerVisible).toBeTrue();
    const leaving = page.canLeave(); confirm(); expect(await leaving).toBeTrue();
    page.close(); confirm(); expect(page.drawerVisible).toBeFalse(); expect(page.dirty).toBeFalse();
  });
  it('downloads employee evidence through the private assessment API without editing it', () => {
    page.open(20); api.download.and.returnValue(of(new Blob(['proof'])));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:proof'); spyOn(HTMLAnchorElement.prototype, 'click');
    page.downloadEvidence(page.items[0].evidence[0]); expect(api.download).toHaveBeenCalledOnceWith(8);
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled(); expect(page.items[0].evidence.length).toBe(1);
  });
  it('formats Monthly, Quarterly and Annual checkpoint labels without simulated dates', () => {
    expect(page.checkpointLabel(checkpoint)).toBe('January 2028');
    expect(page.checkpointLabel({ ...checkpoint, reviewFrequency: 'QUARTERLY' })).toBe('MY_ASSESSMENTS.QUARTER');
    expect(page.checkpointLabel({ ...checkpoint, reviewFrequency: 'ANNUALLY' })).toBe('MY_ASSESSMENTS.ANNUAL');
  });
  it('shows meaningful empty states and allows retry after a failed queue request', () => {
    api.reviews.and.returnValue(throwError(() => ({ status: 403 }))); page.load(); expect(page.error).toBe('KPI_ASSESSMENT_REVIEW.ACCESS_CHANGED');
    api.reviews.and.returnValue(of([])); page.load(); fixture.detectChanges();
    expect(page.error).toBe(''); expect(fixture.nativeElement.textContent).toContain('KPI_ASSESSMENT_REVIEW.EMPTY.PENDING_REVIEW');
  });
});
