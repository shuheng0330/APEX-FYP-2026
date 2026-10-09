import { CommonModule, DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { finalize, of, switchMap, throwError } from 'rxjs';
import { KpiItem, KpiLevel } from '../../models/kpi-plan.model';
import { KpiAssessment, KpiAssessmentCheckpoint, KpiAssessmentEvidence, KpiAssessmentItem,
  KpiAssessmentReview, KpiAssessmentReviewStatus, KpiSuperiorReviewProgress, superiorReviewProgress,
  superiorAssessmentItemError } from '../../models/kpi-assessment.model';
import { KpiAssessmentService } from '../../services/kpi-assessment.service';
import { KpiItemEditorComponent } from '../kpi-plan/kpi-item-editor.component';

@Component({
  selector: 'app-kpi-assessment-reviews', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzDrawerModule, NzSelectModule, NzModalModule, KpiItemEditorComponent],
  templateUrl: './kpi-assessment-reviews.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss', './my-assessments.component.scss', './kpi-assessment-reviews.component.scss']
})
export class KpiAssessmentReviewsComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dates = new DatePipe('en');
  private savedAnswers = '';
  readonly statuses: KpiSuperiorReviewProgress[] = ['PENDING_REVIEW', 'DRAFT', 'REVIEWED'];
  readonly reviewProgress = superiorReviewProgress;
  readonly points = [1, 2, 3, 4, 5];
  filter: KpiSuperiorReviewProgress = 'PENDING_REVIEW';
  periodId: number | null = null;
  search = '';
  reviews: KpiAssessmentReview[] = [];
  selected: KpiAssessment | null = null;
  items: KpiAssessmentItem[] = [];
  busy = false;
  loading = false;
  drawerVisible = false;
  criteriaVisible = false;
  detailItem: KpiItem[] = [];
  detailScope: KpiLevel = 'INDIVIDUAL';
  accessChanged = false;
  showValidation = false;
  error = '';
  success = '';

  constructor(private api: KpiAssessmentService, private translate: TranslateService, private modal: NzModalService) {}
  ngOnInit() { this.load(); }
  get locked() { return this.busy || this.loading || this.drawerVisible; }
  get readonly() {
    return this.accessChanged || this.selected?.canSaveSuperiorDraft !== true
      || this.selected.status !== 'PENDING_REVIEW' || this.selected.reviewPeriodStatus !== 'OPEN';
  }
  get dirty() { return !!this.selected && this.answerSignature() !== this.savedAnswers; }
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
    // Saved missing-point messages must not block newly entered, not-yet-saved answers.
    const reasons = (this.selected.reviewBlockers ?? []).filter(reason => !reason.startsWith('Select a Superior Assessment Point for '));
    const missing = this.items.filter(item => item.superiorPoint == null).length;
    if (missing) reasons.push(this.translate.instant('KPI_ASSESSMENT_REVIEW.POINTS_REMAINING', { count: missing }));
    if (this.items.some(item => superiorAssessmentItemError(item))) reasons.push(this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'));
    if (!this.items.length) reasons.push(this.translate.instant('KPI_ASSESSMENT_REVIEW.NO_ITEMS'));
    return [...new Set(reasons)];
  }
  get canComplete() { return !this.readonly && !this.busy && !this.criteriaVisible && !this.completionReasons.length; }
  itemError(item: KpiAssessmentItem) { return superiorAssessmentItemError(item, this.showValidation); }
  checkpointLabel(checkpoint: KpiAssessmentCheckpoint) {
    if (checkpoint.reviewFrequency === 'MONTHLY') return this.dates.transform(checkpoint.endDate, 'MMMM yyyy') ?? '';
    return this.translate.instant(checkpoint.reviewFrequency === 'QUARTERLY' ? 'MY_ASSESSMENTS.QUARTER' : 'MY_ASSESSMENTS.ANNUAL',
      { number: Math.ceil(Number(checkpoint.endDate.slice(5, 7)) / 3), year: checkpoint.endDate.slice(0, 4) });
  }
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
  private answerSignature() { return JSON.stringify(this.items.map(item => [item.id, item.superiorPoint, item.superiorComment ?? null])); }
  private accept(assessment: KpiAssessment) {
    this.selected = assessment; this.items = structuredClone(assessment.items); this.savedAnswers = this.answerSignature(); this.showValidation = false;
    this.reviews = this.reviews.map(row => row.id === assessment.id ? { ...row, status: assessment.status as KpiAssessmentReviewStatus,
      canReview: assessment.canSaveSuperiorDraft === true, superiorOverdue: assessment.superiorOverdue === true,
      superiorDraftSaved: assessment.superiorDraftSaved,
      reviewedAt: assessment.reviewedAt, reviewedLate: assessment.reviewedLate, checkpointScore: assessment.checkpointScore } : row);
  }
  close() {
    if (this.busy || this.criteriaVisible) return;
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
  viewCriteria(item: KpiAssessmentItem) { this.detailItem = [structuredClone(item.kpi)]; this.detailScope = item.level; this.criteriaVisible = true; }
  private validDraft() {
    if (this.items.some(item => item.id == null || superiorAssessmentItemError(item))) {
      this.error = this.translate.instant('MY_ASSESSMENTS.FIX_ANSWERS'); return false;
    }
    return true;
  }
  private persist() {
    const request = { items: this.items.map(item => ({ itemId: item.id!, superiorPoint: item.superiorPoint, superiorComment: item.superiorComment })) };
    return this.api.saveSuperiorDraft(this.selected!.id!, request).pipe(switchMap(assessment => { this.accept(assessment); return of(assessment); }));
  }
  saveDraft() {
    if (this.readonly || this.busy || !this.validDraft()) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.success = this.translate.instant('KPI_ASSESSMENT_REVIEW.SAVED'), error: error => this.fail(error)
    });
  }
  confirmComplete() {
    this.showValidation = true;
    if (!this.canComplete) return;
    this.modal.confirm({ nzTitle: this.translate.instant('KPI_ASSESSMENT_REVIEW.COMPLETE'),
      nzContent: this.translate.instant('KPI_ASSESSMENT_REVIEW.COMPLETE_CONFIRM'), nzOnOk: () => this.complete() });
  }
  complete() {
    if (!this.canComplete || !this.validDraft()) return;
    this.busy = true; this.error = ''; this.success = '';
    this.persist().pipe(switchMap(assessment => {
      if (!assessment.canCompleteReview || assessment.id === null) return throwError(() => ({ error: { message:
        assessment.reviewBlockers?.join('; ') || this.translate.instant('KPI_ASSESSMENT_REVIEW.READINESS_CHANGED') } }));
      return this.api.completeReview(assessment.id);
    }), finalize(() => this.busy = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: assessment => { this.accept(assessment); this.success = this.translate.instant('KPI_ASSESSMENT_REVIEW.COMPLETED'); }, error: error => this.fail(error)
    });
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
  private fail(error: { status?: number; error?: { message?: string } }) {
    if (error.status === 403) this.accessChanged = true;
    this.error = error.status === 403 ? this.translate.instant('KPI_ASSESSMENT_REVIEW.ACCESS_CHANGED')
      : error.error?.message || this.translate.instant('MY_ASSESSMENTS.ERROR');
  }
}
