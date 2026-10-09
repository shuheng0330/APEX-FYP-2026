import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { NzSelectComponent } from 'ng-zorro-antd/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { MyAssessmentsComponent } from './my-assessments.component';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentItem, assessmentItemError } from '../../models/kpi-assessment.model';
import { emptyKpiItem, KpiPeriodContext } from '../../models/kpi-plan.model';

describe('My Assessments - KPI Self-Assessment', () => {
  let fixture: ComponentFixture<MyAssessmentsComponent>;
  let page: MyAssessmentsComponent;
  let api: jasmine.SpyObj<KpiAssessmentService>;
  let modal: jasmine.SpyObj<NzModalService>;
  const period: KpiPeriodContext = { id: 1, name: '2028 Annual Review', status: 'OPEN', startDate: '2028-01-01', endDate: '2028-12-31', kpiSetupDeadline: '2028-01-31' };
  const checkpoint = (id = 11): KpiAssessmentCheckpoint => ({ id, reviewFrequency: 'MONTHLY', sequenceNumber: 1,
    startDate: '2028-01-01', endDate: '2028-01-31', availableFrom: '2028-02-01', selfAssessmentDeadline: '2028-02-05',
    superiorAssessmentDeadline: '2028-02-10', available: true, overdue: false, assessmentId: null, assessmentStatus: null });
  const row = (id = 4, level: KpiAssessmentItem['level'] = 'INDIVIDUAL'): KpiAssessmentItem => ({ id: 40 + id, assignmentId: id, level,
    kpi: { ...emptyKpiItem(), name: 'Sales ' + id, target: 'RM 80,000', weightage: 100,
      perspective: 'Financial', kra: 'Sales', scoringDefinitions: { 1: 'Low', 2: 'Below target', 3: 'On target', 4: 'Above target', 5: 'Excellent' } },
    selfPoint: null, selfComment: null, superiorPoint: null, superiorComment: null, evidence: [] });
  const assessment = (overrides: Partial<KpiAssessment> = {}): KpiAssessment => ({ id: null, participantId: 7, employeeName: 'Amir', reviewPeriodId: 1,
    reviewPeriodName: '2028 Annual Review', reviewPeriodStatus: 'OPEN', checkpoint: checkpoint(), status: 'DRAFT',
    items: [row()], kpiAllocation: null, missingLevels: [], submissionBlockers: ['Select a Self-Assessment Point for Sales 4'],
    canSaveDraft: true, canSubmit: false, overdue: false, createdAt: null, updatedAt: null, submittedAt: null,
    submittedBy: null, submittedToSuperiorId: null, submittedLate: null, reviewedAt: null, reviewedBy: null, reviewedLate: null, checkpointScore: null, ...overrides });
  beforeEach(async () => {
    api = jasmine.createSpyObj<KpiAssessmentService>('api', ['periods', 'checkpoints', 'mine', 'create', 'update', 'submit', 'upload', 'download', 'removeEvidence']);
    api.periods.and.returnValue(of([period])); api.checkpoints.and.returnValue(of([checkpoint()])); api.mine.and.returnValue(of(assessment()));
    const save = (request: Parameters<KpiAssessmentService['create']>[0]) => of(assessment({ id: 20,
      items: request.items.map(answer => ({ ...row(answer.assignmentId), ...answer })),
      submissionBlockers: request.items.some(answer => answer.selfPoint == null) ? ['Select a Self-Assessment Point for Sales 4'] : [],
      canSubmit: request.items.length > 0 && request.items.every(answer => answer.selfPoint != null) }));
    api.create.and.callFake(save); api.update.and.callFake((_id, request) => save(request));
    api.submit.and.returnValue(of(assessment({ id: 20, status: 'PENDING_REVIEW', items: [{ ...row(), selfPoint: 4 }],
      canSaveDraft: false, canSubmit: false, submittedAt: '2028-02-01T12:00:00Z', submissionBlockers: ['This assessment has already been submitted'] })));
    modal = jasmine.createSpyObj<NzModalService>('modal', ['confirm']);
    TestBed.configureTestingModule({ imports: [MyAssessmentsComponent, TranslateModule.forRoot()], providers: [provideNoopAnimations(),
      { provide: KpiAssessmentService, useValue: api }, { provide: NzModalService, useValue: modal },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LoadingOutline] }] });
    TestBed.overrideProvider(NzModalService, { useValue: modal }); await TestBed.compileComponents();
    fixture = TestBed.createComponent(MyAssessmentsComponent); page = fixture.componentInstance; fixture.detectChanges();
  });
  function confirm() {
    const onOk = modal.confirm.calls.mostRecent().args[0]?.nzOnOk;
    if (onOk && !(onOk instanceof EventEmitter)) onOk(undefined);
  }
  it('loads scoped periods and checkpoints without creating an assessment on GET', () => {
    expect(api.checkpoints).toHaveBeenCalledOnceWith(1); expect(api.mine).toHaveBeenCalledOnceWith(11);
    expect(api.create).not.toHaveBeenCalled(); expect(page.readonly).toBeFalse(); expect(page.canSubmit).toBeFalse();
    expect(fixture.nativeElement.querySelectorAll('.point-option').length).toBe(5);
  });
  it('shows recorded employee context below the tab and above the checkpoint controls', () => {
    page.assessment!.roleName = 'Sales Executive';
    page.assessment!.kpiAllocation = { employeeLevelId: 4, employeeLevelName: 'Executive', companyKpiWeight: 15, departmentKpiWeight: 25, individualKpiWeight: 60 };
    fixture.detectChanges();
    const context: HTMLElement = fixture.nativeElement.querySelector('.assessment-employee');
    expect(context.textContent?.trim()).toBe('Amir · Sales Executive · Executive');
    expect(context.nextElementSibling?.classList.contains('assessment-toolbar')).toBeTrue();
    expect(fixture.nativeElement.querySelector('.assessment-heading').textContent).not.toContain('Amir');
    expect(api.create).not.toHaveBeenCalled();
  });
  it('omits missing context without placeholder text or dangling separators', () => {
    page.assessment!.roleName = null; page.assessment!.kpiAllocation = null; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assessment-employee').textContent.trim()).toBe('Amir');
    page.assessment!.employeeName = ''; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assessment-employee')).toBeNull();
  });
  it('opens the checkpoint dropdown when clicking its visible selected value', async () => {
    await fixture.whenStable(); fixture.detectChanges();
    const select = fixture.debugElement.query(By.css('.checkpoint-picker'));
    select.nativeElement.querySelector('nz-select-item').click(); fixture.detectChanges(); await fixture.whenStable();
    expect((select.componentInstance as NzSelectComponent).nzOpen).toBeTrue();
    expect(select.nativeElement.closest('label')).toBeNull();
  });
  it('opens the period dropdown when clicking its visible selected value', async () => {
    await fixture.whenStable(); fixture.detectChanges();
    const select = fixture.debugElement.query(By.css('.plan-picker'));
    select.nativeElement.querySelector('nz-select-item').click(); fixture.detectChanges(); await fixture.whenStable();
    expect((select.componentInstance as NzSelectComponent).nzOpen).toBeTrue();
    expect(select.nativeElement.closest('label')).toBeNull();
  });
  it('shows a dash for unanswered read-only points without hiding saved points', () => {
    page.assessment = assessment({ canSaveDraft: false }); page.items = [row(), { ...row(5), selfPoint: 4 }]; fixture.detectChanges();
    const points: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.saved-point'));
    expect(points.map(point => point.textContent?.trim())).toEqual(['-', '4']);
  });
  it('shows the matching rating for each selected point without changing KPI criteria', () => {
    const labels = ['Unsatisfactory', 'Below expectation', 'Partially meets expectation', 'Meets expectation', 'Exceeds expectation'];
    const ratings = Object.fromEntries(labels.map((LABEL, index) => [index + 1, { LABEL }]));
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { KPI_SCORING_GUIDE: { RATING: ratings } }); translate.use('en');
    expect(fixture.nativeElement.querySelector('.selected-point-label')).toBeNull();
    const criteria = { ...page.items[0].kpi.scoringDefinitions };
    for (let point = 1; point <= 5; point++) {
      const radio: HTMLInputElement = fixture.nativeElement.querySelectorAll('.point-option input')[point - 1];
      radio.click(); fixture.detectChanges();
      expect(page.items[0].selfPoint).toBe(point);
      expect(fixture.nativeElement.querySelector('.selected-point-label').textContent.trim()).toBe(labels[point - 1]);
    }
    expect(page.items[0].kpi.scoringDefinitions).toEqual(criteria);
    expect(api.create).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
  });
  it('keeps rating labels visible on submitted assessments and omits unanswered labels', () => {
    page.assessment = assessment({ status: 'PENDING_REVIEW', canSaveDraft: false });
    page.items = [row(), { ...row(5), selfPoint: 3 }]; fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.selected-point-label').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.selected-point-label').textContent).toContain('KPI_SCORING_GUIDE.RATING.3.LABEL');
    expect(fixture.nativeElement.querySelector('app-kpi-scoring-guide')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.point-option').length).toBe(0);
  });
  it('labels the checkpoint dropdown using the recorded review frequency', () => {
    const labels = { MONTHLY: 'Monthly Review Checkpoint', QUARTERLY: 'Quarterly Review Checkpoint', ANNUALLY: 'Annual Review Checkpoint' };
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { MY_ASSESSMENTS: { CHECKPOINT_LABEL: labels } }); translate.use('en');
    for (const reviewFrequency of ['MONTHLY', 'QUARTERLY', 'ANNUALLY'] as const) {
      page.assessment!.checkpoint.reviewFrequency = reviewFrequency; fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('label[for="assessment-review-checkpoint"]').textContent.trim()).toBe(labels[reviewFrequency]);
    }
  });
  it('saves incomplete or empty Drafts without requiring points or missing KPI levels', () => {
    page.saveDraft(); expect(api.create.calls.mostRecent().args[0].items[0].selfPoint).toBeNull();
    expect(page.showValidation).toBeFalse();
    page.assessment = assessment({ items: [], missingLevels: ['COMPANY', 'DEPARTMENT'], submissionBlockers: ['Department KPIs are not yet approved'], id: 20 });
    page.items = []; page.saveDraft(); expect(api.update.calls.mostRecent().args[1].items).toEqual([]);
    page.assessment = assessment({ items: [], canSaveDraft: true, submissionBlockers: ['Department KPIs are not yet approved'] });
    expect(page.canSubmit).toBeFalse();
  });
  it('validates 1-5 integer points and optional comment length', () => {
    for (const selfPoint of [0, 6, 1.5, NaN]) { page.items[0].selfPoint = selfPoint; page.saveDraft(); }
    page.items[0].selfPoint = null; page.items[0].selfComment = 'x'.repeat(10001); page.saveDraft();
    expect(api.create).not.toHaveBeenCalled(); expect(assessmentItemError(row(), true)).toBe('MY_ASSESSMENTS.POINT_REQUIRED');
  });
  it('uses current points, confirms, saves first, then submits and freezes answers', () => {
    page.items[0].selfPoint = 4; expect(page.canSubmit).toBeTrue(); page.confirmSubmit(); expect(api.submit).not.toHaveBeenCalled();
    confirm(); expect(api.create).toHaveBeenCalled(); expect(api.submit).toHaveBeenCalledOnceWith(20);
    expect(page.readonly).toBeTrue(); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('.point-selector')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    page.saveDraft(); page.submit(); expect(api.submit.calls.count()).toBe(1);
  });
  it('blocks submission for missing assignments and a missing Superior even with all local points', () => {
    page.items[0].selfPoint = 5; page.assessment!.submissionBlockers.push('Department KPIs are not yet approved', 'An active immediate Superior must be assigned before submission');
    page.confirmSubmit(); expect(page.canSubmit).toBeFalse(); expect(modal.confirm).not.toHaveBeenCalled();
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Department KPIs are not yet approved');
  });
  it('rechecks backend readiness after save and prevents stale submission', () => {
    page.items[0].selfPoint = 4;
    api.create.and.returnValue(of(assessment({ id: 20, items: [{ ...row(), selfPoint: 4 }, row(5, 'COMPANY')],
      submissionBlockers: ['Select a Self-Assessment Point for Sales 5'] })));
    page.submit(); expect(api.submit).not.toHaveBeenCalled(); expect(page.items.length).toBe(2);
    expect(page.error).toContain('Sales 5'); expect(page.items[0].selfPoint).toBe(4);
  });
  it('keeps input when Draft saving fails and does not send a submission', () => {
    page.items[0].selfPoint = 3; page.items[0].selfComment = 'Unsaved observations';
    api.create.and.returnValue(throwError(() => ({ error: { message: 'Connection failed' } })));
    page.submit(); expect(api.submit).not.toHaveBeenCalled(); expect(page.items[0].selfComment).toBe('Unsaved observations');
    expect(page.busy).toBeFalse(); expect(page.dirty).toBeTrue();
  });
  it('opens full read-only KPI details and the five actual scoring definitions', () => {
    page.viewCriteria(page.items[0]); expect(page.drawerVisible).toBeTrue(); expect(page.detailItem[0].scoringDefinitions[5]).toBe('Excellent');
    page.detailItem[0].name = 'Copy'; expect(page.items[0].kpi.name).not.toBe('Copy');
  });
  it('respects backend checkpoint opening/Closed/readonly flags rather than client dates', () => {
    for (const update of [
      { checkpoint: { ...checkpoint(), available: false, availableFrom: '2099-02-01' } },
      { reviewPeriodStatus: 'CLOSED' as const }, { status: 'REVIEWED' as const }
    ]) {
      page.assessment = assessment({ ...update, canSaveDraft: false }); fixture.detectChanges();
      expect(page.readonly).toBeTrue(); page.saveDraft(); page.submit();
    }
    expect(api.create).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
  });
  it('allows overdue Draft submission and distinguishes submitted lateness from current overdue', () => {
    page.assessment!.overdue = true; page.items[0].selfPoint = 5; expect(page.canSubmit).toBeTrue();
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('MY_ASSESSMENTS.LATE_HELP');
    page.assessment = assessment({ status: 'PENDING_REVIEW', canSaveDraft: false, submittedLate: true, overdue: false }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('MY_ASSESSMENTS.SUBMITTED_LATE');
    expect(fixture.nativeElement.textContent).not.toContain('MY_ASSESSMENTS.LATE_HELP');
  });
  it('warns before discarding answers on checkpoint/period changes and protects navigation', async () => {
    page.checkpoints.push(checkpoint(12)); page.items[0].selfPoint = 2; page.changeCheckpoint(12);
    expect(page.checkpointId).toBe(11); confirm(); expect(api.mine).toHaveBeenCalledWith(12);
    page.items[0].selfPoint = 1; const leave = page.canLeave(); confirm(); expect(await leave).toBeTrue();
    page.periods.push({ ...period, id: 2 }); page.changePeriod(2); expect(page.periodId).toBe(1); confirm();
    expect(api.checkpoints).toHaveBeenCalledWith(2);
  });
  it('prevents duplicate actions while a save is pending', () => {
    const pending = new Subject<KpiAssessment>(); api.create.and.returnValue(pending); page.items[0].selfPoint = 4;
    page.saveDraft(); page.saveDraft(); page.submit(); page.changeCheckpoint(11);
    expect(api.create.calls.count()).toBe(1); expect(page.canLeave()).toBeFalse();
    pending.next(assessment({ id: 20 })); pending.complete(); expect(page.busy).toBeFalse();
  });
  it('saves new items before uploading evidence against their saved item ID', () => {
    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' }); const evidence = { id: 8, originalFilename: 'proof.pdf', contentType: 'application/pdf',
      sizeBytes: 5, uploadedBy: 'owner', uploadedAt: '2028-02-01' };
    api.upload.and.returnValue(of(evidence)); page.items[0].id = null; page.items[0].selfPoint = 3;
    const input = document.createElement('input'); const files = new DataTransfer(); files.items.add(file); input.type = 'file'; input.files = files.files;
    page.attachEvidence(4, { target: input } as unknown as Event);
    expect(api.create).toHaveBeenCalled(); expect(api.upload).toHaveBeenCalledOnceWith(44, file);
    expect(page.items[0].evidence[0].id).toBe(8); expect(page.items[0].selfPoint).toBe(3); expect(input.value).toBe('');
  });
  it('preserves the saved Draft on evidence failure and shows backend format/size errors', () => {
    const input = document.createElement('input'); input.type = 'file'; const files = new DataTransfer();
    files.items.add(new File(['bad'], 'wrong.pdf')); input.files = files.files;
    api.upload.and.returnValue(throwError(() => ({ error: { message: 'Attach a valid PDF, PNG or JPEG' } })));
    page.attachEvidence(4, { target: input } as unknown as Event);
    expect(page.assessment?.id).toBe(20); expect(page.items[0].evidence).toEqual([]); expect(page.error).toContain('valid PDF');
  });
  it('requires confirmation to remove evidence and blocks submitted mutations', () => {
    const evidence = { id: 8, originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 5, uploadedBy: 'owner', uploadedAt: '2028-02-01' };
    page.items[0].evidence = [evidence]; api.removeEvidence.and.returnValue(of(undefined));
    page.removeEvidence(page.items[0], evidence); expect(api.removeEvidence).not.toHaveBeenCalled(); confirm();
    expect(page.items[0].evidence).toEqual([]);
    page.assessment!.status = 'PENDING_REVIEW'; page.removeEvidence(page.items[0], evidence); expect(api.removeEvidence.calls.count()).toBe(1);
  });
  it('downloads through the authenticated assessment API and revokes the temporary blob URL', () => {
    jasmine.clock().install();
    try {
      const evidence = { id: 8, originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 5, uploadedBy: 'owner', uploadedAt: '2028-02-01' };
      api.download.and.returnValue(of(new Blob(['proof']))); spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
      spyOn(URL, 'revokeObjectURL'); spyOn(HTMLAnchorElement.prototype, 'click');
      page.downloadEvidence(evidence); expect(api.download).toHaveBeenCalledOnceWith(8);
      jasmine.clock().tick(1001); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
    } finally { jasmine.clock().uninstall(); }
  });
  it('refreshes late assignments without discarding unsaved Draft answers', () => {
    page.items[0].selfPoint = 4; page.items[0].selfComment = 'Keep this';
    api.mine.and.returnValue(of(assessment({ items: [row(), row(5, 'DEPARTMENT')], submissionBlockers: ['Select a Self-Assessment Point for Sales 5'] })));
    page.refresh(); expect(page.items[0].selfComment).toBe('Keep this'); expect(page.items[0].selfPoint).toBe(4);
    expect(page.items[1].selfPoint).toBeNull(); expect(page.canSubmit).toBeFalse();
  });
  it('formats monthly, quarterly and annual checkpoints using backend-provided intervals', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { MY_ASSESSMENTS: { QUARTER: 'Quarter {{number}} {{year}}', ANNUAL: 'Annual {{year}}' } });
    translate.use('en');
    expect(page.checkpointLabel(checkpoint())).toBe('January 2028');
    expect(page.checkpointLabel({ ...checkpoint(), reviewFrequency: 'QUARTERLY', sequenceNumber: 2, endDate: '2028-06-30' })).toBe('Quarter 2 2028');
    expect(page.checkpointLabel({ ...checkpoint(), reviewFrequency: 'QUARTERLY', sequenceNumber: 1, startDate: '2028-07-15', endDate: '2028-09-30' })).toBe('Quarter 3 2028');
    expect(page.checkpointLabel({ ...checkpoint(), reviewFrequency: 'ANNUALLY' })).toBe('Annual 2028');
  });
  it('clears stale context when no enrolled periods remain and supports retry after a failed load', () => {
    api.periods.and.returnValue(of([])); page.load(); fixture.detectChanges();
    expect(page.assessment).toBeNull(); expect(page.items).toEqual([]); expect(page.checkpoints).toEqual([]);
    expect(page.periodId).toBeNull(); expect(page.checkpointId).toBeNull();
    api.periods.and.returnValue(throwError(() => ({ status: 403 }))); page.load();
    expect(page.ready).toBeFalse(); expect(page.error).toBe('MY_ASSESSMENTS.ACCESS_CHANGED');
    api.periods.and.returnValue(of([period])); page.retry();
    expect(page.ready).toBeTrue(); expect(page.items.length).toBe(1);
  });
  it('uses server answers rather than unsaved inputs when refresh discovers submission', () => {
    page.items[0].selfPoint = 5; page.items[0].selfComment = 'Local changes';
    api.mine.and.returnValue(of(assessment({ status: 'PENDING_REVIEW', canSaveDraft: false,
      items: [{ ...row(), selfPoint: 3, selfComment: 'Submitted answer' }] })));
    page.refresh(); expect(page.readonly).toBeTrue(); expect(page.dirty).toBeFalse();
    expect(page.items[0].selfPoint).toBe(3); expect(page.items[0].selfComment).toBe('Submitted answer');
  });
});
