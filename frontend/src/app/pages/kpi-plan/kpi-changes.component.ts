import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiPlanChange } from '../../models/kpi-plan.model';

@Component({
  selector: 'app-kpi-changes', standalone: true, imports: [TranslateModule],
  template: `
    @for (item of changes; track $index) {
      <details class="change" open><summary><strong>{{ item.itemName || '-' }}</strong>
        <span>{{ 'REVIEW_FEEDBACK.' + item.type | translate }}</span></summary>
        <div class="comparison" role="table">
          <div class="field-change heading" role="row"><span>{{ 'KPI_REVISIONS.FIELD' | translate }}</span>
            <span>{{ 'REVIEW_FEEDBACK.BEFORE' | translate }}</span><span>{{ 'REVIEW_FEEDBACK.AFTER' | translate }}</span></div>
          @for (field of item.fields; track field.field) {
            <div class="field-change" role="row"><strong>{{ 'REVIEW_FEEDBACK.FIELD.' + field.field | translate }}</strong>
              <p>{{ field.previousValue || '-' }}</p><p class="new-value">{{ field.currentValue || '-' }}</p></div>
          }
        </div>
      </details>
    } @empty { <p class="help">{{ emptyKey | translate }}</p> }
  `,
  styles: [`
    .change { margin: 12px 0; border: 1px solid #dae3ef; border-radius: 8px; overflow: hidden; }
    summary { cursor: pointer; padding: 14px 16px; } summary span { margin-left: 12px; color: #2459a6; }
    .field-change { display: grid; grid-template-columns: 1fr 2fr 2fr; border-top: 1px solid #dae3ef; }
    .field-change > * { padding: 12px 16px; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .heading { background: #f7f9fc; color: #64748b; } .new-value { background: #edf3fd; color: #2459a6; }
    .help { color: #64748b; } @media(max-width: 600px) { .field-change { grid-template-columns: 1fr; } .heading { display: none; } }
  `]
})
export class KpiChangesComponent {
  @Input() changes: KpiPlanChange[] = [];
  @Input() emptyKey = 'REVIEW_FEEDBACK.NO_CHANGES';
}
