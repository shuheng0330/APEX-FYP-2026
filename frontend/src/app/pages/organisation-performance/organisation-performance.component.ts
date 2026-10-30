import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { Subject, catchError, defer, distinctUntilChanged, finalize, map, merge, of, switchMap, takeUntil } from 'rxjs';
import { OrganisationDepartment, OrganisationPerformance } from '../../models/organisation-performance.model';
import { TeamAverage, TeamChange, TeamMetric, TeamPeriod } from '../../models/team-performance.model';
import { OrganisationPerformanceService } from '../../services/organisation-performance.service';
import { performanceId } from '../staff-performance/staff-performance-navigation';
import { PerformanceTrendSeries, TeamPerformanceTrendComponent } from '../team-performance/team-performance-trend.component';
import { organisationPerformancePath } from './organisation-performance-navigation';
import { OrganisationScoreDistributionComponent } from './organisation-score-distribution.component';

type Sort = 'name' | 'employees' | 'kpi' | 'attitude' | 'final' | 'change';
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslateModule, NzButtonModule, NzInputModule, NzSelectModule, NzToolTipModule, TeamPerformanceTrendComponent, OrganisationScoreDistributionComponent],
  templateUrl: './organisation-performance.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', '../team-performance/team-performance.component.scss', './organisation-performance.component.scss']
})
export class OrganisationPerformanceComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly refreshRequest = new Subject<void>();
  private readonly cancelExport = new Subject<void>();
  private refreshVersion = 0;
  readonly path = organisationPerformancePath;
  readonly metrics: TeamMetric[] = ['FINAL', 'KPI', 'ATTITUDE'];
  readonly scoreFields = ['kpi', 'attitude', 'finalScore'] as const;
  readonly progressFields = ['kpiSelfAssessments', 'kpiSuperiorReviews', 'attitudeSelfAssessments', 'attitudeSuperiorReviews', 'returnedIndividualPlans'] as const;
  readonly columns: Sort[] = ['name', 'employees', 'kpi', 'attitude', 'final', 'change'];
  periods: TeamPeriod[] = []; periodId: number | null = null; data: OrganisationPerformance | null = null;
  years: 3 | 5 = 5; metric: TeamMetric = 'FINAL';
  loading = false; error = false; empty = false; exporting = false; exportError = false;
  search = ''; sort: Sort = 'name'; descending = false;
  decisionKind: 'superiorRecommendations' | 'hrFinalDecisions' = 'superiorRecommendations';
  private comparisonKeys: string[] | null = null;
  selectedDepartments: string[] = [];
  comparisonSeries: PerformanceTrendSeries[] = [];
  constructor(private api: OrganisationPerformanceService, private router: Router, private translate: TranslateService) {}

  ngOnInit() {
    merge(this.route.queryParamMap, this.refreshRequest.pipe(map(() => this.route.snapshot.queryParamMap))).pipe(
      map(query => {
        this.search = query.get('search') ?? '';
        this.sort = this.columns.includes(query.get('sort') as Sort) ? query.get('sort') as Sort : 'name';
        this.descending = query.get('direction') === 'desc';
        this.decisionKind = query.get('decisions') === 'hrFinalDecisions' ? 'hrFinalDecisions' : 'superiorRecommendations';
        this.comparisonKeys = query.has('compare') ? query.getAll('compare').filter(Boolean) : null;
        this.updateComparison();
        return { query, key: JSON.stringify(['reviewPeriodId', 'historyYears', 'metric'].map(k => query.getAll(k))) + this.refreshVersion };
      }), distinctUntilChanged((a, b) => a.key === b.key),
      switchMap(({ query }) => {
        this.cancelExport.next(); this.exportError = false;
        this.loading = true; this.error = false; this.empty = false; this.data = null;
        this.periodId = null; this.periods = []; this.comparisonSeries = [];
        return defer(() => {
          const id = performanceId(query, 'reviewPeriodId');
          const years = this.one(query, 'historyYears') ?? '5', metric = this.one(query, 'metric') ?? 'FINAL';
          if (!['3', '5'].includes(years) || !this.metrics.includes(metric as TeamMetric)) throw new Error('Invalid selection');
          this.years = Number(years) as 3 | 5; this.metric = metric as TeamMetric;
          return this.api.periods().pipe(switchMap(periods => {
            this.periods = [...periods].sort((a, b) => b.endDate.localeCompare(a.endDate) || b.id - a.id);
            const period = id === null ? this.periods.find(p => p.status === 'OPEN') ?? this.periods[0] : this.periods.find(p => p.id === id);
            if (!period) { if (id !== null) throw new Error('Unavailable period'); this.empty = true; return of(null); }
            this.periodId = period.id;
            return this.api.dashboard(period.id, this.years, this.metric).pipe(map(data => {
              if (data.period.id !== period.id || data.historyYears !== this.years || data.distribution.metric !== this.metric) throw new Error('Mismatched response');
              return data;
            }));
          }));
        }).pipe(catchError(() => { this.error = true; return of(null); }), finalize(() => this.loading = false));
      }), takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => { this.data = data; this.updateComparison(); });
  }
  private one(query: ParamMap, key: string) {
    if (query.getAll(key).length > 1) throw new Error('Duplicate selection');
    return query.get(key);
  }
  get context() {
    return { reviewPeriodId: this.periodId, historyYears: this.years, metric: this.metric, search: this.search || null,
      sort: this.sort, direction: this.descending ? 'desc' : 'asc', decisions: this.decisionKind, compare: this.comparisonKeys === null ? null : this.comparisonKeys.length ? this.comparisonKeys : [''] };
  }
  changeSelection(changes: Record<string, string | number | string[] | null>, replaceUrl = false) {
    return this.router.navigate([this.path], { queryParams: { ...this.context, ...changes }, replaceUrl });
  }
  refresh() { this.refreshVersion++; this.refreshRequest.next(); }
  setSort(sort: Sort) { this.changeSelection({ sort, direction: this.sort === sort && !this.descending ? 'desc' : 'asc' }, true); }
  departmentKey(id: number | null) { return id === null ? 'unassigned' : String(id); }
  private updateComparison() {
    const departments = this.data?.departmentTrends ?? [];
    this.selectedDepartments = departments.filter(d => this.comparisonKeys === null ? departments.indexOf(d) < 3 : this.comparisonKeys.includes(this.departmentKey(d.departmentId)))
      .map(d => this.departmentKey(d.departmentId));
    this.comparisonSeries = departments.filter(d => this.selectedDepartments.includes(this.departmentKey(d.departmentId)))
      .map(d => ({ name: d.name, points: d.periods }));
  }
  get rows() {
    const term = this.search.trim().toLocaleLowerCase();
    return (this.data?.departments ?? []).filter(d => !term || d.name.toLocaleLowerCase().includes(term)).sort((a, b) => {
      if (this.sort === 'name') return (this.descending ? -1 : 1) * a.name.localeCompare(b.name);
      const value = (d: OrganisationDepartment) => this.sort === 'employees' ? d.summary.employeesWithResults : this.sort === 'change' ? this.changeValue(d.yearOnYear)
        : this.sort === 'kpi' ? d.summary.kpi.score : this.sort === 'attitude' ? d.summary.attitude.score : d.summary.finalScore.score;
      const av = value(a), bv = value(b);
      return (av === null ? (bv === null ? 0 : 1) : bv === null ? -1 : (av - bv) * (this.descending ? -1 : 1)) || a.name.localeCompare(b.name);
    });
  }
  teamLink(department: OrganisationDepartment) {
    return this.router.createUrlTree(['/team-performance/dashboard'], { queryParams: { scope: 'DEPARTMENT', departmentId: department.departmentId,
      reviewPeriodId: this.periodId, historyYears: this.years, metric: this.metric,
      returnUrl: this.router.serializeUrl(this.router.createUrlTree([this.path], { queryParams: this.context })) } });
  }
  changeValue(change: TeamChange) { return change.status === 'AVAILABLE' ? change.percentagePoints : null; }
  sampleDescription(field: typeof this.scoreFields[number], average: TeamAverage) {
    return this.translate.instant('ORG_PERFORMANCE.SAMPLES.' + field + (average.contributingEmployees === 1 ? '_ONE' : '_OTHER'), { count: average.contributingEmployees });
  }
  get decisions() { return this.data?.appraisalDistribution[this.decisionKind]; }
  decisionPercent(count: number) {
    const series = this.decisions;
    return series?.disclosure === 'AVAILABLE' && series.total ? count / series.total * 100 : 0;
  }
  exportExcel() {
    if (!this.data || this.loading || this.exporting) return;
    const periodId = this.data.period.id;
    this.exporting = true; this.exportError = false;
    this.api.export(periodId, this.years, this.metric).pipe(takeUntil(this.cancelExport), takeUntilDestroyed(this.destroyRef), finalize(() => this.exporting = false))
      .subscribe({ next: blob => {
        if (!blob.size || blob.type !== 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') { this.exportError = true; return; }
        this.download(blob, `organisation-performance-period-${periodId}.xlsx`);
      }, error: () => this.exportError = true });
  }
  download(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = filename; document.body.appendChild(link);
    try { link.click(); } finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 0); }
  }
}
