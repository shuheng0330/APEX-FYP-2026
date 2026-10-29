import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, timer } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { TrainingSession, TrainingSessionKind } from '../../../models/training-session.model';
import { AuthService } from '../../../services/auth.service';
import { TrainingSessionService } from '../../../services/training-session.service';
import { SessionDetailsComponent } from '../session-details/session-details.component';
import { SessionFormComponent } from '../session-form/session-form.component';
import {
  DELIVERY_LABELS, KIND_LABELS, STATUS_LABELS, TIMING_LABELS, addDays, dayKey, hhmm, parseDay, startOfDay, startOfWeek,
  timeRange
} from '../training.util';

/** Other people's changes appear within 30 seconds (NFR-008). */
const REFRESH_SECONDS = 30;
/** Sessions shown in a month-view day before "+ n more". */
const CHIPS_PER_DAY = 3;

type CalendarView = 'month' | 'week' | 'list';

interface DayCell {
  key: string;
  date: Date;
  inMonth: boolean;
  today: boolean;
  past: boolean;
  sessions: TrainingSession[];
}

/**
 * The Training Calendar (FR-12-11): sessions in month, week and list views, opening on the current month, with seats
 * booked and left (FR-13-04). Staff with Trainer access and HR schedule sessions here (FR-12-05); the Super Admin
 * sees them all. A notification's link opens a session's details with ?session=<id>.
 */
