import { CommonModule, DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { Observable, finalize, of, switchMap, throwError } from 'rxjs';
import { KpiItem, KpiPeriodContext } from '../../models/kpi-plan.model';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentEvidence, KpiAssessmentItem, assessmentItemError, assessmentPointError, assessmentCommentError } from '../../models/kpi-assessment.model';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { KpiItemEditorComponent } from '../kpi-plan/kpi-item-editor.component';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzInputModule,
    NzDrawerModule, NzTabsModule, NzModalModule, KpiItemEditorComponent, KpiScoringGuideComponent],
  templateUrl: './my-assessments.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss', './my-assessments.component.scss']
})
export class MyAssessmentsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dates = new DatePipe('en');
  private savedAnswers = '';
  periods: KpiPeriodContext[] = [];
  checkpoints: KpiAssessmentCheckpoint[] = [];
  periodId: number | null = null;
  checkpointId: number | null = null;
  assessment: KpiAssessment | null = null;
  items: KpiAssessmentItem[] = [];
  busy = false;
  ready = false;
  error = '';
  success = '';
  showValidation = false;
  draftWarnings = false;
  drawerVisible = false;
  detailItem: KpiItem[] = [];
  detailScope: KpiAssessmentItem['level'] = 'INDIVIDUAL';
  readonly points = [1, 2, 3, 4, 5];

  constructor(private api: KpiAssessmentService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get employeeContext() {
    return [this.assessment?.employeeName, this.assessment?.roleName, this.assessment?.kpiAllocation?.employeeLevelName]
      .map(value => value?.trim()).filter(Boolean).join(' · ');
  }
  get readonly() { return !this.ready || !this.assessment?.canSaveDraft || this.assessment.status !== 'DRAFT'; }
  get dirty() { return !!this.assessment && !this.readonly && this.answerSignature() !== this.savedAnswers; }
  get canSubmit() { return !this.readonly && !this.busy && this.submissionReasons.length === 0; }
  get submissionReasons(): string[] {
    if (!this.assessment || this.assessment.status !== 'DRAFT') return [];
    // Saved missing-point blockers are replaced by validation of the employee's current inputs.
    const reasons = this.assessment.submissionBlockers.filter(reason => !reason.startsWith('Select a Self-Assessment Point for '));
    const missingPoints = this.items.filter(item => item.selfPoint == null).length;
    if (missingPoints) reasons.push(this.translate.instant('MY_ASSESSMENTS.POINTS_REMAINING', { count: missingPoints }));
    if (this.items.some(item => assessmentItemError(item))) reasons.push(this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'));
    return [...new Set(reasons)];
  }
  itemError(item: KpiAssessmentItem) { return assessmentItemError(item, this.showValidation); }
  pointError(item: KpiAssessmentItem) { return assessmentPointError(item.selfPoint, this.showValidation || this.draftWarnings); }
  readonly commentError = assessmentCommentError;
  private answerSignature() {
    return JSON.stringify(this.items.map(item => [item.assignmentId, item.selfPoint, item.selfComment ?? null]));
  }
  private request() {
    return { checkpointId: this.checkpointId!, items: this.items.map(item => ({
      assignmentId: item.assignmentId, selfPoint: item.selfPoint, selfComment: item.selfComment
    })) };
  }
  private accept(assessment: KpiAssessment) {
    this.assessment = assessment; this.items = structuredClone(assessment.items); this.savedAnswers = this.answerSignature();
    this.showValidation = false; this.draftWarnings = false;
    this.checkpoints = this.checkpoints.map(checkpoint => checkpoint.id === assessment.checkpoint.id ? assessment.checkpoint : checkpoint);
  }
  private preferredCheckpoint() {
    return this.checkpoints.find(checkpoint => checkpoint.available && (!checkpoint.assessmentStatus || checkpoint.assessmentStatus === 'DRAFT'))
      ?? [...this.checkpoints].reverse().find(checkpoint => checkpoint.available)
      ?? this.checkpoints[0];
  }
  private periodAssessment(id: number): Observable<KpiAssessment | null> {
    return this.api.checkpoints(id).pipe(switchMap(checkpoints => {
      this.checkpoints = checkpoints; this.checkpointId = this.preferredCheckpoint()?.id ?? null;
      return this.checkpointId === null ? of(null) : this.api.mine(this.checkpointId);
    }));
  }
  load() {
    if (this.busy) return;
    this.busy = true; this.ready = false; this.error = ''; this.success = '';
    this.assessment = null; this.items = []; this.checkpoints = []; this.checkpointId = null;
    this.api.periods().pipe(switchMap(periods => {
      this.periods = periods;
      this.periodId = periods.find(period => period.id === this.periodId)?.id
        ?? periods.find(period => period.status === 'OPEN')?.id ?? periods[0]?.id ?? null;
      return this.periodId === null ? of(null) : this.periodAssessment(this.periodId);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { if (assessment) this.accept(assessment); this.ready = true; }, error: error => this.fail(error)
    });
  }
  changePeriod(id: number) {
    if (this.busy || id === this.periodId || !this.periods.some(period => period.id === id)) return;
    this.confirmDiscard(() => {
      this.periodId = id; this.assessment = null; this.items = []; this.checkpoints = []; this.checkpointId = null;
      this.fetch(this.periodAssessment(id));
    });
  }
  changeCheckpoint(id: number) {
    if (this.busy || id === this.checkpointId || !this.checkpoints.some(checkpoint => checkpoint.id === id)) return;
    this.confirmDiscard(() => { this.checkpointId = id; this.assessment = null; this.items = []; this.fetch(this.api.mine(id)); });
  }
  private confirmDiscard(action: () => void) {
    if (!this.dirty) { action(); return; }
    this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.DISCARD_HELP'),
      nzOnOk: () => { if (!this.busy) action(); } });
  }
  private fetch(operation: Observable<KpiAssessment | null>) {
    this.busy = true; this.ready = false; this.error = ''; this.success = '';
    operation.pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { if (assessment) this.accept(assessment); this.ready = true; }, error: error => this.fail(error)
    });
  }
  retry() {
    if (this.busy) return;
    if (!this.periods.length) this.load();
    else if (this.checkpointId !== null) this.fetch(this.api.mine(this.checkpointId));
    else if (this.periodId !== null) this.fetch(this.periodAssessment(this.periodId));
  }
  refresh() {
    if (this.busy || this.checkpointId === null) return;
    const answers = new Map(this.items.map(item => [item.assignmentId, { selfPoint: item.selfPoint, selfComment: item.selfComment }]));
    this.busy = true; this.error = ''; this.success = '';
    this.api.mine(this.checkpointId).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => {
        this.accept(assessment);
        if (assessment.canSaveDraft && assessment.status === 'DRAFT') {
          this.items.forEach(item => { const answer = answers.get(item.assignmentId); if (answer) Object.assign(item, answer); });
        }
      }, error: error => this.fail(error)
    });
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy) return false;
    if (!this.dirty) return true;
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.DISCARD_HELP'),
      nzOnOk: () => resolve(true), nzOnCancel: () => resolve(false) }));
  }
  checkpointLabel(checkpoint: KpiAssessmentCheckpoint) {
    if (checkpoint.reviewFrequency === 'MONTHLY') return this.dates.transform(checkpoint.endDate, 'MMMM yyyy') ?? '';
    return this.translate.instant(checkpoint.reviewFrequency === 'QUARTERLY' ? 'MY_ASSESSMENTS.QUARTER' : 'MY_ASSESSMENTS.ANNUAL',
      { number: Math.ceil(Number(checkpoint.endDate.slice(5, 7)) / 3), year: checkpoint.endDate.slice(0, 4) });
  }
  viewCriteria(item: KpiAssessmentItem) {
    this.detailItem = [structuredClone(item.kpi)]; this.detailScope = item.level; this.drawerVisible = true;
  }
  private persist(): Observable<KpiAssessment> {
    const request = this.request();
    return (this.assessment?.id == null ? this.api.create(request) : this.api.update(this.assessment.id, request))
      .pipe(switchMap(assessment => { this.accept(assessment); return of(assessment); }));
  }
  private validDraft() {
    if (this.items.some(item => assessmentItemError(item))) { this.error = this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'); return false; }
    return true;
  }
  saveDraft() {
    if (this.readonly || this.busy || !this.validDraft()) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.draftWarnings = true; this.success = this.translate.instant('MY_ASSESSMENTS.SAVED'); }, error: error => this.fail(error)
    });
  }
  confirmSubmit() {
    this.showValidation = true;
    if (!this.canSubmit) return;
    this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.SUBMIT'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.SUBMIT_CONFIRM'), nzOnOk: () => this.submit() });
  }
  submit() {
    if (!this.canSubmit) return;
    this.busy = true; this.error = ''; this.success = '';
    // Persist current inputs before submitting: the backend submits saved answers, not a request body.
    this.persist().pipe(switchMap(assessment => {
      if (!assessment.canSubmit || assessment.id === null) {
        return throwError(() => ({ error: { message: assessment.submissionBlockers.join('; ') || this.translate.instant('MY_ASSESSMENTS.READINESS_CHANGED') } }));
      }
      return this.api.submit(assessment.id);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { this.accept(assessment); this.success = this.translate.instant('MY_ASSESSMENTS.SUBMITTED'); },
      error: error => this.fail(error)
    });
  }
  attachEvidence(assignmentId: number, event: Event) {
    const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
    if (!file || this.readonly || this.busy || !this.validDraft()) return;
    if (!file.size) { this.error = this.translate.instant('MY_ASSESSMENTS.EMPTY_FILE'); return; }
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(switchMap(assessment => {
      const itemId = assessment.items.find(item => item.assignmentId === assignmentId)?.id;
      return itemId == null ? throwError(() => ({ error: { message: this.translate.instant('MY_ASSESSMENTS.READINESS_CHANGED') } }))
        : this.api.upload(itemId, file);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: evidence => {
        const item = this.items.find(item => item.assignmentId === assignmentId);
        if (item) item.evidence = [...item.evidence, evidence];
        this.success = this.translate.instant('MY_ASSESSMENTS.EVIDENCE_SAVED');
      }, error: error => this.fail(error)
    });
  }
  removeEvidence(item: KpiAssessmentItem, evidence: KpiAssessmentEvidence) {
    if (this.readonly || this.busy) return;
    this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.REMOVE_EVIDENCE'),
      nzContent: evidence.originalFilename, nzOnOk: () => {
        if (this.readonly || this.busy) return;
        this.busy = true; this.error = ''; this.success = '';
        this.api.removeEvidence(evidence.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
          next: () => { item.evidence = item.evidence.filter(file => file.id !== evidence.id); }, error: error => this.fail(error)
        });
      } });
  }
  downloadEvidence(evidence: KpiAssessmentEvidence) {
    if (this.busy) return;
    this.busy = true; this.error = '';
    this.api.download(evidence.id).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = evidence.originalFilename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      }, error: error => this.fail(error)
    });
  }
  @HostListener('window:beforeunload', ['$event']) warnUnsaved(event: BeforeUnloadEvent) {
    if (this.dirty || this.busy) { event.preventDefault(); event.returnValue = ''; }
  }
  private fail(error: { error?: { message?: string }; status?: number }) {
    this.error = error.status === 403 ? this.translate.instant('MY_ASSESSMENTS.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('MY_ASSESSMENTS.ERROR');
  }
}
