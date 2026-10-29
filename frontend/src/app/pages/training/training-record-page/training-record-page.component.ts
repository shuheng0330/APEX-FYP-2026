import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { TrainingRecordComponent } from '../training-record/training-record.component';

/** Another staff member's training record, opened from the Training Scoreboard by HR or their manager (FR-18-01). */
@Component({
  selector: 'app-training-record-page',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzIconModule, TrainingRecordComponent],
  template: `
    <div class="page-shell">
      <div class="page-heading">
        <div>
          <span class="section-kicker">Training records</span>
          <h1>Training record</h1>
          <p>Every course and session, with the hours and certificates.</p>
        </div>
        <button nz-button (click)="back()"><nz-icon nzType="arrow-left"></nz-icon> Back</button>
      </div>
      <app-training-record [staffId]="staffId" [showPerson]="true"></app-training-record>
    </div>
  `
})
export class TrainingRecordPageComponent {
  readonly staffId: string | null;

  constructor(route: ActivatedRoute, private location: Location) {
    this.staffId = route.snapshot.paramMap.get('staffId');
  }

  back(): void {
    this.location.back();
  }
}
