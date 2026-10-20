import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AnnualAppraisalSnapshot } from '../../models/annual-appraisal.model';

@Component({
  selector: 'app-annual-appraisal-performance', standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './annual-appraisal-performance.component.html',
  styleUrls: ['../annual-review-period/review-period.scss', './annual-appraisals.component.scss']
})
export class AnnualAppraisalPerformanceComponent implements OnChanges {
  @Input({ required: true }) performance!: AnnualAppraisalSnapshot;
  @Input() frozen = false;
  @Input() contextId = 'current';
  details = false;
  checkpointId: number | null = null;
  ngOnChanges() { this.details = false; this.checkpointId = null; }
}
