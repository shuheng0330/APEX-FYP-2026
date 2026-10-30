import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { NotificationBellComponent } from '../notification-bell/notification-bell.component';

interface PageContext {
  title: string;
  group: string;
}

@Component({
  selector: 'app-top-header',
  standalone: true,
  imports: [
    CommonModule, NzButtonModule, NzDropDownModule,
    NzIconModule, TranslateModule, NotificationBellComponent
  ],
  templateUrl: './app-top-header.component.html',
  styleUrl: './app-top-header.component.scss'
})
export class AppTopHeaderComponent implements OnInit, OnDestroy {
  @Output() toggleDrawer = new EventEmitter<void>();

  page: PageContext = { title: 'Organisation Chart', group: 'Organisation Management' };
  private routerSubscription?: Subscription;

  private readonly pageMap: Array<{ prefix: string; page: PageContext }> = [
    { prefix: '/team-performance/dashboard', page: { title: 'TEAM_PERFORMANCE.TITLE', group: 'NAV.TEAM_PERFORMANCE' } },
    { prefix: '/my-performance/dashboard', page: { title: 'STAFF_PERFORMANCE.MY_TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/my-performance/staff/', page: { title: 'STAFF_PERFORMANCE.STAFF_TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/team-performance/team-appraisals', page: { title: 'ANNUAL_APPRAISAL.TEAM_TITLE', group: 'NAV.TEAM_PERFORMANCE' } },
    { prefix: '/appraisals/review', page: { title: 'ANNUAL_APPRAISAL.HR_TITLE', group: 'NAV.PERFORMANCE_APPRAISAL' } },
    { prefix: '/appraisals/organisation-performance', page: { title: 'ORG_PERFORMANCE.TITLE', group: 'NAV.PERFORMANCE_APPRAISAL' } },
    { prefix: '/my-performance/my-assessments', page: { title: 'MY_ASSESSMENTS.TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/my-performance/my-kpi-plan', page: { title: 'INDIVIDUAL_KPI.TITLE', group: 'NAV.MY_PERFORMANCE' } },
    { prefix: '/team-performance/team-reviews', page: { title: 'TEAM_REVIEWS.TITLE', group: 'NAV.TEAM_PERFORMANCE' } },
    { prefix: '/kpi-management/company-kpis', page: { title: 'KPI_PLAN.COMPANY_TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/department-kpis', page: { title: 'DEPARTMENT_KPI.TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/kpi-results', page: { title: 'KPI_RESULTS.TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-management/kpi-review', page: { title: 'KPI_ASSISTANCE.REVIEW_TITLE', group: 'NAV.KPI_MANAGEMENT' } },
    { prefix: '/kpi-administration/review-periods', page: { title: 'REVIEW_PERIOD.TITLE', group: 'NAV.KPI_ADMINISTRATION' } },
    { prefix: '/kpi-administration/attitude-evaluation-setup', page: { title: 'ATTITUDE_SETUP.TITLE', group: 'NAV.KPI_ADMINISTRATION' } },
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
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.updatePage(this.router.url);
    this.routerSubscription = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(event => this.updatePage((event as NavigationEnd).urlAfterRedirects));

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
