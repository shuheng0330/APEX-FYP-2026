import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { AudiencePerson } from '../../../models/sop-assignment.model';
import { TrainingPerson, TrainingSession } from '../../../models/training-session.model';
import { SopAssignmentService } from '../../../services/sop-assignment.service';
import { TrainingSessionService } from '../../../services/training-session.service';

const STATUS: Record<TrainingPerson['status'], { label: string; color: string }> = {
  REGISTERED: { label: 'Registered', color: 'green' },
  OFFERED: { label: 'Offered', color: 'blue' },
  WAITLISTED: { label: 'Waitlisted', color: 'orange' },
  INVITED: { label: 'Invited', color: 'purple' },
  DECLINED: { label: 'Declined', color: 'default' },
  NO_RESPONSE: { label: 'No response', color: 'default' },
  CANCELLED: { label: 'Cancelled', color: 'default' }
};

/**
 * Who is on a session, for its trainer, HR and the Super Admin: registered, offered a seat, waitlisted or invited,
 * with the HRDC details for the trainer and HR. Whoever manages the session adds people (the only way in when staff
 * can't register themselves, FR-12-04), removes them, and offers free seats by hand once automatic offers stop
 * (FR-13-07).
 */
@Component({
  selector: 'app-session-people',
  standalone: true,
  imports: [CommonModule, FormsModule, NzButtonModule, NzIconModule, NzPopconfirmModule, NzSelectModule, NzTagModule],
  templateUrl: './session-people.component.html',
  styleUrls: ['./session-people.component.scss']
})
export class SessionPeopleComponent implements OnChanges {
  @Input({ required: true }) session!: TrainingSession;
  /** Someone was added, removed or offered a seat: the session's seats have changed. */
  @Output() changed = new EventEmitter<void>();

  readonly status = STATUS;
  people: TrainingPerson[] = [];
  loading = false;
  staff: AudiencePerson[] = [];
  toAdd: string | null = null;
  showEnded = false;
  busy = false;

  constructor(private sessions: TrainingSessionService, private assignments: SopAssignmentService,
              private message: NzMessageService) {}

  ngOnChanges(changes: SimpleChanges): void {
    const before: TrainingSession | undefined = changes['session']?.previousValue;
    // Reload when another session opens, or when this one's seats change (a registration, a refresh).
    if (!before || before.id !== this.session.id || before.booked !== this.session.booked
      || before.waitlisted !== this.session.waitlisted) {
      this.load();
    }
    if (this.canChange && !this.staff.length) {
      this.assignments.audience().subscribe({ next: a => (this.staff = a.staff) });
    }
  }

  /** Whoever manages the session can change who is on it, until it starts. */
  get canChange(): boolean {
    return this.session.canManage && this.session.status === 'SCHEDULED' && this.session.timing === 'UPCOMING';
  }

  get current(): TrainingPerson[] {
    return this.people.filter(p => p.status !== 'CANCELLED' && p.status !== 'DECLINED' && p.status !== 'NO_RESPONSE');
  }

  get ended(): TrainingPerson[] {
    return this.people.filter(p => !this.current.includes(p));
  }

  get counts(): string {
    const n = (s: TrainingPerson['status']) => this.people.filter(p => p.status === s).length;
    return [
      `${n('REGISTERED')} registered`, n('OFFERED') ? `${n('OFFERED')} offered` : '',
      n('WAITLISTED') ? `${n('WAITLISTED')} waitlisted` : '', n('INVITED') ? `${n('INVITED')} invited` : ''
    ].filter(Boolean).join(' · ');
  }

  /** Staff who could be added: active, not already on the session, and not whoever runs it. */
  get addable(): AudiencePerson[] {
    const on = new Set(this.current.filter(p => p.registrationId).map(p => p.staffId));
    return this.staff.filter(p => !on.has(p.id) && p.id !== this.session.trainerId);
  }

  canOffer(p: TrainingPerson): boolean {
    return this.canChange && p.status === 'WAITLISTED' && this.session.seatsLeft > 0;
  }

  canRemove(p: TrainingPerson): boolean {
    return this.canChange && !!p.registrationId
      && (p.status === 'REGISTERED' || p.status === 'WAITLISTED' || p.status === 'OFFERED');
  }

  add(): void {
    if (!this.toAdd) return;
    const name = this.staff.find(p => p.id === this.toAdd)?.name ?? 'They';
    this.run(this.sessions.addPerson(this.session.id, this.toAdd), `${name} is on the session and has been told.`);
    this.toAdd = null;
  }

  remove(p: TrainingPerson): void {
    this.run(this.sessions.removePerson(this.session.id, p.registrationId!), `${p.name} was taken off the session and told.`);
  }

  offer(p: TrainingPerson): void {
    this.run(this.sessions.offerSeat(this.session.id, p.registrationId!), `A seat is held for ${p.name}, who has been told.`);
  }

  private load(): void {
    this.loading = true;
    this.sessions.people(this.session.id).subscribe({
      next: people => {
        this.people = people;
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  private run(call: Observable<TrainingPerson[]>, done: string): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: people => {
        this.busy = false;
        this.people = people;
        this.message.success(done);
        this.changed.emit();
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'That did not work. Reload the calendar and try again.');
      }
    });
  }
}
