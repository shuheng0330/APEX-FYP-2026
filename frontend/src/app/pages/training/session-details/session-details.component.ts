import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { Observable } from 'rxjs';
import { TrainingSession } from '../../../models/training-session.model';
import { AuthService } from '../../../services/auth.service';
import { TrainingSessionService } from '../../../services/training-session.service';
import { formatDuration } from '../../sop/sop.util';
import { SessionAttendanceComponent } from '../session-attendance/session-attendance.component';
import { SessionPeopleComponent } from '../session-people/session-people.component';
import { SessionReportComponent } from '../session-report/session-report.component';
import { SessionSignupComponent } from '../session-signup/session-signup.component';
import {
  DELIVERY_LABELS, KIND_LABELS, STATUS_LABELS, TIMING_LABELS, TRAINING_TYPE_LABELS, isLink, mapsLink, timeRange
} from '../training.util';

/**
 * One session's details on the Training Calendar, with what whoever manages it may do (FR-12-06): change it, cancel it
 * with a reason before it starts, archive it once it has ended or was cancelled, restore it, or delete it while
 * nobody has registered or been invited.
 */
@Component({
  selector: 'app-session-details',
  standalone: true,
  imports: [
    CommonModule, FormsModule, NzAlertModule, NzButtonModule, NzIconModule, NzInputModule, NzPopconfirmModule, NzTabsModule,
    NzTagModule, SessionAttendanceComponent, SessionPeopleComponent, SessionReportComponent, SessionSignupComponent
  ],
  templateUrl: './session-details.component.html',
  styleUrls: ['./session-details.component.scss']
})
export class SessionDetailsComponent implements OnChanges {
  @Input({ required: true }) session!: TrainingSession;
  /** Opened from the sessions awaiting attendance: start on the Attendance tab. */
  @Input() openOn: 'details' | 'attendance' = 'details';
  @Output() edit = new EventEmitter<TrainingSession>();
  @Output() changed = new EventEmitter<TrainingSession>();
  @Output() deleted = new EventEmitter<number>();

  readonly kindLabels = KIND_LABELS;
  readonly deliveryLabels = DELIVERY_LABELS;
  readonly typeLabels = TRAINING_TYPE_LABELS;
  readonly formatDuration = formatDuration;
  readonly timeRange = timeRange;
  readonly isLink = isLink;

  tabIndex = 0;
  /** The cancel box is open. */
  cancelling = false;
  cancelReason = '';
  busy = false;
  /** Schedules sessions: told who else may change one they can't. */
  readonly schedules: boolean;

  constructor(private sessions: TrainingSessionService, private message: NzMessageService, auth: AuthService) {
    this.schedules = auth.hasRole('CAN_MANAGE_TRAINING') || auth.hasRole('CAN_ASSIGN_TRAINING');
  }

  ngOnChanges(changes: SimpleChanges): void {
    // A refresh of the same session keeps a half-typed reason and the tab; another session starts afresh.
    const before: TrainingSession | undefined = changes['session']?.previousValue;
    if (!before || before.id !== this.session.id) {
      this.cancelling = false;
      this.cancelReason = '';
      this.tabIndex = this.openOn === 'attendance' && this.showAttendance ? this.attendanceTabIndex : 0;
    }
  }

  get s(): TrainingSession {
    return this.session;
  }

  /** "Upcoming", "In progress" or "Ended" while scheduled; otherwise Cancelled or Archived. */
  get statusLabel(): string {
    return this.s.status === 'SCHEDULED' ? TIMING_LABELS[this.s.timing] : STATUS_LABELS[this.s.status];
  }

  get statusColor(): string {
    if (this.s.status === 'CANCELLED') return 'red';
    if (this.s.status === 'ARCHIVED' || this.s.timing === 'ENDED') return 'default';
    return this.s.timing === 'IN_PROGRESS' ? 'processing' : 'blue';
  }

  get mapsUrl(): string | null {
    return mapsLink(this.s);
  }

  get seatsPercent(): number {
    return this.s.capacity ? Math.min(100, Math.round((this.s.booked * 100) / this.s.capacity)) : 0;
  }

  get canEdit(): boolean {
    return this.s.canManage && this.s.status === 'SCHEDULED';
  }

  get canCancel(): boolean {
    return this.s.canManage && this.s.status === 'SCHEDULED' && this.s.timing === 'UPCOMING';
  }

  get canArchive(): boolean {
    return this.s.canManage && !this.archiveWaitsForAttendance
      && (this.s.status === 'CANCELLED' || (this.s.status === 'SCHEDULED' && this.s.timing === 'ENDED'));
  }

  /** An ended session with people on it is archived once its attendance is submitted (decision D). */
  get archiveWaitsForAttendance(): boolean {
    return this.s.canManage && this.s.status === 'SCHEDULED' && this.s.timing === 'ENDED' && this.awaitingAttendance;
  }

  get showPeople(): boolean {
    return this.s.canSeePeople;
  }

  /** Once it starts, for its trainer, HR and the Super Admin; a cancelled session has none. */
  get showAttendance(): boolean {
    return this.s.canSeePeople && !this.s.cancelledAt && this.s.timing !== 'UPCOMING';
  }

  /** FR-12-07: for the session's trainer, HR and the Super Admin, like its people. */
  get showReport(): boolean {
    return this.s.canSeePeople;
  }

  get attendanceTabIndex(): number {
    return this.showPeople ? 2 : 1;
  }

  /** Started, with someone on it, and not submitted yet (FR-14-10). */
  get awaitingAttendance(): boolean {
    return this.s.status === 'SCHEDULED' && this.s.timing !== 'UPCOMING' && !this.s.attendanceSubmittedAt
      && (this.s.booked > 0 || this.s.attendanceRecorded);
  }

  get canRestore(): boolean {
    return this.s.canManage && this.s.status === 'ARCHIVED';
  }

  confirmCancel(): void {
    const reason = this.cancelReason.trim();
    if (!reason) return;
    this.run(this.sessions.cancel(this.s.id, reason), 'Session cancelled. Everyone registered or invited has been told why.');
  }

  archive(): void {
    this.run(this.sessions.archive(this.s.id), 'Session archived. Its records are kept; turn on Archived to see it.');
  }

  /** FR-15-06 */
  requestTrainerAccess(): void {
    this.run(this.sessions.requestTrainerAccess(this.s.id), 'Asked. The Super Admin has been told.');
  }

  restore(): void {
    this.run(this.sessions.restore(this.s.id), 'Session restored to the calendar.');
  }

  remove(): void {
    if (this.busy) return;
    this.busy = true;
    const id = this.s.id;
    this.sessions.delete(id).subscribe({
      next: () => {
        this.busy = false;
        this.message.success('Session deleted.');
        this.deleted.emit(id);
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'Could not delete the session.');
      }
    });
  }

  /** People were added, removed or offered seats: show the session's seats as they are now. */
  refresh(): void {
    this.sessions.get(this.s.id).subscribe({ next: updated => this.changed.emit(updated) });
  }

  private run(call: Observable<TrainingSession>, done: string): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: updated => {
        this.busy = false;
        this.cancelling = false;
        this.cancelReason = '';
        this.message.success(done);
        this.changed.emit(updated);
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'That did not work. Reload the calendar and try again.');
      }
    });
  }
}
