import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { TrainingTemplate } from '../../../models/training-session.model';
import { TrainingSessionService } from '../../../services/training-session.service';
import { DELIVERY_LABELS, KIND_LABELS, hhmm } from '../training.util';

/**
 * Saved session set-ups (FR-12-08 to FR-12-10): the signed-in trainer's own, or everyone's for HR. Using one opens the
 * scheduling form filled in, with the date left to choose; its creator or HR may delete it.
 */
@Component({
  selector: 'app-session-templates',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzEmptyModule, NzIconModule, NzPopconfirmModule, NzSpinModule],
  templateUrl: './session-templates.component.html',
  styleUrls: ['./session-templates.component.scss']
})
export class SessionTemplatesComponent implements OnInit {
  @Output() use = new EventEmitter<TrainingTemplate>();

  templates: TrainingTemplate[] = [];
  loading = true;
  busy = false;

  constructor(private sessions: TrainingSessionService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.sessions.templates().subscribe({
      next: list => {
        this.templates = list;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.message.error(err?.error?.message || 'Could not load the templates.');
      }
    });
  }

  /** "Training · Physical · 09:00–12:00 · 20 seats" */
  summary(t: TrainingTemplate): string {
    const s = t.setup;
    return [
      s.kind ? KIND_LABELS[s.kind] : null,
      s.delivery ? DELIVERY_LABELS[s.delivery] : null,
      s.startTime && s.endTime ? `${hhmm(s.startTime)}–${hhmm(s.endTime)}` : null,
      s.capacity ? `${s.capacity} seats` : null
    ].filter(Boolean).join(' · ');
  }

  remove(t: TrainingTemplate): void {
    if (this.busy) return;
    this.busy = true;
    this.sessions.deleteTemplate(t.id).subscribe({
      next: () => {
        this.busy = false;
        this.templates = this.templates.filter(x => x.id !== t.id);
        this.message.success(`Template "${t.name}" deleted. Sessions scheduled from it stay as they are.`);
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'Could not delete the template.');
      }
    });
  }
}
