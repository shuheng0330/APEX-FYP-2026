import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { AnnualReviewPeriod, REVIEW_FREQUENCIES, ReviewFrequency, weightTotal } from '../../models/annual-review-period.model';
import { FINAL_DEADLINES, SETUP_DEADLINES } from './review-period.validation';

@Component({
  selector: 'app-review-period-summary', standalone: true,
  imports: [CommonModule, TranslateModule, NzButtonModule],
  templateUrl: './review-period-summary.component.html', styleUrl: './review-period.scss'
})
export class ReviewPeriodSummaryComponent {
  @Input({ required: true }) period!: AnnualReviewPeriod;
  setupDeadlines = SETUP_DEADLINES;
  finalDeadlines = FINAL_DEADLINES;
  frequencies = REVIEW_FREQUENCIES;
  selectedFrequency: ReviewFrequency | null = null;
  total = weightTotal;
  get scheduleFrequencies(): ReviewFrequency[] { return this.frequencies.filter(f => this.period.checkpoints.some(c => c.reviewFrequency === f)); }
  get schedule() {
    const frequency = this.selectedFrequency && this.scheduleFrequencies.includes(this.selectedFrequency) ? this.selectedFrequency : this.scheduleFrequencies[0];
    return this.period.checkpoints.filter(c => c.reviewFrequency === frequency);
  }
  rolesFor(frequency: ReviewFrequency): string { return this.period.roleConfigurations.filter(r => r.reviewFrequency === frequency).map(r => r.roleName).join(', '); }
}
