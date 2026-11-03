import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule } from 'ng-zorro-antd/modal';

@Component({
  selector: 'app-kpi-revision-guide', standalone: true,
  imports: [TranslateModule, NzButtonModule, NzModalModule],
  template: `
    <button nz-button (click)="visible = true">{{ 'KPI_REVISIONS.GUIDE.TITLE' | translate }}</button>
    <nz-modal [nzVisible]="visible" [nzTitle]="'KPI_REVISIONS.GUIDE.TITLE' | translate"
      nzWidth="min(760px, 96vw)" [nzFooter]="footer" (nzOnCancel)="visible = false">
      <ng-container *nzModalContent>
        <p class="guide-intro">{{ 'KPI_REVISIONS.GUIDE.INTRO' | translate }}</p>
        @for (level of ['COMPANY', 'DEPARTMENT', 'INDIVIDUAL_SUPERIOR', 'INDIVIDUAL_HR']; track level) {
          <section><h4>{{ 'KPI_REVISIONS.GUIDE.' + level + '_TITLE' | translate }}</h4>
            <p>{{ 'KPI_REVISIONS.GUIDE.' + level | translate }}</p></section>
        }
        <aside>
          <h4>{{ 'KPI_REVISIONS.GUIDE.RULES_TITLE' | translate }}</h4>
          <ul>
            @for (rule of ['ACCESS', 'CLOSED', 'RETURNED']; track rule) {
              <li>{{ 'KPI_REVISIONS.GUIDE.' + rule | translate }}</li>
            }
          </ul>
          <p class="guide-legend">{{ 'KPI_REVISIONS.GUIDE.STATUSES' | translate }}</p>
        </aside>
      </ng-container>
    </nz-modal>
    <ng-template #footer><button nz-button (click)="visible = false">{{ 'KPI_PLAN.CLOSE' | translate }}</button></ng-template>
  `,
  styles: [`
    .guide-intro { background: var(--tbm-surface-subtle); padding: 12px; border-radius: 8px; }
    section { border-bottom: 1px solid var(--tbm-border); padding: 12px 0; }
    h4 { margin: 0 0 4px; font-weight: 600; }
    section p { margin: 0; line-height: 1.6; overflow-wrap: anywhere; }
    aside { margin-top: 16px; }
    ul { padding-left: 20px; margin: 6px 0 12px; }
    li { margin: 4px 0; }
    .guide-legend { color: var(--tbm-muted); margin-bottom: 0; }
  `]
})
export class KpiRevisionGuideComponent { visible = false; }
