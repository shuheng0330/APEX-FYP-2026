import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { EMPTY, Subject, catchError, combineLatest, finalize, map, merge, of, switchMap } from 'rxjs';
import { KpiItem, KpiLevel, KpiPeriodContext } from '../../models/kpi-plan.model';
import { PerformanceApprovedAppraisal, PerformanceCheckpointDetails, PerformancePeriod, PerformanceScores, StaffPerformance } from '../../models/staff-performance.model';
import { AuthService } from '../../services/auth.service';
import { StaffPerformanceService } from '../../services/staff-performance.service';
import { AnnualAppraisalPerformanceComponent } from '../annual-appraisal/annual-appraisal-performance.component';
import { AnnualPerformanceTrendComponent, AnnualTrendResult } from '../annual-appraisal/annual-performance-trend.component';
import { KpiScoringGuideComponent } from '../kpi-plan/kpi-scoring-guide.component';
import { KpiScoreSummaryComponent } from '../kpi-assessment/kpi-score-summary.component';
import { performanceEmployeeId, performanceId, performanceReturnUrl } from './staff-performance-navigation';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, NzButtonModule, NzDrawerModule, NzModalModule, NzSelectModule,
    AnnualAppraisalPerformanceComponent, AnnualPerformanceTrendComponent, KpiScoringGuideComponent, KpiScoreSummaryComponent],
  templateUrl: './staff-performance.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './staff-performance.component.scss']
})
export class StaffPerformanceComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly refresh = new Subject<void>();
  private readonly checkpointRequests = new Subject<{ participantId: number; checkpointId: number } | null>();
  private readonly appraisalRequests = new Subject<{ participantId: number; appraisalId: number } | null>();
  readonly staffView = this.route.snapshot.data['staffPerformance'] === true;
  readonly points = [5, 4, 3, 2, 1];
  data: StaffPerformance | null = null;
  summary: (PerformanceScores & { reviewFrequency: PerformancePeriod['reviewFrequency'] }) | null = null;
  chartPeriod: KpiPeriodContext | null = null;
  trend: AnnualTrendResult[] = [];
  periods: PerformancePeriod[] = [];
  periodId: number | null = null;
  loading = false;
  error = false;
  empty = false;
  checkpointId: number | null = null;
  checkpoint: PerformanceCheckpointDetails | null = null;
  checkpointLoading = false;
  checkpointError = false;
  drawer: 'history' | 'attitude' | 'appraisal' | null = null;
  appraisal: PerformanceApprovedAppraisal | null = null;
  appraisalLoading = false;
  appraisalError = false;
  recordedCheckpointId: number | null = null;
  scoringKpi: KpiItem | null = null;
  private selectedAppraisal: { participantId: number; appraisalId: number } | null = null;

  constructor(private api: StaffPerformanceService, public auth: AuthService, private router: Router) {}

  ngOnInit() {
    this.checkpointRequests.pipe(switchMap(request => {
      this.checkpoint = null; this.checkpointError = false; this.checkpointLoading = !!request;
      return request ? this.api.checkpoint(request.participantId, request.checkpointId).pipe(map(result => {
        if (result.period.participantId !== request.participantId || result.checkpoint.id !== request.checkpointId) throw new Error('Mismatched checkpoint');
        return result;
      }), catchError(() => { this.checkpointError = true; return of(null); }),
      finalize(() => this.checkpointLoading = false)) : of(null);
    }), takeUntilDestroyed(this.destroyRef)).subscribe(value => this.checkpoint = value);

    this.appraisalRequests.pipe(switchMap(request => {
      this.appraisal = null; this.appraisalError = false; this.appraisalLoading = !!request;
      return request ? this.api.appraisal(request.participantId, request.appraisalId).pipe(map(result => {
        if (result.id !== request.appraisalId || result.period.participantId !== request.participantId || result.status !== 'APPROVED')
          throw new Error('Mismatched approved outcome');
        return result;
      }), catchError(() => { this.appraisalError = true; return of(null); }),
      finalize(() => this.appraisalLoading = false)) : of(null);
    }), takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      this.appraisal = value;
      const checkpoints = [...(this.recordedPerformance?.checkpoints ?? [])].sort((a, b) => a.endDate.localeCompare(b.endDate)
        || a.sequenceNumber - b.sequenceNumber || a.checkpointId - b.checkpointId);
      this.recordedCheckpointId = checkpoints.length ? checkpoints[checkpoints.length - 1].checkpointId : null;
    });

    merge(combineLatest([this.route.paramMap, this.route.queryParamMap]), this.refresh.pipe(map(() =>
      [this.route.snapshot.paramMap, this.route.snapshot.queryParamMap] as const))).pipe(switchMap(([params, query]) => {
      this.data = null; this.periodId = null; this.empty = false; this.error = false; this.loading = true;
      this.summary = null; this.chartPeriod = null; this.trend = [];
      this.drawer = null; this.scoringKpi = null; this.checkpointId = null; this.checkpointRequests.next(null); this.appraisalRequests.next(null);
      this.selectedAppraisal = null; this.periods = [];
      const employeeId = this.staffView ? params.get('employeeId') : this.auth.userId;
      let requestedPeriod: number | null;
      let requestedParticipant: number | null;
      try {
        if (!performanceEmployeeId(employeeId)) throw new Error('Missing employee');
        requestedPeriod = performanceId(query, 'reviewPeriodId'); requestedParticipant = performanceId(query, 'participantId');
        if (requestedParticipant !== null && requestedPeriod === null) throw new Error('Missing period context');
      } catch { this.error = true; this.loading = false; return EMPTY; }
      return this.api.periods(employeeId).pipe(switchMap(periods => {
        this.periods = periods;
        const selected = requestedPeriod !== null ? periods.find(p => p.id === requestedPeriod)
          : periods.find(p => p.status === 'OPEN') ?? periods[0];
        if (!selected) {
          if (requestedPeriod !== null) throw new Error('Unavailable period');
          this.empty = true; return of(null);
        }
        if (requestedParticipant !== null && requestedParticipant !== selected.participantId) throw new Error('Mismatched participant');
        this.periodId = selected.id;
        return this.api.dashboard(employeeId, selected.id).pipe(map(result => {
          if (result.employee.id !== employeeId || result.period.id !== selected.id || result.period.participantId !== selected.participantId)
            throw new Error('Mismatched performance');
          return result;
        }));
      }), catchError(() => { this.error = true; return of(null); }), finalize(() => this.loading = false));
    }), takeUntilDestroyed(this.destroyRef)).subscribe(value => {
      this.data = value;
      if (value) {
        this.summary = { ...value.scores, reviewFrequency: value.period.reviewFrequency };
        this.chartPeriod = { ...value.period, kpiSetupDeadline: null };
        this.trend = value.trend.map(row => ({ ...row.scores, employeeId: value.employee.id, periodId: row.period.id,
          periodName: row.period.name, startDate: row.period.startDate, endDate: row.period.endDate }));
        this.selectCheckpoint(value.defaultCheckpointId);
      }
    });
  }

  get titleKey() { return this.staffView ? 'STAFF_PERFORMANCE.STAFF_TITLE' : 'STAFF_PERFORMANCE.MY_TITLE'; }
  get isSelf() { return this.data?.employee.id === this.auth.userId; }
  get returnUrl() { return performanceReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')); }
  get returnLink() { return this.router.parseUrl(this.returnUrl); }
  get initials() { return (this.data?.employee.name ?? '').trim().split(/\s+/).slice(0, 2).map(part => part[0] ?? '').join('').toUpperCase(); }
  get kpiRows(): { key: string; kpi: KpiItem; level: KpiLevel; point: number | null }[] {
    if (this.checkpoint?.breakdown) return this.checkpoint.breakdown.levels.flatMap(level => level.items.map((item, index) =>
      ({ key: level.level + '-' + (item.kpi.id ?? index), kpi: item.kpi, level: level.level, point: item.point })));
    return this.checkpoint?.answers.map(row => ({ key: String(row.id), kpi: row.kpi, level: row.level, point: row.superiorPoint })) ?? [];
  }
  get internalAppraisal() { return !this.isSelf && this.data?.capabilities.canViewInternalAppraisal ? this.appraisal?.internalDetails : null; }
  get selectedCheckpoint() { return this.data?.checkpoints.find(c => c.id === this.checkpointId) ?? null; }
  get recordedPerformance() { return this.internalAppraisal?.recordedPerformance ?? null; }
  get recordedCheckpoints() { return this.recordedPerformance?.checkpoints ?? []; }
  get recordedCheckpoint() { return this.recordedCheckpoints.find(c => c.checkpointId === this.recordedCheckpointId) ?? null; }
  get recordedVersions() { return this.recordedCheckpoint?.versions ?? []; }
  recordedVersion(level: KpiLevel) { return this.recordedVersions.find(v => v.level === level); }
  selectRecordedCheckpoint(id: number) {
    if (this.recordedCheckpoints.some(c => c.checkpointId === id)) this.recordedCheckpointId = id;
  }
  showScoringDefinitions(kpi: KpiItem) { this.scoringKpi = kpi; }
  closeScoringDefinitions() { this.scoringKpi = null; }
  attitudeRatingLabel(point: number | null) { return this.data?.attitude.ratingDefinitions.find(r => r.point === point)?.label; }
  refreshDetails() { this.refresh.next(); }
  changePeriod(id: number) {
    if (this.loading || id === this.periodId) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { reviewPeriodId: id, participantId: null }, queryParamsHandling: 'merge' });
  }
  selectCheckpoint(id: number | null) {
    if (!this.data) return;
    this.checkpointId = id;
    this.checkpointRequests.next(id !== null && this.data.checkpoints.some(c => c.id === id)
      ? { participantId: this.data.period.participantId, checkpointId: id } : null);
  }
  openAppraisal(row: PerformanceApprovedAppraisal) {
    this.drawer = 'appraisal'; this.selectedAppraisal = { participantId: row.period.participantId, appraisalId: row.id };
    this.appraisalRequests.next(this.selectedAppraisal);
  }
  retryAppraisal() { this.appraisalRequests.next(this.selectedAppraisal); }
  closeDrawer() { this.drawer = null; this.scoringKpi = null; this.appraisalRequests.next(null); this.selectedAppraisal = null; }
  scrollTo(id: string) { document.getElementById(id)?.scrollIntoView({ block: 'start', behavior: 'smooth' }); }
}
