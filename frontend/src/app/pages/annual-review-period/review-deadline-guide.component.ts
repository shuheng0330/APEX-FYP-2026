import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-review-deadline-guide', standalone: true,
  imports: [TranslateModule],
  template: `
    <details class="deadline-guide">
      <summary>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.TITLE' | translate }}</summary>
      <div class="guide-content">
        <div class="guide-track">
          <strong>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.KPI' | translate }}</strong>
          <ol>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.CHECKPOINT_END' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.KPI_SELF' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.KPI_SUPERIOR' | translate }}</li>
          </ol>
        </div>
        <div class="guide-track">
          <strong>{{ 'REVIEW_PERIOD.ATTITUDE_SCHEDULE' | translate }}</strong>
          <ol>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.ATTITUDE_OPENS' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.ATTITUDE_SELF' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.ATTITUDE_SUPERIOR' | translate }}</li>
          </ol>
        </div>
        <div class="guide-track appraisal-track">
          <strong>{{ 'REVIEW_PERIOD.APPRAISAL_SCHEDULE' | translate }}</strong>
          <ol>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.RESULTS_COMPLETE' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.RECOMMENDATION' | translate }}</li>
            <li>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.HR' | translate }}</li>
          </ol>
        </div>
        <p>{{ 'REVIEW_PERIOD.DEADLINE_GUIDE.TARGET_DATES' | translate }}</p>
      </div>
    </details>
  `,
  styleUrl: './review-deadline-guide.component.scss'
})
export class ReviewDeadlineGuideComponent {}
