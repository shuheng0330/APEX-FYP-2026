import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AttitudeAssessment, AttitudeAssessmentItem } from '../../models/attitude-assessment.model';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';
import { AttitudeSelfAssessmentComponent } from './attitude-self-assessment.component';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

describe('Annual Attitude Self-Assessment', () => {
  let fixture: ComponentFixture<AttitudeSelfAssessmentComponent>;
  let page: AttitudeSelfAssessmentComponent;
  let api: jasmine.SpyObj<AttitudeAssessmentService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const row = (criterionId = 7): AttitudeAssessmentItem => ({ id: null, criterionId, name: criterionId === 7 ? 'Respect' : 'Leadership',
    description: 'Demonstrate expected behaviour', criterionType: criterionId === 7 ? 'SHARED_CORE_VALUE' : 'FORMAT_SPECIFIC',
    displayOrder: criterionId, selfPoint: null, selfComment: null });
  const assessment = (overrides: Partial<AttitudeAssessment> = {}): AttitudeAssessment => ({ id: null, participantId: 3,
    employeeName: 'Alice', roleName: 'Sales Manager', departmentName: 'Sales', reviewPeriodId: 1, reviewPeriodName: '2026 Annual Review',
    reviewPeriodStatus: 'OPEN', configurationId: 5, configurationName: 'Preserved 2026 edition', evaluationFormat: 'MANAGER', status: 'DRAFT',
    createdAt: null, updatedAt: null, submittedAt: null, submittedBy: null, submittedToSuperiorId: null, submittedLate: null,
    availableFrom: '2026-10-01', openingPending: false,
    selfAssessmentDeadline: '2026-12-20', superiorEvaluationDeadline: '2026-12-27', available: true, availabilityTitle: null, availabilityMessage: null,
    canSaveDraft: true, canSubmit: false, overdue: false, submissionBlockers: ['Select a Self-Assessment Point for Respect'],
    ratingDefinitions: [1, 2, 3, 4, 5].map(point => ({ point, label: `Configured rating ${point}`, description: `Configured behaviour ${point}` })),
    items: [row()], ...overrides });
  beforeEach(async () => {
    api = jasmine.createSpyObj<AttitudeAssessmentService>('api', ['mine', 'create', 'update', 'submit']);
    api.mine.and.returnValue(of(assessment()));
    const save = (request: Parameters<AttitudeAssessmentService['create']>[0]) => of(assessment({ id: 20,
      items: request.items.map(answer => ({ ...row(answer.criterionId), ...answer })),
      submissionBlockers: request.items.some(item => item.selfPoint === null) ? ['Select a Self-Assessment Point for Respect'] : [],
      canSubmit: request.items.length > 0 && request.items.every(item => item.selfPoint !== null) }));
    api.create.and.callFake(save); api.update.and.callFake((_id, request) => save(request));
    api.submit.and.returnValue(of(assessment({ id: 20, status: 'PENDING_REVIEW', canSaveDraft: false, canSubmit: false,
      items: [{ ...row(), selfPoint: 4, selfComment: 'Reflection' }], submittedAt: '2026-12-21T10:00:00Z', submittedLate: true })));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [AttitudeSelfAssessmentComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: AttitudeAssessmentService, useValue: api },
        { provide: NzModalService, useValue: modal }, { provide: NZ_ICONS, useValue: [CloseOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(AttitudeSelfAssessmentComponent); page = fixture.componentInstance;
    fixture.componentRef.setInput('reviewPeriodId', 1); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('loads annual criteria and recorded context without a checkpoint or write', () => {
    expect(api.mine).toHaveBeenCalledOnceWith(1); expect(api.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Alice · Sales Manager · Sales');
    expect(fixture.nativeElement.textContent).toContain('2026 Annual Review');
    expect(fixture.nativeElement.textContent).toContain('20 Dec 2026');
    expect(fixture.nativeElement.querySelectorAll('.point-option').length).toBe(5);
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeNull();
  });
  it('shows completed Superior ratings, employee feedback and the stored score with equal-weight contributions', () => {
    api.mine.and.returnValue(of(assessment({ id: 20, status: 'REVIEWED', canSaveDraft: false, canSubmit: false,
      attitudeScore: 70, reviewedAt: '2026-10-20T02:00:00Z',
      items: [{ ...row(7), selfPoint: 5, selfComment: 'My reflection', superiorPoint: 4, superiorComment: 'Respectful collaboration' },
        { ...row(8), selfPoint: 4, superiorPoint: 3, superiorComment: 'Build consistent initiative' }] })));
    page.load(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.attitude-results').textContent).toContain('70.00 / 100');
    expect(fixture.nativeElement.querySelectorAll('.submitted-superior-feedback').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Respectful collaboration'); expect(fixture.nativeElement.textContent).toContain('My reflection');
    expect(page.contribution(page.items[0])).toBe(40); expect(page.contribution(page.items[1])).toBe(30);
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull(); expect(fixture.nativeElement.querySelector('.assessment-actions')).toBeNull();
    expect(fixture.nativeElement.querySelector('.assessment-progress')).toBeNull();
  });
  it('never renders Superior drafts, comments or official results before review completion', () => {
    api.mine.and.returnValue(of(assessment({ id: 20, status: 'PENDING_REVIEW', canSaveDraft: false,
      items: [{ ...row(), selfPoint: 4, superiorPoint: 5, superiorComment: 'Unfinished private draft' }], attitudeScore: 100 })));
    page.load(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.attitude-results')).toBeNull();
    expect(fixture.nativeElement.querySelector('.submitted-superior-feedback')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Unfinished private draft');
  });
  it('shows the scheduled opening clearly and prevents editing or writes before it opens', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { ATTITUDE_ASSESSMENT: { OPENS_ON: 'Attitude Self-Assessment Opens on {{date}}',
      OPENING_HELP: 'You can complete your annual Attitude Self-Assessment from this date.' } }); translate.use('en');
    api.mine.and.returnValue(of(assessment({ availableFrom: '2099-10-01', openingPending: true, available: false,
      canSaveDraft: false, canSubmit: false, availabilityMessage: 'Attitude Self-Assessment opens on 2099-10-01.' })));
    page.load(); fixture.detectChanges();
    const notice: HTMLElement = fixture.nativeElement.querySelector('.unavailable-assessment');
    expect(notice.querySelector('h3')?.textContent).toBe('Attitude Self-Assessment Opens on 01 Oct 2099');
    expect(notice.textContent).toContain('You can complete your annual Attitude Self-Assessment from this date.');
    expect(fixture.nativeElement.textContent).toContain('2026 Annual Review');
    expect(fixture.nativeElement.textContent).toContain('20 Dec 2026');
    expect(fixture.nativeElement.querySelector('.attitude-criteria')).toBeNull();
    expect(fixture.nativeElement.querySelector('.assessment-actions')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-status=DRAFT]')).toBeNull();
    page.saveDraft(); page.confirmSubmit(); page.submit();
    expect(api.create).not.toHaveBeenCalled(); expect(api.update).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
    expect(modal.confirm).not.toHaveBeenCalled();
  });
  it('refreshes the scheduled notice into the editable assessment using server availability rather than the browser date', () => {
    api.mine.and.returnValue(of(assessment({ availableFrom: '2099-10-01', openingPending: true, available: false,
      canSaveDraft: false, availabilityMessage: 'Scheduled opening' })));
    page.load(); fixture.detectChanges();
    api.mine.and.returnValue(of(assessment({ availableFrom: '2099-10-01', openingPending: false, available: true })));
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.opening-refresh'); button.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.unavailable-assessment')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.point-option').length).toBe(5);
    expect(fixture.nativeElement.querySelector('.assessment-actions')).not.toBeNull();
    expect(page.readonly).toBeFalse(); expect(api.create).not.toHaveBeenCalled();
  });
  it('keeps the primary configuration message instead of inferring opening status from a future date', () => {
    api.mine.and.returnValue(of(assessment({ availableFrom: '2099-10-01', openingPending: false, available: false,
      canSaveDraft: false, items: [], availabilityTitle: 'Attitude Evaluation Not Yet Available',
      availabilityMessage: 'The Attitude Evaluation criteria have not been configured for this Annual Review Period. Please check again later.' })));
    page.load(); fixture.detectChanges();
    const notice: HTMLElement = fixture.nativeElement.querySelector('.unavailable-assessment');
    expect(notice.querySelector('h3')?.textContent).toBe('Attitude Evaluation Not Yet Available');
    expect(notice.textContent).toContain('criteria have not been configured');
    expect(notice.textContent).not.toContain('2099');
  });
  it('uses each returned format and all shared/specific criteria without inferring a format from the role', () => {
    for (const evaluationFormat of ['MANAGER', 'SALES', 'OTHERS'] as const) {
      api.mine.and.returnValue(of(assessment({ evaluationFormat, items: [row(), row(8)] }))); page.load(); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.level-pill').textContent).toContain('ATTITUDE_SETUP.FORMAT.' + evaluationFormat);
      expect(fixture.nativeElement.querySelectorAll('.attitude-criterion').length).toBe(2);
    }
  });
  it('shows only the configured label below the points and retains full definitions in the scoring guide', () => {
    page.items[0].selfPoint = 3; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selected-rating').textContent).toContain('Configured rating 3');
    const label: HTMLElement = fixture.nativeElement.querySelector('.criterion-rating .selected-rating');
    expect(label.previousElementSibling?.classList.contains('point-selector')).toBeTrue();
    expect(label.textContent?.trim()).toBe('Configured rating 3');
    expect(fixture.nativeElement.textContent).not.toContain('Configured behaviour 3');
    expect(fixture.nativeElement.querySelector('.criterion-description').textContent).toBe('Demonstrate expected behaviour');
    const guide = fixture.debugElement.query(By.directive(KpiScoringGuideComponent)).componentInstance as KpiScoringGuideComponent;
    expect(guide.ratings).toEqual(page.assessment!.ratingDefinitions); expect(guide.titleKey).toBe('ATTITUDE_ASSESSMENT.GUIDE_TITLE');
    expect(fixture.nativeElement.textContent).not.toContain('KPI_SCORING_GUIDE.RATING');
    expect(guide.orderedRatings.find(rating => rating.point === 3)?.description).toBe('Configured behaviour 3');
    expect(api.create).not.toHaveBeenCalled(); expect(api.update).not.toHaveBeenCalled();
  });
  it('wraps long configured labels beneath the points at desktop, tablet and mobile widths', () => {
    page.items[0].selfPoint = 3;
    page.assessment!.ratingDefinitions[2].label = 'Long configured rating label '.repeat(8); fixture.detectChanges();
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(fixture.nativeElement.querySelector('.attitude-criteria'), width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(layout.window.getComputedStyle(layout.root.querySelector('.selected-rating')!).overflowWrap).toBe('anywhere');
      } finally { layout.remove(); }
    }
  });
  it('binds radio selections and shows criterion completion progress, not a performance score', async () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelectorAll('input[type=radio]')[2];
    input.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(page.items[0].selfPoint).toBe(3); expect(page.completed).toBe(1); expect(page.progress).toBe(100);
    expect(fixture.nativeElement.querySelector('[role=progressbar]').getAttribute('aria-valuenow')).toBe('1');
    expect(page.canSubmit).toBeTrue();
  });
  it('saves incomplete Drafts and only then marks missing required points inline', () => {
    expect(fixture.nativeElement.querySelector('#attitude-point-error-7')).toBeNull();
    page.saveDraft(); fixture.detectChanges(); expect(api.create).toHaveBeenCalledWith({ reviewPeriodId: 1,
      items: [{ criterionId: 7, selfPoint: null, selfComment: null }] });
    expect(fixture.nativeElement.querySelector('#attitude-point-error-7').textContent).toContain('MY_ASSESSMENTS.POINT_REQUIRED');
    expect(page.canSubmit).toBeFalse(); page.items[0].selfPoint = 2; page.saveDraft(); expect(api.update).toHaveBeenCalled();
  });
  it('validates invalid supplied points and comments without making comments mandatory', () => {
    for (const point of [0, 6, 1.5, NaN]) { page.items[0].selfPoint = point; page.saveDraft(); }
    page.items[0].selfPoint = null; page.items[0].selfComment = 'x'.repeat(10001); page.saveDraft(); fixture.detectChanges();
    expect(api.create).not.toHaveBeenCalled(); expect(fixture.nativeElement.querySelector('textarea[aria-invalid=true]')).not.toBeNull();
    page.items[0].selfComment = null; page.items[0].selfPoint = 5; expect(page.canSubmit).toBeTrue();
  });
  it('confirms, persists current answers first, submits, then makes Pending Review read-only', () => {
    page.items[0].selfPoint = 4; page.confirmSubmit(); expect(api.submit).not.toHaveBeenCalled(); confirm(); fixture.detectChanges();
    expect(api.create).toHaveBeenCalled(); expect(api.submit).toHaveBeenCalledOnceWith(20); expect(page.readonly).toBeTrue();
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull(); expect(fixture.nativeElement.querySelector('input[type=radio]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('MY_ASSESSMENTS.STATUS.PENDING_REVIEW');
    expect(fixture.nativeElement.querySelector('.selected-rating').textContent).toContain('Configured rating 4');
    page.saveDraft(); page.submit(); expect(api.submit.calls.count()).toBe(1);
  });
  it('warns about a late Attitude submission and keeps the Draft untouched on cancellation', () => {
    page.assessment!.overdue = true; page.items[0].selfPoint = 4; page.items[0].selfComment = 'Keep reflection';
    page.confirmSubmit(); const options = modal.confirm.calls.mostRecent().args[0]!;
    expect(options.nzOkText).toBe('REVIEW_FEEDBACK.SUBMIT_LATE');
    expect(options.nzData).toEqual({ deadline: page.assessment!.selfAssessmentDeadline });
    expect(api.create).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
    expect(page.items[0].selfComment).toBe('Keep reflection');
  });
  it('blocks missing points and structural blockers while allowing a Draft with no Superior', () => {
    page.assessment!.submissionBlockers.push('An active immediate Superior must be assigned before submission');
    page.items[0].selfPoint = 4; expect(page.canSubmit).toBeFalse(); page.confirmSubmit(); expect(modal.confirm).not.toHaveBeenCalled();
    page.saveDraft(); expect(api.create).toHaveBeenCalled();
  });
  it('rechecks server readiness after saving instead of trusting locally complete answers', () => {
    page.items[0].selfPoint = 4; api.create.and.returnValue(of(assessment({ id: 20,
      items: [{ ...row(), selfPoint: 4 }], submissionBlockers: ['Your Superior is no longer available'], canSubmit: false })));
    page.submit(); expect(api.submit).not.toHaveBeenCalled(); expect(page.error).toContain('Superior'); expect(page.items[0].selfPoint).toBe(4);
  });
  it('keeps unsaved input and permits retry after a failed save', () => {
    page.items[0].selfPoint = 3; page.items[0].selfComment = 'Keep my reflection';
    api.create.and.returnValue(throwError(() => ({ error: { message: 'Connection failed' } }))); page.submit();
    expect(page.items[0].selfComment).toBe('Keep my reflection'); expect(page.dirty).toBeTrue(); expect(page.busy).toBeFalse();
    expect(api.submit).not.toHaveBeenCalled();
  });
  it('distinguishes current overdue from stored submission lateness and still permits late submission', () => {
    page.assessment!.overdue = true; page.items[0].selfPoint = 5; fixture.detectChanges();
    expect(page.canSubmit).toBeTrue(); expect(fixture.nativeElement.textContent).toContain('MY_ASSESSMENTS.LATE_HELP');
    page.submit(); fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('MY_ASSESSMENTS.SUBMITTED_LATE');
    expect(fixture.nativeElement.textContent).not.toContain('MY_ASSESSMENTS.LATE_HELP');
  });
  it('displays unavailable configuration/mapping/opening messages without exposing setup controls or fake status', () => {
    api.mine.and.returnValue(of(assessment({ available: false, canSaveDraft: false, status: null, items: [], evaluationFormat: null,
      ratingDefinitions: [], availabilityTitle: 'Attitude Evaluation Not Yet Available', availabilityMessage: 'Please check again later.' })));
    page.load(); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('.unavailable-assessment').textContent).toContain('Please check again later.');
    expect(fixture.nativeElement.querySelector('.status')).toBeNull(); expect(fixture.nativeElement.querySelector('app-kpi-scoring-guide')).toBeNull();
    page.saveDraft(); page.submit(); expect(api.create).not.toHaveBeenCalled();
  });
  it('honours backend Upcoming/Closed/Reviewed flags and retains saved criteria read-only', () => {
    for (const reviewPeriodStatus of ['UPCOMING', 'CLOSED'] as const) {
      api.mine.and.returnValue(of(assessment({ reviewPeriodStatus, available: false, canSaveDraft: false })));
      page.load(); fixture.detectChanges(); expect(page.readonly).toBeTrue(); page.saveDraft(); page.submit();
      expect(fixture.nativeElement.querySelectorAll('.attitude-criterion').length).toBe(1);
    }
    api.mine.and.returnValue(of(assessment({ status: 'REVIEWED', canSaveDraft: false }))); page.load(); expect(page.readonly).toBeTrue();
    expect(api.create).not.toHaveBeenCalled();
  });
  it('prevents duplicate actions while saving', () => {
    const pending = new Subject<AttitudeAssessment>(); api.create.and.returnValue(pending); page.items[0].selfPoint = 3;
    page.saveDraft(); page.saveDraft(); page.submit(); expect(api.create.calls.count()).toBe(1);
    pending.next(assessment({ id: 20 })); pending.complete(); expect(page.busy).toBeFalse();
  });
  it('refreshes without losing Draft input but accepts frozen server answers after external submission', () => {
    page.items[0].selfPoint = 3; page.items[0].selfComment = 'Keep'; page.refresh(); expect(page.items[0].selfComment).toBe('Keep');
    api.mine.and.returnValue(of(assessment({ id: 20, status: 'PENDING_REVIEW', canSaveDraft: false, items: [{ ...row(), selfPoint: 5 }] })));
    page.refresh(); expect(page.items[0].selfPoint).toBe(5); expect(page.dirty).toBeFalse();
  });
  it('loads the newly selected annual period without keeping the preceding period answers', async () => {
    page.items[0].selfPoint = 3; fixture.componentRef.setInput('reviewPeriodId', 2); fixture.detectChanges();
    await fixture.whenStable(); fixture.detectChanges();
    expect(api.mine).toHaveBeenCalledWith(2); expect(page.items[0].selfPoint).toBeNull(); expect(page.dirty).toBeFalse();
  });
  it('shows permission changes and recovers from a failed GET without creating records', () => {
    api.mine.and.returnValue(throwError(() => ({ status: 403 }))); page.load(); fixture.detectChanges();
    expect(page.error).toBe('MY_ASSESSMENTS.ACCESS_CHANGED'); expect(page.assessment).toBeNull();
    api.mine.and.returnValue(of(assessment())); page.refresh(); expect(page.assessment).not.toBeNull(); expect(api.create).not.toHaveBeenCalled();
  });
});
