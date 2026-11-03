import { Component, DestroyRef, Input, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { AuthService } from '../../services/auth.service';
import { ReviewTask, organisationReviewPath, reviewTasks, teamReviewPath } from './review-task-directory';

@Component({
  selector: 'app-review-task-guide', standalone: true,
  imports: [TranslateModule, NzButtonModule, NzModalModule, RouterLink],
  template: `
    <button nz-button type="button" [disabled]="disabled" (click)="visible = true">{{ 'REVIEW_NAV.TITLE' | translate }}</button>
    <nz-modal [nzVisible]="visible" [nzTitle]="'REVIEW_NAV.TITLE' | translate" nzWidth="min(780px, 96vw)"
      [nzFooter]="footer" (nzOnCancel)="visible = false">
      <ng-container *nzModalContent>
        <div class="task-guide">
          <p class="guide-help">{{ 'REVIEW_NAV.HELP' | translate }}</p>
          <ul>
            @for (task of tasks; track task.id) {
              <li><span>{{ 'REVIEW_NAV.TASK.' + task.id | translate }}</span>
                <a [routerLink]="task.path" [queryParams]="task.tab ? { tab: task.tab } : null"
                  (click)="closeIfCurrent(task)"
                  [queryParamsHandling]="task.path === currentPath ? 'merge' : ''">
                  @if (task.path === organisationPath) { {{ 'KPI_ASSISTANCE.REVIEW_TITLE' | translate }} <span aria-hidden="true"> / </span> }
                  @else if (task.path === teamPath) { {{ 'TEAM_REVIEWS.TITLE' | translate }} <span aria-hidden="true"> / </span> }
                  @else if (task.id === 'MY_PLAN' || task.id === 'MY_ASSESSMENTS') { {{ 'NAV.MY_PERFORMANCE' | translate }} <span aria-hidden="true"> / </span> }
                  {{ task.destination | translate }}
                </a>
              </li>
            } @empty { <li>{{ 'REVIEW_NAV.NO_TASKS' | translate }}</li> }
          </ul>
          <p class="guide-help">{{ 'REVIEW_NAV.SCOPE_HELP' | translate }}</p>
          @if (hasSharedRevisions) { <p class="guide-help">{{ 'REVIEW_NAV.SAME_REQUEST' | translate }}</p> }
        </div>
      </ng-container>
    </nz-modal>
    <ng-template #footer><button nz-button type="button" (click)="visible = false">{{ 'KPI_PLAN.CLOSE' | translate }}</button></ng-template>
  `,
  styleUrl: './review-task-guide.component.scss'
})
export class ReviewTaskGuideComponent {
  @Input() disabled = false;
  visible = false;
  readonly organisationPath = organisationReviewPath;
  readonly teamPath = teamReviewPath;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  get currentPath() { return this.router.url.split('?')[0]; }
  get tasks() { return reviewTasks(permission => this.auth.hasRole(permission)); }
  get hasSharedRevisions() {
    return this.tasks.some(task => task.path === this.organisationPath && task.tab === 'revisions')
      && this.tasks.some(task => task.path === this.teamPath && task.tab === 'revisions');
  }
  closeIfCurrent(task: ReviewTask) {
    if (task.path === this.currentPath && task.tab === this.router.parseUrl(this.router.url).queryParams['tab']) this.visible = false;
  }
  constructor() {
    this.router.events.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(event => {
      if (event instanceof NavigationEnd) this.visible = false;
    });
  }
}
