import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { AttitudeRating } from '../../models/attitude-configuration.model';

@Component({
  selector: 'app-kpi-scoring-guide',
  standalone: true,
  imports: [TranslateModule, NzButtonModule, NzModalModule],
  templateUrl: './kpi-scoring-guide.component.html',
  styleUrl: './kpi-scoring-guide.component.scss'
})
export class KpiScoringGuideComponent {
  @Input() ratings: AttitudeRating[] | null = null;
  @Input() titleKey = 'KPI_SCORING_GUIDE.TITLE';
  visible = false;
  readonly points = [5, 4, 3, 2, 1];
  get orderedRatings() { return [...(this.ratings ?? [])].sort((a, b) => b.point - a.point); }
}
