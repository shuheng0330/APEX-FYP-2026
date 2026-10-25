import { CommonModule } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { finalize, map, tap } from 'rxjs';
import { AnnualAppraisal, AnnualAppraisalCandidate } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { TablePageSizeSelectorComponent } from '../../components/table-page-size-selector/table-page-size-selector.component';
import { ReviewTaskGuideComponent } from '../kpi-plan/review-task-guide.component';
import { AnnualAppraisalNavigationState, AppraisalQueueState, AppraisalQueueStatus } from './annual-appraisal-navigation-state.service';
import { notificationTarget } from '../../services/notification-navigation';
import { NotificationTargetComponent } from '../../components/notification-bell/notification-target.component';

@Component({
  selector: 'app-annual-appraisals', standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, NzButtonModule, NzSelectModule, NzPaginationModule,
    TablePageSizeSelectorComponent, ReviewTaskGuideComponent, NotificationTargetComponent],
  templateUrl: './annual-appraisals.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../kpi-plan/kpi-plan.scss', './annual-appraisals.component.scss']
})
export class AnnualAppraisalsComponent implements OnInit {
  readonly target = notificationTarget();
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  readonly hrReview = this.route.snapshot.data['hrReview'] === true;
  readonly statuses: AppraisalQueueStatus[] = this.hrReview ? ['PENDING_REVIEW', 'RETURNED', 'APPROVED']
    : ['NOT_READY', 'READY', 'DRAFT', 'RETURNED', 'PENDING_REVIEW', 'APPROVED'];
  filter: AppraisalQueueStatus = this.hrReview ? 'PENDING_REVIEW' : 'READY';
  periods: KpiPeriodContext[] = [];
  periodId: number | null = null;
  candidates: AnnualAppraisalCandidate[] = [];
  reviews: AnnualAppraisal[] = [];
  periodLoading = false;
  loading = false;
  navigating = false;
  queueLoaded = false;
  periodError = '';
  error = '';
  search = '';
  department: string | null = null;
  pageIndex = 1;
  pageSize = 10;
  private restore: AppraisalQueueState | null = null;
  private destroyed = false;

