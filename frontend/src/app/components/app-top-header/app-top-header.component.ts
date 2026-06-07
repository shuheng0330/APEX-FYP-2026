import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, of, Subscription } from 'rxjs';
import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzTagModule } from 'ng-zorro-antd/tag';
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
    NzIconModule, NzTagModule
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
    { prefix: '/evaluation/org-overview', page: { title: 'Organisation-Wide Performance', group: 'Performance and Appraisal' } },
    { prefix: '/evaluation/overview', page: { title: 'Team Performance', group: 'Performance and Appraisal' } },
    { prefix: '/evaluation/my-evaluation', page: { title: 'My Performance', group: 'Performance and Appraisal' } },
    { prefix: '/evaluation', page: { title: 'Perform Evaluation', group: 'Performance and Appraisal' } },
    { prefix: '/performance', page: { title: 'Staff Performance', group: 'Performance and Appraisal' } },
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
    this.page = this.pageMap.find(item => url.startsWith(item.prefix))?.page
      ?? { title: 'TBM Staff Platform', group: 'Workspace' };
  }
}
