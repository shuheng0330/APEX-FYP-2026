import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { KpiScoreBreakdown } from '../../models/kpi-assessment.model';

export function breakdownLevelScore(level: KpiScoreBreakdown['levels'][number]): number | null {
  if (level.status !== 'AVAILABLE' || level.contribution == null || level.allocation == null || level.allocation <= 0) return null;
  // Display the unweighted score from the server's contribution; never recalculate the total.
  return level.contribution * 100 / level.allocation;
}

@Component({
  selector: 'app-kpi-score-summary', standalone: true,
  imports: [CommonModule, TranslateModule],
  styleUrls: ['../annual-review-period/review-period.scss'],
  styles: [':host { display: block; } .breakdown-summary { min-width: 520px; }'],
  template: `
    <div class="table-scroll"><table class="data-table breakdown-summary" [attr.aria-label]="'KPI_SCORE_BREAKDOWN.TITLE' | translate">
      <thead><tr><th>{{ 'MY_ASSESSMENTS.LEVEL' | translate }}</th><th>{{ 'KPI_SCORE_BREAKDOWN.ALLOCATION_LABEL' | translate }}</th>
        <th>{{ 'KPI_SCORE_BREAKDOWN.LEVEL_SCORE' | translate }}</th><th>{{ 'KPI_SCORE_BREAKDOWN.CONTRIBUTION_LABEL' | translate }}</th></tr></thead>
      <tbody>@for (level of levels; track level.level) {
        <tr><td>{{ 'INDIVIDUAL_KPI.LEVEL.' + level.level | translate }}</td><td>{{ level.allocation == null ? '-' : (level.allocation | number:'1.0-2') + '%' }}</td>
          <td>@if (level.status === 'NOT_APPLICABLE') { {{ 'KPI_SCORE_BREAKDOWN.STATUS.NOT_APPLICABLE' | translate }} }
            @else { @let score = levelScore(level);
              {{ score === null ? ('KPI_SCORE_BREAKDOWN.STATUS.PENDING' | translate) : (score | number:'1.2-2') + '%' }}
            }
          </td><td>{{ level.contribution == null ? ('KPI_SCORE_BREAKDOWN.STATUS.PENDING' | translate) : (level.contribution | number:'1.2-2') + '%' }}</td></tr>
      }</tbody>
    </table></div>
  `
})
export class KpiScoreSummaryComponent {
  @Input({ required: true }) levels: KpiScoreBreakdown['levels'] = [];
  readonly levelScore = breakdownLevelScore;
}
