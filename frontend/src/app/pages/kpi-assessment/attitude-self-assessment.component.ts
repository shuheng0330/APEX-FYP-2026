import { CommonModule } from '@angular/common';
import { Component, DestroyRef, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalService } from 'ng-zorro-antd/modal';
import { finalize, of, switchMap, throwError } from 'rxjs';
import { AttitudeAssessment, AttitudeAssessmentItem, AttitudeAssessmentRequest } from '../../models/attitude-assessment.model';
import { assessmentCommentError, assessmentPointError } from '../../models/kpi-assessment.model';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';

@Component({
  selector: 'app-attitude-self-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzInputModule, KpiScoringGuideComponent],
  templateUrl: './attitude-self-assessment.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss',
    './my-assessments.component.scss', './attitude-self-assessment.component.scss']
})
export class AttitudeSelfAssessmentComponent implements OnChanges {
  @Input({ required: true }) reviewPeriodId!: number;
  private readonly destroyRef = inject(DestroyRef);
  private destroyed = false;
  private savedAnswers = '';
  assessment: AttitudeAssessment | null = null;
  items: AttitudeAssessmentItem[] = [];
  busy = false;
  error = '';
  success = '';
  showValidation = false;
  draftWarnings = false;
  readonly points = [1, 2, 3, 4, 5];

  constructor(private api: AttitudeAssessmentService, private translate: TranslateService, private modal: NzModalService) {
    this.destroyRef.onDestroy(() => this.destroyed = true);
  }
  ngOnChanges() {
    const periodId = this.reviewPeriodId;
    // Initialise after the view check so the shared period selector's loading guard stays consistent.
    queueMicrotask(() => { if (!this.destroyed && this.reviewPeriodId === periodId) this.load(); });
  }
  get readonly() { return !this.assessment?.canSaveDraft || this.assessment.status !== 'DRAFT'; }
  get dirty() { return !this.readonly && this.signature() !== this.savedAnswers; }
  get completed() { return this.items.filter(item => !assessmentPointError(item.selfPoint, true)).length; }
  get progress() { return this.items.length ? this.completed / this.items.length * 100 : 0; }
  get employeeContext() { return [this.assessment?.employeeName, this.assessment?.roleName, this.assessment?.departmentName].filter(Boolean).join(' · '); }
  get submissionReasons() {
    if (this.readonly || !this.assessment) return [];
    const reasons = this.assessment.submissionBlockers.filter(reason => !reason.startsWith('Select a Self-Assessment Point for '));
    const remaining = this.items.filter(item => item.selfPoint == null).length;
    if (remaining) reasons.push(this.translate.instant('ATTITUDE_ASSESSMENT.REMAINING', { count: remaining }));
    if (this.items.some(item => assessmentPointError(item.selfPoint) || assessmentCommentError(item.selfComment))) {
      reasons.push(this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'));
    }
    return [...new Set(reasons)];
  }
  get canSubmit() { return !this.busy && !this.readonly && this.items.length > 0 && !this.submissionReasons.length; }
  pointError(item: AttitudeAssessmentItem) { return assessmentPointError(item.selfPoint, this.showValidation || this.draftWarnings); }
  readonly commentError = assessmentCommentError;
  rating(point: number | null) { return this.assessment?.ratingDefinitions.find(rating => rating.point === point); }
  private signature() { return JSON.stringify(this.items.map(item => [item.criterionId, item.selfPoint, item.selfComment ?? null])); }
  private accept(assessment: AttitudeAssessment) {
    this.assessment = assessment; this.items = structuredClone(assessment.items); this.savedAnswers = this.signature();
    this.showValidation = false; this.draftWarnings = false;
  }
  load() {
    this.assessment = null; this.items = []; this.error = ''; this.success = ''; this.busy = true;
    this.api.mine(this.reviewPeriodId).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => this.accept(assessment), error: error => this.fail(error)
    });
  }
  refresh() {
    if (this.busy) return;
    const answers = new Map(this.items.map(item => [item.criterionId, { selfPoint: item.selfPoint, selfComment: item.selfComment }]));
    this.busy = true; this.error = ''; this.success = '';
    this.api.mine(this.reviewPeriodId).pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => {
        this.accept(assessment);
        if (!this.readonly) this.items.forEach(item => { const answer = answers.get(item.criterionId); if (answer) Object.assign(item, answer); });
      }, error: error => this.fail(error)
    });
  }
  private persist() {
    const request: AttitudeAssessmentRequest = { reviewPeriodId: this.reviewPeriodId,
      items: this.items.map(({ criterionId, selfPoint, selfComment }) => ({ criterionId, selfPoint, selfComment })) };
    return (this.assessment?.id == null ? this.api.create(request) : this.api.update(this.assessment.id, request))
      .pipe(switchMap(assessment => { this.accept(assessment); return of(assessment); }));
  }
  saveDraft() {
    if (this.busy || this.readonly) return;
    if (this.items.some(item => assessmentPointError(item.selfPoint) || assessmentCommentError(item.selfComment))) {
      this.error = this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'); return;
    }
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.draftWarnings = true; this.success = this.translate.instant('MY_ASSESSMENTS.SAVED'); }, error: error => this.fail(error)
    });
  }
  confirmSubmit() {
    this.showValidation = true;
    if (!this.canSubmit) return;
    this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.SUBMIT'),
      nzContent: this.translate.instant('ATTITUDE_ASSESSMENT.SUBMIT_CONFIRM'), nzOnOk: () => this.submit() });
  }
  submit() {
    if (!this.canSubmit) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(switchMap(assessment => {
      if (!assessment.canSubmit || assessment.id === null) {
        return throwError(() => ({ error: { message: assessment.submissionBlockers.join('; ') || this.translate.instant('ATTITUDE_ASSESSMENT.READINESS_CHANGED') } }));
      }
      return this.api.submit(assessment.id);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { this.accept(assessment); this.success = this.translate.instant('MY_ASSESSMENTS.SUBMITTED'); }, error: error => this.fail(error)
    });
  }
  private fail(error: { status?: number; error?: { message?: string } }) {
    this.error = error.status === 403 ? this.translate.instant('MY_ASSESSMENTS.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('ATTITUDE_ASSESSMENT.ERROR');
  }
}
