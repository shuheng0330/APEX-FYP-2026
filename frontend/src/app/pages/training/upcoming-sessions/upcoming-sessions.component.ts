import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { TrainingSession } from '../../../models/training-session.model';
import { TrainingSessionService } from '../../../services/training-session.service';
import { KIND_LABELS, addDays, dayKey, hhmm, parseDay, startOfDay } from '../training.util';

/** Open sessions shown on the My Learning home before "Full calendar". */
const OPEN_SHOWN = 3;
/** How far ahead the home looks for sessions open to register. */
const OPEN_DAYS_AHEAD = 60;

/**
 * Training sessions on the My Learning home (FR-13-05), laid out as in the prototype's Learning Portal: the person's
 * own sessions (registered, waitlisted, offered a seat or invited), then a few sessions open for them to register.
 * Each card opens the session on the Training Calendar.
 */
@Component({
  selector: 'app-upcoming-sessions',
  standalone: true,
  imports: [CommonModule, RouterModule, NzIconModule],
  templateUrl: './upcoming-sessions.component.html',
  styleUrls: ['./upcoming-sessions.component.scss']
})
export class UpcomingSessionsComponent implements OnInit {
  readonly hhmm = hhmm;

  mine: TrainingSession[] = [];
  open: TrainingSession[] = [];

  constructor(private sessions: TrainingSessionService, private router: Router) {}

  ngOnInit(): void {
    const today = startOfDay(new Date());
    forkJoin({
      mine: this.sessions.mine().pipe(catchError(() => of([] as TrainingSession[]))),
      calendar: this.sessions.calendar(dayKey(today), dayKey(addDays(today, OPEN_DAYS_AHEAD)))
        .pipe(catchError(() => of([] as TrainingSession[])))
    }).subscribe(({ mine, calendar }) => {
      this.mine = mine;
      this.open = calendar.filter(s => s.canRegister).slice(0, OPEN_SHOWN);
    });
  }

  kindLabel(s: TrainingSession): string {
    return KIND_LABELS[s.kind];
  }

  daysAway(s: TrainingSession): number {
    return Math.round((parseDay(s.date).getTime() - startOfDay(new Date()).getTime()) / 86_400_000);
  }

  seatsPercent(s: TrainingSession): number {
    return s.capacity ? Math.min(100, Math.round((s.booked * 100) / s.capacity)) : 0;
  }

  /** The person's own place, as the card's status chip shows it. */
  place(s: TrainingSession): { label: string; tone: string } {
    switch (s.myStatus) {
      case 'REGISTERED': return { label: 'Registered', tone: 'registered' };
      case 'WAITLISTED': return { label: `Waitlist #${s.myWaitlistPosition}`, tone: 'waiting' };
      case 'OFFERED': return { label: 'Seat held for you', tone: 'offered' };
      default: return { label: 'Invited: please answer', tone: 'invited' };
    }
  }

  openSession(s: TrainingSession): void {
    this.router.navigate(['/training/calendar'], { queryParams: { session: s.id } });
  }
}
