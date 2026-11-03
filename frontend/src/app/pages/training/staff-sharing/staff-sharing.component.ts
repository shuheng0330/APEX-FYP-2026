import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { filter, timer } from 'rxjs';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import {
  SharingPresenter, SharingSessionHeld, TrainingSession, TrainingSharingBoard
} from '../../../models/training-session.model';
import { TrainingSessionService } from '../../../services/training-session.service';
import { timeRange } from '../training.util';

/** Other people's changes appear within 30 seconds (NFR-008). */
const REFRESH_SECONDS = 30;

type PresenterSort = 'sessions' | 'reached' | 'attendances';
type SessionSort = 'newest' | 'attended';

/**
 * The Staff Sharing page, for all staff (FR-15-01 to FR-15-03): a year's sharing sessions and the colleagues who
 * presented them, with the staff they reached against the company's headcount apart from the total attendances (TF-23),
 * and the upcoming sessions. As in the prototype's Staff Sharing Sessions page. Each session's audience decides who sees
 * it here, as on the calendar (decision A, 29 Oct 2026).
 */
@Component({
  selector: 'app-staff-sharing',
  standalone: true,
  imports: [CommonModule, FormsModule, NzEmptyModule, NzIconModule, NzRadioModule, NzSelectModule, NzSpinModule, NzTagModule],
  templateUrl: './staff-sharing.component.html',
  styleUrls: ['./staff-sharing.component.scss']
})
export class StaffSharingComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly timeRange = timeRange;
  board: TrainingSharingBoard | null = null;
  year: number | null = null;
  loading = true;
  presenterSort: PresenterSort = 'sessions';
  sessionSort: SessionSort = 'newest';

  private loadSeq = 0;

  constructor(private sessions: TrainingSessionService, private router: Router, private message: NzMessageService) {}

  ngOnInit(): void {
    this.load();
    timer(REFRESH_SECONDS * 1000, REFRESH_SECONDS * 1000).pipe(
      filter(() => !document.hidden),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.load(true));
  }

  /** FR-15-03 */
  get presenters(): SharingPresenter[] {
    const by = (p: SharingPresenter) => this.presenterSort === 'reached' ? p.staffReached
      : this.presenterSort === 'attendances' ? p.attendances : p.sessions;
    return [...(this.board?.staffWhoShared ?? [])].sort((a, b) =>
      by(b) - by(a) || b.sessions - a.sessions || b.attendances - a.attendances || a.name.localeCompare(b.name));
  }

  get held(): SharingSessionHeld[] {
    const list = [...(this.board?.sessions ?? [])];
    return this.sessionSort === 'attended'
      ? list.sort((a, b) => b.attended - a.attended || b.date.localeCompare(a.date))
      : list.sort((a, b) => b.date.localeCompare(a.date) || b.startTime.localeCompare(a.startTime));
  }

  /** "Registered", "Waitlist #2" or "Seat offered" on an upcoming session the viewer is on. */
  myLabel(s: TrainingSession): string | null {
    switch (s.myStatus) {
      case 'REGISTERED': return 'Registered';
      case 'WAITLISTED': return `Waitlist #${s.myWaitlistPosition}`;
      case 'OFFERED': return 'Seat offered';
      default: return null;
    }
  }

  /** Opens the session on the calendar, where staff register for it. */
  open(s: TrainingSession): void {
    this.router.navigate(['/training/calendar'], { queryParams: { session: s.id } });
  }

  onYearChange(): void {
    this.load();
  }

  /** Loads the year shown; quietly for the 30-second refresh. */
  private load(quiet = false): void {
    const seq = ++this.loadSeq;
    if (!quiet) this.loading = true;
    this.sessions.sharingBoard(this.year ?? undefined).subscribe({
      next: board => {
        if (seq !== this.loadSeq) return;
        this.board = board;
        this.year = board.year;
        this.loading = false;
      },
      error: err => {
        if (seq !== this.loadSeq) return;
        this.loading = false;
        if (!quiet) this.message.error(err?.error?.message || 'Could not load staff sharing.');
      }
    });
  }
}
