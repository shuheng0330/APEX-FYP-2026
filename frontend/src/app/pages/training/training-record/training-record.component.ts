import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { TrainingRecord, TrainingRecordItem, TrainingRecordStatus } from '../../../models/training-record.model';
import { TrainingRecordService } from '../../../services/training-record.service';

const STATUS: Record<TrainingRecordStatus, { label: string; color: string }> = {
  NOT_STARTED: { label: 'Not started', color: 'default' },
  IN_PROGRESS: { label: 'In progress', color: 'processing' },
  OVERDUE: { label: 'Overdue', color: 'red' },
  COMPLETED: { label: 'Completed', color: 'green' },
  WITHDRAWN: { label: 'Withdrawn', color: 'default' },
  ATTENDED: { label: 'Attended', color: 'green' },
  ABSENT: { label: 'Absent', color: 'orange' }
};

/**
 * A staff member's training record (FR-18): the totals, every course and session with its status, dates, scores and
 * hours, and the SOP certificates. On the profile it is the signed-in person's own, or the profile's person's; the
 * server decides who may see it (decision A, 29 Oct 2026).
 */
@Component({
  selector: 'app-training-record',
  standalone: true,
  imports: [CommonModule, FormsModule, NzEmptyModule, NzIconModule, NzRadioModule, NzSpinModule, NzTagModule],
  templateUrl: './training-record.component.html',
  styleUrls: ['./training-record.component.scss']
})
export class TrainingRecordComponent implements OnChanges {
  /** Whose record; none for the signed-in person's own. */
  @Input() staffId: string | null = null;
  /** Show the person's name and job, when it isn't one's own record. */
  @Input() showPerson = false;

  record: TrainingRecord | null = null;
  loading = false;
  error: string | null = null;
  show: 'ALL' | 'COURSE' | 'SESSION' = 'ALL';
  /** Courses whose module scores are open. */
  open = new Set<string>();

  constructor(private records: TrainingRecordService) {}

  ngOnChanges(): void {
    this.load();
  }

  get items(): TrainingRecordItem[] {
    const all = this.record?.items ?? [];
    return this.show === 'ALL' ? all : all.filter(i => i.type === this.show);
  }

  status(i: TrainingRecordItem): { label: string; color: string } {
    return STATUS[i.status];
  }

  key(i: TrainingRecordItem): string {
    return `${i.type}-${i.id}`;
  }

  toggle(i: TrainingRecordItem): void {
    const k = this.key(i);
    if (this.open.has(k)) this.open.delete(k);
    else this.open.add(k);
  }

  private load(): void {
    this.loading = true;
    this.error = null;
    (this.staffId ? this.records.of(this.staffId) : this.records.mine()).subscribe({
      next: r => {
        this.record = r;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.record = null;
        this.error = err?.error?.message || 'Could not load the training record.';
      }
    });
  }
}
