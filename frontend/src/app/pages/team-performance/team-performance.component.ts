import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzPaginationModule } from 'ng-zorro-antd/pagination';
import { Subject, catchError, distinctUntilChanged, finalize, map, merge, of, switchMap } from 'rxjs';
import { TeamChange, TeamEmployee, TeamMetric, TeamPerformance, TeamPeriod, TeamScope } from '../../models/team-performance.model';
import { TeamPerformanceService } from '../../services/team-performance.service';
import { AuthService } from '../../services/auth.service';
import { performanceId } from '../staff-performance/staff-performance-navigation';
import { TeamPerformanceTrendComponent } from './team-performance-trend.component';
import { organisationReturnUrl } from '../organisation-performance/organisation-performance-navigation';

type Sort = 'name' | 'kpi' | 'attitude' | 'final' | 'change';
@Component({
  standalone: true, imports: [CommonModule, FormsModule, RouterModule, TranslateModule, NzButtonModule, NzInputModule,
    NzSelectModule, NzPaginationModule, TeamPerformanceTrendComponent],
  templateUrl: './team-performance.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './team-performance.component.scss']
})
export class TeamPerformanceComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly refreshRequest = new Subject<void>();
  private refreshVersion = 0;
  scopes: TeamScope[] = []; periods: TeamPeriod[] = []; scope: TeamScope | null = null;
  periodId: number | null = null; data: TeamPerformance | null = null;
  loading = false; error = false; empty: 'scopes' | 'periods' | null = null;
  years: 3 | 5 = 5; metric: TeamMetric = 'FINAL'; readonly metrics: TeamMetric[] = ['FINAL', 'KPI', 'ATTITUDE'];
  search = ''; role = ''; result = 'ALL'; sort: Sort = 'name'; descending = false; page = 1; readonly pageSize = 10;
  progressOpen = false;
  returnUrl: string | null = null;
  readonly scoreFields = ['kpi', 'attitude', 'finalScore'] as const;
  readonly columns: Sort[] = ['name', 'kpi', 'attitude', 'final', 'change'];
  readonly progressFields = ['kpiSelfAssessments', 'kpiSuperiorReviews', 'attitudeSelfAssessments', 'attitudeSuperiorReviews', 'returnedIndividualPlans'] as const;
  private readonly actionRules = {
    KPI_REVIEWS: { count: 'pendingKpiReviews', tab: 'assessments', permission: 'CAN_REVIEW_KPI_ASSESSMENT' },
    ATTITUDE_REVIEWS: { count: 'pendingAttitudeReviews', tab: 'attitude', permission: 'CAN_REVIEW_ATTITUDE_EVALUATION' },
    INDIVIDUAL_PLAN_APPROVALS: { count: 'pendingIndividualPlanApprovals', tab: 'reviews', permission: 'CAN_REVIEW_INDIVIDUAL_KPI' },
    RETURNED_INDIVIDUAL_REVISIONS: { count: 'returnedIndividualRevisions', tab: 'revisions', permission: 'CAN_REVIEW_INDIVIDUAL_KPI' }
  } as const;
  constructor(private api: TeamPerformanceService, private router: Router, private auth: AuthService) {}

  ngOnInit() {
    merge(this.route.queryParamMap, this.refreshRequest.pipe(map(() => this.route.snapshot.queryParamMap))).pipe(
      map(query => {
        this.returnUrl = query.getAll('returnUrl').length === 1 ? organisationReturnUrl(query.get('returnUrl')) : null;
        this.search = query.get('search') || ''; this.role = query.get('role') || '';
        this.result = ['ALL', 'AVAILABLE', 'PENDING'].includes(query.get('result') || '') ? query.get('result')! : 'ALL';
        this.sort = ['name', 'kpi', 'attitude', 'final', 'change'].includes(query.get('sort') || '') ? query.get('sort') as Sort : 'name';
        this.descending = query.get('direction') === 'desc';
        const page = Number(query.get('page')); this.page = Number.isSafeInteger(page) && page > 0 ? page : 1;
        return { query, key: JSON.stringify(['scope', 'departmentId', 'reviewPeriodId', 'historyYears', 'metric'].map(k => query.getAll(k))) + this.refreshVersion };
      }),
      distinctUntilChanged((a, b) => a.key === b.key),
      switchMap(({ query }) => {
        this.loading = true; this.error = false; this.empty = null; this.data = null; this.progressOpen = false;
        this.scopes = []; this.periods = []; this.scope = null; this.periodId = null;
        return this.api.scopes().pipe(switchMap(scopes => {
          this.scopes = scopes;
          const type = this.one(query, 'scope'); const departmentId = performanceId(query, 'departmentId');
          const periodId = performanceId(query, 'reviewPeriodId');
          const years = this.one(query, 'historyYears') ?? '5'; const metric = this.one(query, 'metric') ?? 'FINAL';
          if (!['3', '5'].includes(years) || !this.metrics.includes(metric as TeamMetric) || (type !== null && !['TEAM', 'DEPARTMENT'].includes(type)) || (!type && departmentId !== null)) throw new Error('Invalid selection');
          this.years = Number(years) as 3 | 5; this.metric = metric as TeamMetric;
          const selected = type ? scopes.find(s => s.type === type && s.departmentId === departmentId) : scopes[0];
          if (!selected) { if (type) throw new Error('Unavailable scope'); this.empty = 'scopes'; return of(null); }
          this.scope = selected;
          return this.api.periods(selected).pipe(switchMap(periods => {
            this.periods = [...periods].sort((a, b) => b.endDate.localeCompare(a.endDate) || b.id - a.id);
            const period = periodId === null ? this.periods.find(p => p.status === 'OPEN') ?? this.periods[0] : this.periods.find(p => p.id === periodId);
            if (!period) { if (periodId !== null) throw new Error('Unavailable period'); this.empty = 'periods'; return of(null); }
            this.periodId = period.id;
            return this.api.dashboard(selected, period.id, this.years, this.metric).pipe(map(data => {
              if (data.scope.type !== selected.type || data.scope.departmentId !== selected.departmentId || data.period.id !== period.id || data.distribution.metric !== this.metric) throw new Error('Mismatched response');
              return data;
            }));
          }));
        }), catchError(() => { this.error = true; return of(null); }), finalize(() => this.loading = false));
      }), takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => this.data = data);
  }
  private one(query: ParamMap, key: string) {
    if (query.getAll(key).length > 1) throw new Error('Duplicate selection');
    return query.get(key);
  }
  scopeKey(scope: TeamScope) { return `${scope.type}:${scope.departmentId ?? ''}`; }
  get returnLink() { return this.returnUrl ? this.router.parseUrl(this.returnUrl) : null; }
  changeScope(key: string) {
    const scope = this.scopes.find(s => this.scopeKey(s) === key);
    if (scope) this.navigate({ scope: scope.type, departmentId: scope.departmentId, reviewPeriodId: null, role: null, search: null, page: 1 });
  }
  changePeriod(id: number) { this.navigate({ reviewPeriodId: id, page: 1 }); }
  changeMetric(metric: TeamMetric) { this.navigate({ metric }); }
  changeYears(years: number) { this.navigate({ historyYears: years }); }
  refresh() { this.refreshVersion++; this.refreshRequest.next(); }
  filtersChanged() { this.navigate({ search: this.search || null, role: this.role || null, result: this.result, page: 1 }, true); }
  setSort(sort: Sort) { this.navigate({ sort, direction: this.sort === sort && !this.descending ? 'desc' : 'asc', page: 1 }, true); }
  changePage(page: number) { this.navigate({ page }, true); }
  private navigate(changes: Record<string, string | number | null>, replaceUrl = false) {
    return this.router.navigate(['/team-performance/dashboard'], { queryParams: { ...this.context, ...changes }, replaceUrl });
  }
  get context() {
    return { scope: this.scope?.type, departmentId: this.scope?.departmentId, reviewPeriodId: this.periodId, historyYears: this.years,
      metric: this.metric, search: this.search || null, role: this.role || null, result: this.result, sort: this.sort,
      direction: this.descending ? 'desc' : 'asc', page: this.currentPage, returnUrl: this.returnUrl };
  }
  get roles() { return [...new Set((this.data?.employees ?? []).map(e => e.roleName).filter((r): r is string => !!r))].sort(); }
  get filteredEmployees() {
    const term = this.search.trim().toLocaleLowerCase();
    return (this.data?.employees ?? []).filter(e => (!term || [e.name, e.roleName, e.departmentName].some(v => v?.toLocaleLowerCase().includes(term)))
      && (!this.role || e.roleName === this.role) && (this.result === 'ALL' || (this.result === 'AVAILABLE' ? e.scores.finalScore !== null : e.scores.finalScore === null)))
      .sort((a, b) => {
        if (this.sort === 'name') return (this.descending ? -1 : 1) * (a.name || '').localeCompare(b.name || '') || a.participantId - b.participantId;
        const value = (e: TeamEmployee) => this.sort === 'change' ? e.yearOnYear.percentagePoints
          : this.sort === 'kpi' ? e.scores.annualKpiScore : this.sort === 'attitude' ? e.scores.attitudeScore : e.scores.finalScore;
        const av = value(a), bv = value(b);
        return (av === null ? (bv === null ? 0 : 1) : bv === null ? -1 : (av - bv) * (this.descending ? -1 : 1)) || a.participantId - b.participantId;
      });
  }
  get currentPage() { return Math.min(this.page, Math.max(1, Math.ceil(this.filteredEmployees.length / this.pageSize))); }
  get rows() { return this.filteredEmployees.slice((this.currentPage - 1) * this.pageSize, this.currentPage * this.pageSize); }
  employeeLink(employee: TeamEmployee) {
    return this.router.createUrlTree(['/my-performance/staff', employee.employeeId], { queryParams: {
      reviewPeriodId: this.periodId, participantId: employee.participantId,
      returnUrl: this.router.serializeUrl(this.router.createUrlTree(['/team-performance/dashboard'], { queryParams: this.context }))
    } });
  }
  get actions() {
    const actions = this.data?.attention.yourActions;
    if (!actions) return [];
    return actions.links.flatMap(link => {
      const rule = this.actionRules[link.task as keyof typeof this.actionRules];
      if (!rule || !this.auth.hasRole(rule.permission) || actions[rule.count] <= 0 || link.path !== '/team-performance/team-reviews'
        || link.parameters['tab'] !== rule.tab || link.parameters['reviewPeriodId'] !== String(this.periodId)) return [];
      return [{ task: link.task, count: actions[rule.count], parameters: { tab: rule.tab, reviewPeriodId: this.periodId } }];
    });
  }
  changeValue(change: TeamChange) { return change.status === 'AVAILABLE' ? change.percentagePoints : null; }
  binPercent(count: number) { return this.data?.distribution.employeesWithResults ? count / this.data.distribution.employeesWithResults * 100 : 0; }
}