  constructor(private api: AnnualAppraisalService, public auth: AuthService, private translate: TranslateService,
    private router: Router, private navigation: AnnualAppraisalNavigationState) {}
  ngOnInit() { this.destroyRef.onDestroy(() => { this.destroyed = true; this.rememberQueue(); }); this.loadPeriods(); }
  get authorised() { return this.auth.hasRole(this.hrReview ? 'CAN_REVIEW_ANNUAL_APPRAISAL' : 'CAN_SUBMIT_ANNUAL_APPRAISAL'); }
  get titleKey() { return this.hrReview ? 'ANNUAL_APPRAISAL.HR_TITLE' : 'ANNUAL_APPRAISAL.TEAM_TITLE'; }
  get locked() { return this.loading || this.periodLoading || this.navigating; }
  get period() { return this.periods.find(p => p.id === this.periodId); }
  get rows() { return this.hrReview ? this.reviews.map(a => ({ ...a, appraisalId: a.id })) : this.candidates; }
  status(row: AnnualAppraisalCandidate): AppraisalQueueStatus { return row.status ?? (row.readiness.ready ? 'READY' : 'NOT_READY'); }
  private get matchingRows() {
    const search = this.search.trim().toLowerCase();
    return this.rows.filter(r => (!this.department || r.departmentName === this.department)
      && (!search || `${r.employeeName ?? ''} ${r.departmentName ?? ''} ${r.roleName ?? ''}`.toLowerCase().includes(search)));
  }
  get filteredRows() { return this.matchingRows.filter(r => this.status(r) === this.filter); }
  get visibleRows() { return this.filteredRows.slice((this.pageIndex - 1) * this.pageSize, this.pageIndex * this.pageSize); }
  get departments() { return [...new Set(this.rows.map(r => r.departmentName).filter((v): v is string => !!v))].sort(); }
  count(status: AppraisalQueueStatus) { return this.matchingRows.filter(r => this.status(r) === status).length; }
  queueScore(row: AnnualAppraisalCandidate) {
    if (!this.hrReview && row.status === 'PENDING_REVIEW') return null;
    return this.hrReview ? this.reviews.find(a => a.id === row.appraisalId)?.snapshot?.finalScore : row.readiness.performance.finalScore;
  }
  rowAction(row: AnnualAppraisalCandidate) {
    if (this.period?.status !== 'OPEN' || row.status === 'APPROVED') return 'ANNUAL_APPRAISAL.VIEW';
    if (this.hrReview) return row.status === 'PENDING_REVIEW' ? 'ANNUAL_APPRAISAL.REVIEW' : 'ANNUAL_APPRAISAL.VIEW';
    if (row.status === 'PENDING_REVIEW') return 'ANNUAL_APPRAISAL.VIEW';
    return 'ANNUAL_APPRAISAL.' + (row.status === 'RETURNED' ? 'REVISE' : row.status === 'DRAFT' ? 'CONTINUE_DRAFT' : 'PREPARE');
  }
  setFilter(status: AppraisalQueueStatus) { this.filter = status; this.pageIndex = 1; }
  loadPeriods() {
    if (this.locked || !this.authorised) return;
    this.periodLoading = true; this.periodError = '';
    this.api.periods(this.hrReview).pipe(finalize(() => this.periodLoading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: periods => {
        this.periods = periods;
        this.restore = this.navigation.read(this.auth.userId, this.hrReview);
        let remembered = 0;
        try { remembered = Number(sessionStorage.getItem(`annual-appraisal-period:${this.hrReview ? 'hr' : 'team'}:${this.auth.userId}`)); } catch {}
        let linkedPeriod: number | null = null;
        try { linkedPeriod = this.target.value('reviewPeriodId'); this.periodId = this.target.choose(periods, 'reviewPeriodId', p => p.id, periods.find(p => p.id === this.periodId)?.id ?? periods.find(p => p.id === this.restore?.periodId)?.id
          ?? periods.find(p => p.id === remembered)?.id ?? periods.find(p => p.status === 'OPEN')?.id
          ?? periods.find(p => p.status === 'CLOSED')?.id ?? null); }
        catch { this.periodId = null; return; }
        if (linkedPeriod === null && this.restore?.periodId === this.periodId) {
          const { filter, search, department, pageIndex, pageSize } = this.restore;
          Object.assign(this, { filter, search, department, pageIndex, pageSize });
        } else this.restore = null;
        this.loadQueue();
      }, error: () => this.periodError = this.translate.instant('ANNUAL_APPRAISAL.PERIOD_ERROR')
    });
  }
  loadQueue() {
    if (this.loading || this.navigating || !this.authorised || this.periodId === null) return;
    this.loading = true; this.queueLoaded = false; this.error = '';
    const request = this.hrReview
      ? this.api.reviews(this.periodId).pipe(tap(rows => this.reviews = rows), map(() => undefined))
      : this.api.team(this.periodId).pipe(tap(rows => this.candidates = rows), map(() => undefined));
    request.pipe(finalize(() => this.loading = false), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.queueLoaded = true;
        this.pageIndex = Math.min(this.pageIndex, Math.max(1, Math.ceil(this.filteredRows.length / this.pageSize)));
        if (this.restore) {
          const scrollTop = this.restore.scrollTop; this.restore = null;
          requestAnimationFrame(() => { if (!this.destroyed) document.querySelector('.main-content')?.scrollTo({ top: scrollTop }); });
        }
      }, error: error => this.error = error.error?.message || this.translate.instant('ANNUAL_APPRAISAL.ERROR')
    });
  }
  changePeriod(id: number | null) {
    if (this.locked || id === this.periodId || !this.periods.some(p => p.id === id)) return;
    this.periodId = id; this.search = ''; this.department = null; this.pageIndex = 1;
    this.candidates = []; this.reviews = []; this.restore = null;
    this.navigation.rememberPeriod(this.auth.userId, this.hrReview, id!); this.loadQueue();
  }
  async open(row: AnnualAppraisalCandidate) {
    if (this.locked || this.periodId === null || (this.hrReview && row.appraisalId === null)) return;
    this.rememberQueue();
    this.navigating = true;
    try {
      await this.router.navigate(this.hrReview ? ['/appraisals/review', row.appraisalId]
        : ['/team-performance/team-appraisals', this.periodId, row.participantId]);
    } finally { this.navigating = false; }
  }
  @HostListener('window:beforeunload')
  rememberQueue() {
    if (!this.queueLoaded || this.periodId === null || !this.authorised) return;
    this.navigation.save(this.auth.userId, this.hrReview, { periodId: this.periodId, filter: this.filter, search: this.search,
      department: this.department, pageIndex: this.pageIndex, pageSize: this.pageSize,
      scrollTop: document.querySelector('.main-content')?.scrollTop ?? 0 });
  }
  canLeave() {
    const allowed = !this.loading && !this.periodLoading;
    if (allowed) this.rememberQueue();
    return allowed;
  }
}
