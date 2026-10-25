import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiScoreBreakdown } from '../../models/kpi-assessment.model';

@Component({
  selector: 'app-company-reporting-source', standalone: true,
  imports: [CommonModule, TranslateModule],
  styles: [`.help { color: var(--tbm-muted); font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }`],
  template: `
    @if (source && compact) {
      <p class="help" role="status">{{ 'KPI_SCORE_BREAKDOWN.COMPANY_REPORTING_PERIOD' | translate }}:
        {{ source.startDate | date:(source.startDate.slice(0, 7) === source.endDate.slice(0, 7) ? 'd' : 'd MMM yyyy') }}–{{ source.endDate | date:'d MMM yyyy' }}
        ({{ ('REVIEW_PERIOD.FREQUENCY.' + source.reviewFrequency) | translate }})
      </p>
    } @else if (source) {
      <p class="help" role="status">{{ 'KPI_SCORE_BREAKDOWN.COMPANY_REPORTING_SOURCE' | translate }}:
        {{ ('REVIEW_PERIOD.FREQUENCY.' + source.reviewFrequency) | translate }} ·
        {{ source.startDate | date:'dd MMM yyyy' }} - {{ source.endDate | date:'dd MMM yyyy' }}
        @if (departmentBased) { <br>{{ 'KPI_SCORE_BREAKDOWN.COMPANY_YTD' | translate }} }
      </p>
    }
  `
})
export class CompanyReportingSourceComponent {
  @Input() source: KpiScoreBreakdown['levels'][number]['reportingCheckpoint'] = null;
  @Input() departmentBased = false;
  @Input() compact = false;
}
