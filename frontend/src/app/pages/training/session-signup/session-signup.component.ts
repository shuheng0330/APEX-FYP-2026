import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { TrainingRegistrationForm, TrainingSession } from '../../../models/training-session.model';
import { TrainingSessionService } from '../../../services/training-session.service';
import { timeRange } from '../training.util';

/** Why the HRDC details are asked for (FR-13-03, NFR-014). */
export const PDPA_NOTICE = 'TBM collects your IC or passport number, phone number and branch only to register you with HRDC '
  + '(Human Resources Development Corporation) for this training. Only the session\'s trainer and HR can see them, and they '
  + 'are erased if you cancel. Under the Personal Data Protection Act 2010 you can ask HR to see or correct them.';

/**
 * The signed-in person's own place on a session (FR-13): register (always confirmed first), join the waitlist, take up
 * or decline an offered seat, answer an invitation, or cancel. The server checks every rule again.
 */
@Component({
  selector: 'app-session-signup',
  standalone: true,
  imports: [
    CommonModule, FormsModule, NzAlertModule, NzButtonModule, NzCheckboxModule, NzIconModule, NzInputModule,
    NzPopconfirmModule, NzSelectModule
  ],
  templateUrl: './session-signup.component.html',
  styleUrls: ['./session-signup.component.scss']
})
export class SessionSignupComponent implements OnChanges {
  @Input({ required: true }) session!: TrainingSession;
  @Output() changed = new EventEmitter<TrainingSession>();

  readonly pdpaNotice = PDPA_NOTICE;
  readonly timeRange = timeRange;

  /** The confirm step is open: a summary, or the HRDC form. */
  confirming = false;
  /** The reason box for declining an invitation is open. */
  declining = false;
  declineReason = '';
  form: TrainingRegistrationForm | null = null;
  idNumber = '';
  phone = '';
  branch: string | null = null;
  pdpaConsent = false;
  busy = false;

  constructor(private sessions: TrainingSessionService, private message: NzMessageService) {}

  ngOnChanges(changes: SimpleChanges): void {
    const before: TrainingSession | undefined = changes['session']?.previousValue;
    if (!before || before.id !== this.session.id) {
      this.confirming = this.declining = false;
      this.declineReason = '';
      this.form = null;
    }
  }

  get s(): TrainingSession {
    return this.session;
  }

  /** Still taking registrations: scheduled and not started. */
  get open(): boolean {
    return this.s.status === 'SCHEDULED' && this.s.timing === 'UPCOMING';
  }

  get active(): boolean {
    return this.s.myStatus === 'REGISTERED' || this.s.myStatus === 'WAITLISTED' || this.s.myStatus === 'OFFERED';
  }

  get invited(): boolean {
    return this.s.myInvitation === 'PENDING' && !this.active;
  }

  get regions(): string[] {
    return this.form ? Object.keys(this.form.branches) : [];
  }

  /** The first thing missing from the HRDC form; the server checks the same. */
  get formProblem(): string | null {
    if (!this.s.registrationForm) return null;
    const id = this.idNumber.replace(/[\s-]/g, '').toUpperCase();
    if (!/^(\d{12}|[A-Z0-9]{6,20})$/.test(id)) return 'Enter your IC number (12 digits) or passport number.';
    if (!/^\+?\d[\d -]{7,18}$/.test(this.phone.trim())) return 'Enter a phone number, for example 012-345 6789.';
    if (!this.branch) return 'Choose your branch.';
    if (!this.pdpaConsent) return 'Tick the box to agree to the PDPA notice.';
    return null;
  }

  startRegistration(): void {
    this.confirming = true;
    this.declining = false;
    if (this.s.registrationForm && !this.form) {
      this.sessions.registrationForm(this.s.id).subscribe({
        next: form => (this.form = form),
        error: err => {
          this.confirming = false;
          this.message.error(err?.error?.message || 'Could not open the registration form.');
        }
      });
    }
  }

  confirmRegistration(): void {
    if (this.formProblem) return;
    const request = this.s.registrationForm
      ? { idNumber: this.idNumber, phone: this.phone, branch: this.branch ?? undefined, pdpaConsent: this.pdpaConsent }
      : {};
    this.run(this.sessions.register(this.s.id, request), updated => updated.myStatus === 'WAITLISTED'
      ? `The session is full, so you're number ${updated.myWaitlistPosition} on the waitlist.`
      : "You're registered. A confirmation is in your notifications.");
  }

  cancel(): void {
    const leaving = this.s.myStatus === 'WAITLISTED' ? "You've left the waitlist." : 'Your registration is cancelled.';
    this.run(this.sessions.cancelRegistration(this.s.id), () => leaving);
  }

  accept(): void {
    this.run(this.sessions.acceptOffer(this.s.id), () => "The seat is yours: you're registered.");
  }

  declineOffer(): void {
    this.run(this.sessions.declineOffer(this.s.id), () => 'Seat declined. It goes to the next person on the waitlist.');
  }

  confirmDecline(): void {
    const reason = this.declineReason.trim();
    if (!reason) return;
    this.run(this.sessions.declineInvitation(this.s.id, reason), () => 'Invitation declined. Your trainer sees your reason.');
  }

  private run(call: Observable<TrainingSession>, done: (updated: TrainingSession) => string): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: updated => {
        this.busy = false;
        this.confirming = this.declining = false;
        this.declineReason = '';
        this.idNumber = this.phone = '';
        this.branch = null;
        this.pdpaConsent = false;
        this.message.success(done(updated));
        this.changed.emit(updated);
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'That did not work. Reload the calendar and try again.');
      }
    });
  }
}
