import { Component, Input, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzIconModule, NzIconService } from 'ng-zorro-antd/icon';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { InfoCircleOutline } from '@ant-design/icons-angular/icons';
import { KpiItem, KpiItemErrors, emptyKpiItem } from '../../models/kpi-plan.model';
import { KpiScoringGuideComponent } from './kpi-scoring-guide.component';
@Component({
  selector: 'app-kpi-item-editor', standalone: true,
  imports: [FormsModule, TranslateModule, NzButtonModule, NzInputModule, NzIconModule, NzToolTipModule, KpiScoringGuideComponent],
  styleUrls: ['../annual-review-period/review-period.scss', './kpi-plan.scss'],
  templateUrl: './kpi-item-editor.component.html'
})
export class KpiItemEditorComponent {
  @Input() items: KpiItem[] = [];
  @Input() readonly = false;
  @Input() scope: 'COMPANY' | 'DEPARTMENT' | 'INDIVIDUAL' = 'COMPANY';
  @Input() singleItem = false;
  @Input() errors: KpiItemErrors = {};
  readonly fields: { key: 'perspective' | 'kra' | 'name' | 'target'; label: string; tooltip?: string }[] = [
    { key: 'perspective', label: 'PERSPECTIVE', tooltip: 'PERSPECTIVE_HELP' },
    { key: 'kra', label: 'KRA', tooltip: 'KRA_HELP' },
    { key: 'name', label: 'NAME' },
    { key: 'target', label: 'TARGET' }
  ];
  readonly points = [5, 4, 3, 2, 1];
  newItem = emptyKpiItem;
  constructor() { inject(NzIconService).addIcon(InfoCircleOutline); }
  get total() { return Math.round(this.items.reduce((n, i) => n + (i.weightage ?? 0), 0) * 100) / 100; }
  placeholder(field: string) {
    if (field === 'name' && this.scope !== 'COMPANY') return this.scope === 'INDIVIDUAL'
      ? 'INDIVIDUAL_KPI.NAME_PLACEHOLDER' : 'DEPARTMENT_KPI.NAME_PLACEHOLDER';
    return 'KPI_PLAN.PLACEHOLDER.' + field;
  }
  get weightHelp() {
    return this.scope === 'INDIVIDUAL' ? 'INDIVIDUAL_KPI.WEIGHT_FIELD_HELP'
      : this.scope === 'DEPARTMENT' ? 'DEPARTMENT_KPI.WEIGHT_FIELD_HELP' : 'KPI_PLAN.WEIGHT_FIELD_HELP';
  }
  clearError(field: string) { delete this.errors[field]; }
  preventNonNumeric(event: KeyboardEvent) {
    if (!event.ctrlKey && !event.metaKey && event.key.length === 1 && !/[0-9.]/.test(event.key)) event.preventDefault();
  }
  setCriterion(item: KpiItem, point: number, value: string) {
    this.clearError('point' + point);
    if (value?.trim()) item.scoringDefinitions[point] = value;
    else delete item.scoringDefinitions[point];
  }
}
