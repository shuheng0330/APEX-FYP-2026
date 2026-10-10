import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AttitudeAssessment, AttitudeAssessmentItem } from '../../models/attitude-assessment.model';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';
import { AttitudeSelfAssessmentComponent } from './attitude-self-assessment.component';

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
  it('uses each returned format and all shared/specific criteria without inferring a format from the role', () => {
    for (const evaluationFormat of ['MANAGER', 'SALES', 'OTHERS'] as const) {
      api.mine.and.returnValue(of(assessment({ evaluationFormat, items: [row(), row(8)] }))); page.load(); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.level-pill').textContent).toContain('ATTITUDE_SETUP.FORMAT.' + evaluationFormat);
      expect(fixture.nativeElement.querySelectorAll('.attitude-criterion').length).toBe(2);
    }
  });
  it('uses bound rating labels/definitions in the selected answer and reusable scoring guide', () => {
    page.items[0].selfPoint = 3; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.selected-rating').textContent).toContain('Configured rating 3');
    expect(fixture.nativeElement.querySelector('.selected-rating').textContent).toContain('Configured behaviour 3');
    const guide = fixture.debugElement.query(By.directive(KpiScoringGuideComponent)).componentInstance as KpiScoringGuideComponent;
    expect(guide.ratings).toEqual(page.assessment!.ratingDefinitions); expect(guide.titleKey).toBe('ATTITUDE_ASSESSMENT.GUIDE_TITLE');
    expect(fixture.nativeElement.textContent).not.toContain('KPI_SCORING_GUIDE.RATING');
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
