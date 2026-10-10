import { CommonModule } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { finalize, switchMap, tap, throwError } from 'rxjs';
import { AttitudeAssessment, AttitudeAssessmentItem, AttitudeAssessmentReview } from '../../models/attitude-assessment.model';
import { assessmentPointError, assessmentCommentError, KpiSuperiorReviewProgress, superiorReviewProgress } from '../../models/kpi-assessment.model';
import { AttitudeAssessmentService } from '../../services/attitude-assessment.service';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';

@Component({
  selector: 'app-attitude-assessment-reviews', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzSelectModule, NzModalModule, KpiScoringGuideComponent],
  templateUrl: './attitude-assessment-reviews.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss', './my-assessments.component.scss', './kpi-assessment-reviews.component.scss']
})
export class AttitudeAssessmentReviewsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private savedAnswers = '';
  readonly statuses: KpiSuperiorReviewProgress[] = ['PENDING_REVIEW', 'DRAFT', 'REVIEWED'];
  readonly points = [1, 2, 3, 4, 5];
  filter: KpiSuperiorReviewProgress = 'PENDING_REVIEW';
  periodId: number | null = null;
  search = '';
  reviews: AttitudeAssessmentReview[] = [];
  selected: AttitudeAssessment | null = null;
  items: AttitudeAssessmentItem[] = [];
  busy = false;
  loading = false;
  drawerVisible = false;
  accessChanged = false;
  showValidation = false;
  draftWarnings = false;
  error = '';
  success = '';

  constructor(private api: AttitudeAssessmentService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get locked() { return this.busy || this.loading || this.drawerVisible; }
  get readonly() {
    return this.accessChanged || this.selected?.canSaveSuperiorDraft !== true
      || this.selected.status !== 'PENDING_REVIEW' || this.selected.reviewPeriodStatus !== 'OPEN';
  }
  get dirty() { return !!this.selected && this.answerSignature() !== this.savedAnswers; }
  reviewProgress(assessment: Pick<AttitudeAssessment, 'status' | 'superiorDraftSaved'>) {
    return superiorReviewProgress({ status: assessment.status ?? 'PENDING_REVIEW', superiorDraftSaved: assessment.superiorDraftSaved });
  }
  get periods() {
    return [...new Map(this.reviews.map(row => [row.reviewPeriodId, { id: row.reviewPeriodId, name: row.reviewPeriodName }])).values()];
  }
  private get filteredReviews() {
    const search = this.search.trim().toLowerCase();
    return this.reviews.filter(row => (this.periodId === null || row.reviewPeriodId === this.periodId)
      && (!search || `${row.employeeName} ${row.departmentName ?? ''}`.toLowerCase().includes(search)));
  }
  get visibleReviews() { return this.filteredReviews.filter(row => this.reviewProgress(row) === this.filter); }
  count(status: KpiSuperiorReviewProgress) { return this.filteredReviews.filter(row => this.reviewProgress(row) === status).length; }
  get completionReasons() {
    if (!this.selected || this.selected.status !== 'PENDING_REVIEW') return [];
    // Replace saved missing-point messages with readiness based on the current inputs.
    const reasons = (this.selected.reviewBlockers ?? []).filter(reason => !reason.startsWith('Select a Superior Assessment Point for '));
    const missing = this.items.filter(item => item.superiorPoint == null).length;
    if (missing) reasons.push(this.translate.instant('ATTITUDE_REVIEW.POINTS_REMAINING', { count: missing }));
    if (this.items.some(item => this.itemError(item))) reasons.push(this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'));
    if (!this.items.length) reasons.push(this.translate.instant('ATTITUDE_REVIEW.NO_ITEMS'));
    return [...new Set(reasons)];
  }
  get canComplete() { return !this.readonly && !this.busy && !this.completionReasons.length; }
  pointError(item: AttitudeAssessmentItem) { return assessmentPointError(item.superiorPoint, this.showValidation || this.draftWarnings); }
  readonly commentError = assessmentCommentError;
  private itemError(item: AttitudeAssessmentItem) {
    return assessmentPointError(item.superiorPoint) ?? assessmentCommentError(item.superiorComment);
  }
  rating(point: number | null | undefined) { return this.selected?.ratingDefinitions.find(rating => rating.point === point); }
  load() {
    if (this.locked) return;
    this.loading = true; this.error = '';
    this.api.reviews().pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: rows => this.reviews = rows, error: error => this.fail(error)
    });
  }
  open(id: number) {
    if (this.locked) return;
    this.loading = true; this.error = ''; this.success = ''; this.accessChanged = false;
    this.api.get(id).pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { this.accept(assessment); this.drawerVisible = true; }, error: error => this.fail(error)
    });
  }
  private answerSignature() { return JSON.stringify(this.items.map(item => [item.id, item.superiorPoint ?? null, item.superiorComment ?? null])); }
  private accept(assessment: AttitudeAssessment) {
    this.selected = assessment; this.items = structuredClone(assessment.items); this.savedAnswers = this.answerSignature();
    this.showValidation = false; this.draftWarnings = false;
    this.reviews = this.reviews.map(row => row.id === assessment.id ? { ...row, status: assessment.status as AttitudeAssessmentReview['status'],
      canReview: assessment.canSaveSuperiorDraft === true, superiorOverdue: assessment.superiorOverdue === true,
      superiorDraftSaved: assessment.superiorDraftSaved === true, reviewedAt: assessment.reviewedAt ?? null,
      reviewedLate: assessment.reviewedLate ?? null, attitudeScore: assessment.attitudeScore ?? null } : row);
  }
  close() {
    if (this.busy) return;
    if (!this.dirty) { this.drawerVisible = false; return; }
    this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.DISCARD_HELP'), nzOnOk: () => {
        if (!this.busy) { this.items = structuredClone(this.selected!.items); this.savedAnswers = this.answerSignature(); this.drawerVisible = false; }
      } });
  }
  canLeave(): boolean | Promise<boolean> {
    if (this.busy || this.loading) return false;
    if (!this.dirty) return true;
    return new Promise(resolve => this.modal.confirm({ nzTitle: this.translate.instant('MY_ASSESSMENTS.DISCARD_TITLE'),
      nzContent: this.translate.instant('MY_ASSESSMENTS.DISCARD_HELP'), nzOnOk: () => resolve(!this.busy), nzOnCancel: () => resolve(false) }));
  }
  private validDraft() {
    if (this.items.some(item => item.id == null || this.itemError(item))) {
      this.error = this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'); return false;
    }
    return true;
  }
  private persist() {
    const request = { items: this.items.map(item => ({ itemId: item.id!, superiorPoint: item.superiorPoint ?? null, superiorComment: item.superiorComment ?? null })) };
    return this.api.saveSuperiorDraft(this.selected!.id!, request).pipe(tap(assessment => this.accept(assessment)));
  }
  saveDraft() {
    if (this.readonly || this.busy || !this.validDraft()) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.draftWarnings = true; this.success = this.translate.instant('ATTITUDE_REVIEW.SAVED'); }, error: error => this.fail(error)
    });
  }
  confirmComplete() {
    this.showValidation = true;
    if (!this.canComplete) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSESSMENT_REVIEW.COMPLETE'),
      nzContent: this.translate.instant('ATTITUDE_REVIEW.COMPLETE_CONFIRM'), nzOnOk: () => this.complete() });
  }
  complete() {
    if (!this.canComplete || !this.validDraft()) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(switchMap(assessment => {
      if (!assessment.canCompleteReview || assessment.id === null) return throwError(() => ({ error: { message:
        assessment.reviewBlockers?.join('; ') || this.translate.instant('KPI_ASSESSMENT_REVIEW.READINESS_CHANGED') } }));
      return this.api.completeReview(assessment.id);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { this.accept(assessment); this.success = this.translate.instant('ATTITUDE_REVIEW.COMPLETED'); }, error: error => this.fail(error)
    });
  }
  @HostListener('window:beforeunload', ['$event']) warnUnsaved(event: BeforeUnloadEvent) {
    if (this.dirty || this.busy) { event.preventDefault(); event.returnValue = ''; }
  }
  private fail(error: { status?: number; error?: { message?: string } }) {
    if (error.status === 403) this.accessChanged = true;
    this.error = error.status === 403 ? this.translate.instant('ATTITUDE_REVIEW.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('MY_ASSESSMENTS.ERROR');
  }
}