@Component({
  selector: 'app-training-calendar',
  standalone: true,
  imports: [
    CommonModule, FormsModule, NzButtonModule, NzDrawerModule, NzEmptyModule, NzIconModule, NzRadioModule, NzSelectModule,
    NzSpinModule, NzSwitchModule, NzTagModule, SessionDetailsComponent, SessionFormComponent
  ],
  templateUrl: './training-calendar.component.html',
  styleUrls: ['./training-calendar.component.scss']
})
export class TrainingCalendarComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  readonly kindLabels = KIND_LABELS;
  readonly deliveryLabels = DELIVERY_LABELS;
  readonly chipsPerDay = CHIPS_PER_DAY;
  readonly hhmm = hhmm;
  readonly timeRange = timeRange;

  view: CalendarView = 'month';
  /** A day inside the period shown. */
  anchor = startOfDay(new Date());
  kindFilter: 'ALL' | TrainingSessionKind = 'ALL';
  /** FR-13-05: sessions open for the viewer to register, or the ones they are already on. */
  showFilter: 'ALL' | 'OPEN' | 'MINE' = 'ALL';
  showArchived = false;
  readonly canSchedule: boolean;

  sessions: TrainingSession[] = [];
  loading = true;
  weeks: DayCell[][] = [];
  weekDays: DayCell[] = [];
  listDays: DayCell[] = [];

  selected: TrainingSession | null = null;
  /** Which tab the details open on. */
  openOn: 'details' | 'attendance' = 'details';
  /** FR-14-10: started sessions whose attendance the viewer has still to submit. */
  awaiting: TrainingSession[] = [];
  formOpen = false;
  editing: TrainingSession | null = null;
  formDate: Date | null = null;

  private loadSeq = 0;

  constructor(private sessionsApi: TrainingSessionService, private auth: AuthService, private route: ActivatedRoute,
              private router: Router, private message: NzMessageService) {
    this.canSchedule = auth.hasRole('CAN_MANAGE_TRAINING') || auth.hasRole('CAN_ASSIGN_TRAINING');
  }

  ngOnInit(): void {
    const linked = Number(this.route.snapshot.queryParamMap.get('session'));
    if (linked) {
      this.openLinked(linked);
    } else {
      this.load();
    }
    this.loadAwaiting();
    timer(REFRESH_SECONDS * 1000, REFRESH_SECONDS * 1000).pipe(
      filter(() => !document.hidden),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.load(true);
      this.loadAwaiting();
    });
  }

  /** Only trainers and HR record attendance. */
  loadAwaiting(): void {
    if (!this.canSchedule) return;
    this.sessionsApi.awaitingAttendance().subscribe({ next: list => (this.awaiting = list) });
  }

  /** When the trainer's 7 days end; after that only HR can record it (FR-14-01). */
  trainerUntil(s: TrainingSession): Date {
    return addDays(new Date(`${s.date}T${s.endTime}`), 7);
  }

  overdue(s: TrainingSession): boolean {
    return this.trainerUntil(s).getTime() < Date.now();
  }

  openAttendance(s: TrainingSession): void {
    this.openOn = 'attendance';
    this.selected = s;
  }

  // --- period -------------------------------------------------------------------

  get periodLabel(): string {
    if (this.view !== 'week') return formatDate(this.anchor, 'MMMM yyyy', 'en-US');
    const from = startOfWeek(this.anchor);
    const to = addDays(from, 6);
    const sameMonth = from.getMonth() === to.getMonth();
    return `${formatDate(from, sameMonth ? 'd' : 'd MMM', 'en-US')} – ${formatDate(to, 'd MMM yyyy', 'en-US')}`;
  }

  setView(view: CalendarView): void {
    this.view = view;
    this.load();
  }

  move(step: number): void {
    this.anchor = this.view === 'week'
      ? addDays(this.anchor, 7 * step)
      : new Date(this.anchor.getFullYear(), this.anchor.getMonth() + step, 1);
    this.load();
  }

  goToday(): void {
    this.anchor = startOfDay(new Date());
    this.load();
  }

  /** Opens the week holding a busy day from the month view. */
  showWeek(day: Date): void {
    this.anchor = day;
    this.setView('week');
  }

  private range(): { from: Date; to: Date } {
    if (this.view === 'week') {
      const from = startOfWeek(this.anchor);
      return { from, to: addDays(from, 6) };
    }
    const first = new Date(this.anchor.getFullYear(), this.anchor.getMonth(), 1);
    const last = new Date(this.anchor.getFullYear(), this.anchor.getMonth() + 1, 0);
    return this.view === 'list' ? { from: first, to: last } : { from: startOfWeek(first), to: addDays(startOfWeek(last), 6) };
  }

  // --- data ---------------------------------------------------------------------

  /** Loads the period shown; quietly for the 30-second refresh. */
  load(quiet = false): void {
    const { from, to } = this.range();
    const seq = ++this.loadSeq;
    if (!quiet) this.loading = true;
    this.rebuild(); // the new period's grid shows at once
    this.sessionsApi.calendar(dayKey(from), dayKey(to), this.showArchived).subscribe({
      next: list => {
        if (seq !== this.loadSeq) return;
        this.sessions = list;
        this.loading = false;
        if (this.selected) {
          this.selected = list.find(s => s.id === this.selected!.id) ?? this.selected;
        }
        this.rebuild();
      },
      error: err => {
        if (seq !== this.loadSeq) return;
        this.loading = false;
        if (!quiet) this.message.error(err?.error?.message || 'Could not load the calendar.');
      }
    });
  }

  /** Lays the loaded sessions out for the view, with the kind filter applied. */
  rebuild(): void {
    const { from, to } = this.range();
    const shown = this.sessions.filter(s => (this.kindFilter === 'ALL' || s.kind === this.kindFilter)
      && (this.showFilter === 'ALL' || (this.showFilter === 'OPEN' ? s.canRegister : this.isMine(s))));
    const byDay = new Map<string, TrainingSession[]>();
    for (const s of shown) {
      byDay.set(s.date, [...(byDay.get(s.date) ?? []), s]);
    }
    const todayKey = dayKey(new Date());
    const cells: DayCell[] = [];
    for (let d = from; d <= to; d = addDays(d, 1)) {
      const key = dayKey(d);
      cells.push({
        key, date: d, inMonth: d.getMonth() === this.anchor.getMonth(), today: key === todayKey, past: key < todayKey,
        sessions: byDay.get(key) ?? []
      });
    }
    this.weeks = [];
    for (let i = 0; i < cells.length; i += 7) {
      this.weeks.push(cells.slice(i, i + 7));
    }
    this.weekDays = cells;
    this.listDays = cells.filter(c => c.sessions.length > 0);
  }

  // --- sessions -----------------------------------------------------------------

  chipClass(s: TrainingSession): Record<string, boolean> {
    return {
      [s.delivery.toLowerCase()]: true, sharing: s.kind === 'STAFF_SHARING', cancelled: s.status === 'CANCELLED',
      archived: s.status === 'ARCHIVED', ended: s.timing === 'ENDED', mandatory: s.mandatory, mine: this.isMine(s)
    };
  }

  /** The viewer is registered, waiting, offered a seat or invited and yet to answer. */
  isMine(s: TrainingSession): boolean {
    return s.myStatus === 'REGISTERED' || s.myStatus === 'WAITLISTED' || s.myStatus === 'OFFERED'
      || s.myInvitation === 'PENDING';
  }

  /** "Registered", "Waitlist #2", "Seat offered" or "Invited", for the viewer's own sessions. */
  myLabel(s: TrainingSession): string | null {
    switch (s.myStatus) {
      case 'REGISTERED': return 'Registered';
      case 'WAITLISTED': return `Waitlist #${s.myWaitlistPosition}`;
      case 'OFFERED': return 'Seat offered';
      default: return s.myInvitation === 'PENDING' ? 'Invited' : null;
    }
  }

  statusLabel(s: TrainingSession): string {
    return s.status === 'SCHEDULED' ? TIMING_LABELS[s.timing] : STATUS_LABELS[s.status];
  }

  statusColor(s: TrainingSession): string {
    if (s.status === 'CANCELLED') return 'red';
    if (s.status === 'ARCHIVED' || s.timing === 'ENDED') return 'default';
    return s.timing === 'IN_PROGRESS' ? 'processing' : 'blue';
  }

  openDetails(s: TrainingSession): void {
    this.openOn = 'details';
    this.selected = s;
  }

  closeDetails(): void {
    this.selected = null;
    if (this.route.snapshot.queryParamMap.has('session')) {
      this.router.navigate([], { relativeTo: this.route, queryParams: { session: null }, replaceUrl: true });
    }
  }

  openNew(day?: Date): void {
    this.editing = null;
    this.formDate = day ?? null;
    this.formOpen = true;
  }

  openEdit(s: TrainingSession): void {
    this.editing = s;
    this.formDate = null;
    this.selected = null;
    this.formOpen = true;
  }

  closeForm(): void {
    this.formOpen = false;
    this.editing = null;
  }

  /** Shows the saved session on the calendar, at its date, with its details open. */
  onSaved(saved: TrainingSession): void {
    this.closeForm();
    this.anchor = parseDay(saved.date);
    this.selected = saved;
    this.load();
  }

  onChanged(updated: TrainingSession): void {
    const leaves = (updated.status === 'ARCHIVED') !== this.showArchived;
    this.sessions = leaves ? this.sessions.filter(s => s.id !== updated.id)
      : this.sessions.map(s => (s.id === updated.id ? updated : s));
    this.selected = leaves ? null : updated;
    this.rebuild();
    this.loadAwaiting();
  }

  onDeleted(id: number): void {
    this.sessions = this.sessions.filter(s => s.id !== id);
    this.selected = null;
    this.rebuild();
  }

  /** From a notification: open the session's details on its own month. */
  private openLinked(id: number): void {
    this.sessionsApi.get(id).subscribe({
      next: s => {
        this.anchor = parseDay(s.date);
        this.showArchived = s.status === 'ARCHIVED';
        this.selected = s;
        this.load();
      },
      error: err => {
        this.message.error(err?.error?.message || 'That session is no longer available.');
        this.load();
      }
    });
  }
}
