import { Component, Input, OnChanges } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiPlanChange } from '../../models/kpi-plan.model';

@Component({
  selector: 'app-kpi-changes', standalone: true, imports: [TranslateModule],
  template: `
    @if (summaryTable) {
      <p class="change-count" role="status">{{ 'KPI_REVISIONS.CHANGE_TOTAL' | translate:{ items: changes.length, fields: editedFields } }}
        @if (addedCount) { <span> · {{ 'KPI_REVISIONS.ADDED_COUNT' | translate:{ count: addedCount } }}</span> }
        @if (removedCount) { <span> · {{ 'KPI_REVISIONS.REMOVED_COUNT' | translate:{ count: removedCount } }}</span> }
      </p>
      @if (changes.length) {
        <div class="table-scroll"><table class="change-list"><thead><tr>
          <th>{{ 'KPI_PLAN.NAME' | translate }}</th><th>{{ 'KPI_REVISIONS.CHANGE_COUNT' | translate }}</th>
          <th>{{ 'KPI_REVISIONS.CHANGE_SUMMARY' | translate }}</th><th>{{ 'REVIEW_PERIOD.ACTIONS' | translate }}</th>
        </tr></thead><tbody>
          @for (item of changes; track $index; let index = $index) {
            <tr><td><strong>{{ item.itemName || '-' }}</strong>
              @if (item.type !== 'EDITED') { <span class="change-type">{{ 'REVIEW_FEEDBACK.' + item.type | translate }}</span> }</td>
              <td>{{ item.type === 'EDITED' ? item.fields.length : '-' }}</td>
              <td>@if (item.type === 'EDITED') {
                @for (field of item.fields; track field.field; let last = $last) {
                  {{ 'REVIEW_FEEDBACK.FIELD.' + field.field | translate }}@if (!last) {, }
                }
              } @else { {{ 'KPI_REVISIONS.CHANGE_' + item.type | translate }} }</td>
              <td><button type="button" class="change-link" [attr.aria-expanded]="expanded.has(index)"
                [attr.aria-controls]="'revision-change-' + instanceId + '-' + index" (click)="toggle(index)">
                {{ (expanded.has(index) ? 'KPI_REVISIONS.HIDE_CHANGES' : 'KPI_REVISIONS.VIEW_CHANGES') | translate }}</button></td></tr>
            @if (expanded.has(index)) {
              <tr [id]="'revision-change-' + instanceId + '-' + index"><td colspan="4" class="expanded-cell">
                <div class="comparison" role="table" [attr.aria-label]="item.itemName">
                  <div class="field-change heading" role="row"><span>{{ 'KPI_REVISIONS.FIELD' | translate }}</span>
                    <span>{{ 'KPI_REVISIONS.BEFORE' | translate }}</span><span>{{ 'KPI_REVISIONS.AFTER' | translate }}</span></div>
                  @for (field of item.fields; track field.field) {
                    <div class="field-change" role="row"><strong>{{ 'REVIEW_FEEDBACK.FIELD.' + field.field | translate }}</strong>
                      <p><span class="mobile-label">{{ 'KPI_REVISIONS.BEFORE' | translate }}: </span>{{ field.previousValue || '-' }}</p>
                      <p class="new-value"><span class="mobile-label">{{ 'KPI_REVISIONS.AFTER' | translate }}: </span>{{ field.currentValue || '-' }}</p></div>
                  }
                </div>
              </td></tr>
            }
          }
        </tbody></table></div>
      } @else { <p class="help">{{ emptyKey | translate }}</p> }
    } @else {
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
    }
  `,
  styles: [`
    .change { margin: 12px 0; border: 1px solid #dae3ef; border-radius: 8px; overflow: hidden; }
    summary { cursor: pointer; padding: 14px 16px; } summary span { margin-left: 12px; color: #2459a6; }
    .field-change { display: grid; grid-template-columns: 1fr 2fr 2fr; border-top: 1px solid #dae3ef; }
    .field-change > * { padding: 12px 16px; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .heading { background: #f7f9fc; color: #64748b; } .new-value { background: #edf3fd; color: #2459a6; }
    .change-count { color: var(--tbm-muted); margin: 12px 0 18px; }
    .table-scroll { overflow-x: auto; border: 1px solid var(--tbm-border); border-radius: 8px; }
    .change-list { width: 100%; border-collapse: collapse; text-align: left; }
    .change-list th { background: var(--tbm-surface-subtle); color: var(--tbm-muted); font-size: 12px; }
    .change-list th, .change-list td { padding: 14px 16px; border-bottom: 1px solid var(--tbm-border); vertical-align: top; }
    .change-list td.expanded-cell { padding: 0; } .change-list tr:last-child td { border-bottom: 0; }
    .change-link { border: 0; background: transparent; color: var(--tbm-primary); padding: 0; cursor: pointer; font: inherit; white-space: nowrap; }
    .change-link:hover { text-decoration: underline; } .change-type { display: block; color: var(--tbm-muted); font-size: 12px; }
    .mobile-label { display: none; }
    .help { color: #64748b; } @media(max-width: 600px) { .field-change { grid-template-columns: 1fr; } .heading { display: none; } }
    @media(max-width: 600px) { .mobile-label { display: inline; font-weight: 600; } }
  `]
})
export class KpiChangesComponent implements OnChanges {
  private static nextId = 0;
  readonly instanceId = KpiChangesComponent.nextId++;
  @Input() changes: KpiPlanChange[] = [];
  @Input() emptyKey = 'REVIEW_FEEDBACK.NO_CHANGES';
  @Input() summaryTable = false;
  expanded = new Set<number>();
  ngOnChanges() { this.expanded.clear(); }
  get editedFields() { return this.changes.filter(c => c.type === 'EDITED').reduce((total, c) => total + c.fields.length, 0); }
  get addedCount() { return this.changes.filter(c => c.type === 'ADDED').length; }
  get removedCount() { return this.changes.filter(c => c.type === 'REMOVED').length; }
  toggle(index: number) { if (this.expanded.has(index)) this.expanded.delete(index); else this.expanded.add(index); }
}
