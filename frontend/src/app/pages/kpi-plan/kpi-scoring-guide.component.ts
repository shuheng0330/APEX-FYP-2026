import { Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule } from 'ng-zorro-antd/modal';

@Component({
  selector: 'app-kpi-scoring-guide',
  standalone: true,
  imports: [TranslateModule, NzButtonModule, NzModalModule],
  templateUrl: './kpi-scoring-guide.component.html',
  styleUrl: './kpi-scoring-guide.component.scss'
})
export class KpiScoringGuideComponent {
  visible = false;
  readonly points = [5, 4, 3, 2, 1];
}
