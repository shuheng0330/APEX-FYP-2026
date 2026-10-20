import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, of, Subscription } from 'rxjs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { EvaluationCycleDto, EvaluationCycleService } from '../../services/evaluation-cycle.service';

interface PageContext {
  title: string;
  group: string;
}

@Component({
  selector: 'app-top-header',
  standalone: true,
  imports: [
    CommonModule, NzBadgeModule, NzButtonModule, NzDropDownModule,
    NzIconModule, NzTagModule, TranslateModule
  ],
  templateUrl: './app-top-header.component.html',
  styleUrl: './app-top-header.component.scss'
})
export class AppTopHeaderComponent implements OnInit, OnDestroy {
  @Output() toggleDrawer = new EventEmitter<void>();
  @Output() openEvaluation = new EventEmitter<void>();

  page: PageContext = { title: 'Organisation Chart', group: 'Organisation Management' };
  currentCycle: EvaluationCycleDto | null = null;
  private routerSubscription?: Subscription;

  private readonly pageMap: Array<{ prefix: string; page: PageContext }> = [
    { prefix: '/team-performance/team-appraisals', page: { title: 'ANNUAL_APPRAISAL.TEAM_TITLE', group: 'NAV.TEAM_PERFORMANCE' } },
    { prefix: '/appraisals/review', page: { title: 'ANNUAL_APPRAISAL.HR_TITLE', group: 'NAV.PERFORMANCE_APPRAISAL' } },
    { prefix: '/my-performance/my-assessments', page: { title: 'MY_ASSESSMENTS.TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/my-performance/my-kpi-plan', page: { title: 'INDIVIDUAL_KPI.TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/team-performance/team-reviews', page: { title: 'TEAM_REVIEWS.TITLE', group: 'NAV.TEAM_PERFORMANCE' } },
    { prefix: '/kpi-management/company-kpis', page: { title: 'KPI_PLAN.COMPANY_TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/department-kpis', page: { title: 'DEPARTMENT_KPI.TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/kpi-results', page: { title: 'KPI_RESULTS.TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/kpi-review', page: { title: 'KPI_ASSISTANCE.REVIEW_TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-administration/review-periods', page: { title: 'REVIEW_PERIOD.TITLE', group: 'NAV.KPI_ADMINISTRATION' } },
    { prefix: '/kpi-administration/attitude-evaluation-setup', page: { title: 'ATTITUDE_SETUP.TITLE', group: 'NAV.KPI_ADMINISTRATION' } },
    { prefix: '/evaluation/org-overview', page: { title: 'Organisation-Wide Performance', group: 'NAV.LEGACY_COMPETENCY_PERFORMANCE' } },
    { prefix: '/evaluation/overview', page: { title: 'Team Performance', group: 'NAV.LEGACY_COMPETENCY_PERFORMANCE' } },
    { prefix: '/evaluation/my-evaluation', page: { title: 'My Performance', group: 'NAV.LEGACY_COMPETENCY_PERFORMANCE' } },
    { prefix: '/evaluation', page: { title: 'Perform Evaluation', group: 'NAV.LEGACY_COMPETENCY_PERFORMANCE' } },
    { prefix: '/performance', page: { title: 'Staff Performance', group: 'NAV.LEGACY_COMPETENCY_PERFORMANCE' } },
    { prefix: '/sop/upload', page: { title: 'SOP Automation Workspace', group: 'Training Automation' } },
    { prefix: '/sop/review', page: { title: 'Generated Training Review', group: 'Training Automation' } },
    { prefix: '/roles-competencies', page: { title: 'Roles and Competencies', group: 'Organisation Management' } },
    { prefix: '/competencies', page: { title: 'Competencies', group: 'Organisation Management' } },
    { prefix: '/roles', page: { title: 'Roles', group: 'Organisation Management' } },
    { prefix: '/staff', page: { title: 'Staff', group: 'Organisation Management' } },
    { prefix: '/profile', page: { title: 'Profile', group: 'Account' } },
    { prefix: '/org-chart', page: { title: 'Organisation Chart', group: 'Organisation Management' } }
  ];

  constructor(
    private router: Router,
    private authService: AuthService,
    private evaluationCycleService: EvaluationCycleService
  ) {}

  ngOnInit(): void {
    this.updatePage(this.router.url);
    this.routerSubscription = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(event => this.updatePage((event as NavigationEnd).urlAfterRedirects));

    if (this.authService.hasRole('CAN_MANAGE_EVALUATION_CYCLE')) {
      this.evaluationCycleService.getCurrentCycle().pipe(
        catchError(() => of(null))
      ).subscribe(cycle => this.currentCycle = cycle);
    }
  }

  ngOnDestroy(): void {
    this.routerSubscription?.unsubscribe();
  }

  goToProfile(): void {
    this.router.navigate(['/profile']);
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }

  get canManageCycles(): boolean {
    return this.authService.hasRole('CAN_MANAGE_EVALUATION_CYCLE');
  }

  private updatePage(url: string): void {
    const path = url.split(/[?#]/)[0];
    if (/^\/team-performance\/team-appraisals\/[^/]+\/[^/]+\/?$/.test(path)) {
      this.page = { title: 'ANNUAL_APPRAISAL.DETAIL_TITLE', group: 'NAV.TEAM_PERFORMANCE' }; return;
    }
    if (/^\/appraisals\/review\/[^/]+\/?$/.test(path)) {
      this.page = { title: 'ANNUAL_APPRAISAL.REVIEW_DETAIL_TITLE', group: 'NAV.PERFORMANCE_APPRAISAL' }; return;
    }
    this.page = this.pageMap.find(item => url.startsWith(item.prefix))?.page
      ?? { title: 'TBM Staff Platform', group: 'Workspace' };
  }
}
