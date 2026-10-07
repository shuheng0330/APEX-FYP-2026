import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { KpiItem, emptyKpiItem } from '../../models/kpi-plan.model';
@Component({
  selector: 'app-kpi-item-editor', standalone: true,
  imports: [FormsModule, TranslateModule, NzButtonModule, NzInputModule],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  template: `
    <p class="notice">{{ 'KPI_PLAN.WEIGHT_HELP' | translate }}</p>
    @for (item of items; track item; let index = $index) {
      <section class="card">
        <div class="card-head actions"><h3>{{ 'KPI_PLAN.ITEM' | translate }} {{ index + 1 }}</h3>
          @if (!readonly) { <button nz-button nzDanger (click)="items.splice(index, 1)">{{ 'KPI_PLAN.REMOVE' | translate }}</button> }
        </div>
        <div class="card-body grid">
          <label class="field">{{ 'KPI_PLAN.NAME' | translate }}<input nz-input [disabled]="readonly" maxlength="255" [(ngModel)]="item.name"></label>
          <label class="field">{{ 'KPI_PLAN.WEIGHT' | translate }}<input nz-input type="number" min="0" max="100" step="0.01" [disabled]="readonly" [(ngModel)]="item.weightage"></label>
          <label class="field">{{ 'KPI_PLAN.PERSPECTIVE' | translate }}<input nz-input [disabled]="readonly" maxlength="255" [(ngModel)]="item.perspective"></label>
          <label class="field">{{ 'KPI_PLAN.KRA' | translate }}<input nz-input [disabled]="readonly" maxlength="255" [(ngModel)]="item.kra"></label>
          <label class="field">{{ 'KPI_PLAN.TARGET' | translate }}<textarea nz-input [disabled]="readonly" maxlength="10000" [(ngModel)]="item.target"></textarea></label>
          <label class="field">{{ 'KPI_PLAN.UNIT' | translate }}<input nz-input [disabled]="readonly" maxlength="100" [(ngModel)]="item.measurementUnit"></label>
          <label class="field span-two">{{ 'KPI_PLAN.DESCRIPTION' | translate }}<textarea nz-input [disabled]="readonly" maxlength="10000" [(ngModel)]="item.description"></textarea></label>
          <details class="span-two" [open]="!readonly"><summary>{{ 'KPI_PLAN.SCORING' | translate }}</summary>
            <p class="help">{{ 'KPI_PLAN.SCORING_HELP' | translate }}</p>
            @for (point of points; track point) {
              <label class="criterion">{{ 'KPI_PLAN.POINT' | translate }} {{ point }}<textarea nz-input [disabled]="readonly" maxlength="10000"
                [ngModel]="item.scoringDefinitions[point]" (ngModelChange)="setCriterion(item, point, $event)"></textarea></label>
            }
          </details>
        </div>
      </section>
    }
    @if (!readonly) { <button nz-button (click)="items.push(newItem())">{{ 'KPI_PLAN.ADD_ITEM' | translate }}</button> }
    <p class="total" [class.valid]="total === 100" [class.invalid]="total !== 100">{{ 'KPI_PLAN.TOTAL' | translate }}: {{ total }}% / 100%</p>
  `
})
export class KpiItemEditorComponent {
  @Input() items: KpiItem[] = [];
  @Input() readonly = false;
  points = [1, 2, 3, 4, 5];
  newItem = emptyKpiItem;
  get total() { return Math.round(this.items.reduce((n, i) => n + (i.weightage ?? 0), 0) * 100) / 100; }
  setCriterion(item: KpiItem, point: number, value: string) {
    if (value?.trim()) item.scoringDefinitions[point] = value;
    else delete item.scoringDefinitions[point];
  }
}
