import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiPlan } from '../../models/kpi-plan.model';

@Component({
  selector: 'app-kpi-return-feedback', standalone: true, imports: [CommonModule, TranslateModule],
  template: `
    @if (plan.latestReturnReason || plan.returnReason) {
      <section class="feedback" aria-live="polite">
        <h3>{{ 'REVIEW_FEEDBACK.TITLE' | translate }}</h3>
        <p class="reason">{{ plan.latestReturnReason || plan.returnReason }}</p>
        <p class="metadata">{{ plan.latestReturnedByName }} @if (plan.latestReturnedAt) { {{ plan.latestReturnedAt | date:'dd MMM yyyy, h:mm a' }} }</p>
        <h4>{{ 'REVIEW_FEEDBACK.CHANGES' | translate }}</h4>
        @if (!plan.returnComparisonAvailable) { <p>{{ 'REVIEW_FEEDBACK.UNAVAILABLE' | translate }}</p> }
        @else {
          @for (item of plan.changesSinceReturn; track $index) {
            <details class="change"><summary><strong>{{ item.itemName || '-' }}</strong> <span>{{ 'REVIEW_FEEDBACK.' + item.type | translate }}</span></summary>
              <div class="comparison">
                @for (field of item.fields; track field.field) {
                  <div class="field-change"><strong>{{ 'REVIEW_FEEDBACK.FIELD.' + field.field | translate }}</strong>
                    <div><span>{{ 'REVIEW_FEEDBACK.BEFORE' | translate }}</span><p>{{ field.previousValue || '-' }}</p></div>
                    <div><span>{{ 'REVIEW_FEEDBACK.AFTER' | translate }}</span><p>{{ field.currentValue || '-' }}</p></div>
                  </div>
                }
              </div>
            </details>
          } @empty { <p>{{ 'REVIEW_FEEDBACK.NO_CHANGES' | translate }}</p> }
        }
      </section>
    }
  `,
  styles: [`
    .feedback { border: 1px solid #ead8a8; border-left: 4px solid #d99a17; border-radius: 8px; padding: 18px; margin: 20px 0; background: #fffaf0; }
    h3, h4 { margin: 0 0 10px; } .reason { white-space: pre-wrap; overflow-wrap: anywhere; }
    .metadata, .field-change span { color: #64748b; font-size: 13px; }
    .change { margin-top: 10px; padding: 10px; background: white; border: 1px solid #e2e8f0; border-radius: 6px; }
    summary { cursor: pointer; } summary span { margin-left: 10px; color: #2459a6; }
    .field-change { display: grid; grid-template-columns: 1fr 2fr 2fr; gap: 12px; border-top: 1px solid #e2e8f0; padding-top: 12px; margin-top: 12px; }
    .field-change p { white-space: pre-wrap; overflow-wrap: anywhere; margin: 4px 0; }
    @media(max-width: 600px) { .field-change { grid-template-columns: 1fr; } }
  `]
})
export class KpiReturnFeedbackComponent {
  @Input({ required: true }) plan!: KpiPlan;
}
