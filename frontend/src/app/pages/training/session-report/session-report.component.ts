import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { TrainingSession, TrainingSessionReport } from '../../../models/training-session.model';
import { TrainingSessionService } from '../../../services/training-session.service';
import { formatDuration } from '../../sop/sop.util';
import { saveFile, sessionFileName } from '../training.util';

/**
 * A session's report (FR-12-07) for its trainer, HR and the Super Admin: its numbers, everyone on its attendance list
 * and its evidence, with an Excel copy to download (decision D, 28 Oct 2026).
 */
@Component({
  selector: 'app-session-report',
  standalone: true,
  imports: [CommonModule, NzAlertModule, NzButtonModule, NzIconModule, NzTagModule],
  templateUrl: './session-report.component.html',
  styleUrls: ['./session-report.component.scss']
})
export class SessionReportComponent implements OnChanges {
  @Input({ required: true }) session!: TrainingSession;

  readonly formatDuration = formatDuration;
  report: TrainingSessionReport | null = null;
  loading = false;
  downloading = false;

  constructor(private sessions: TrainingSessionService, private message: NzMessageService) {}

  /** Loaded afresh whenever the session's details are, so the numbers follow attendance changes. */
  ngOnChanges(): void {
    if (this.report && this.report.sessionId !== this.session.id) {
      this.report = null;
    }
    this.load();
  }

  download(): void {
    if (this.downloading) return;
    this.downloading = true;
    this.sessions.reportFile(this.session.id).subscribe({
      next: blob => {
        this.downloading = false;
        saveFile(blob, sessionFileName('Session report', this.session));
      },
      error: () => {
        this.downloading = false;
        this.message.error('Could not download the report. Reload the calendar and try again.');
      }
    });
  }

  private load(): void {
    const id = this.session.id;
    this.loading = true;
    this.sessions.report(id).subscribe({
      next: r => {
        if (id !== this.session.id) return;
        this.report = r;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.message.error(err?.error?.message || 'Could not load the report.');
      }
    });
  }
}
