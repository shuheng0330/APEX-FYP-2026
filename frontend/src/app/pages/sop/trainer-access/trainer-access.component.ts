import { Component, OnInit } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTimelineModule } from 'ng-zorro-antd/timeline';
import { TrainerAccessService } from '../../../services/trainer-access.service';
import { TrainerAccessStaff, TrainerOwnedCourse, TrainingAuditEntry } from '../../../models/trainer-access.model';

type AccessFilter = 'ALL' | 'ANY' | 'GRANT' | 'ROLE' | 'NONE';

const MAX_REASON = 500;
const ENDING_SOON_DAYS = 7;
const NO_DEPARTMENT = 'No department';

const ACTIONS: Record<string, { label: string; color: string }> = {
  TRAINER_ACCESS_GRANTED: { label: 'Granted', color: 'green' },
  TRAINER_ACCESS_END_DATE_CHANGED: { label: 'End date changed', color: 'blue' },
  TRAINER_ACCESS_REVOKED: { label: 'Revoked', color: 'red' },
  TRAINER_ACCESS_LAPSED: { label: 'Ended', color: 'gray' },
};

/**
 * Manage Trainer Access (UC-43). The Super Admin gives individual staff Trainer access on top of their
 * job role, optionally until a last day, and revokes it; every change needs a reason and is recorded
 * (FR-20-01). Someone who owns courses cannot lose Trainer access until their courses are transferred
 * (FR-20-07).
 */
@Component({
  selector: 'app-trainer-access',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzAlertModule, NzButtonModule, NzDatePickerModule, NzDrawerModule, NzEmptyModule, NzIconModule, NzInputModule,
    NzModalModule, NzRadioModule, NzSelectModule, NzSpinModule, NzTableModule, NzTagModule, NzTimelineModule
  ],
  templateUrl: './trainer-access.component.html',
  styleUrls: ['./trainer-access.component.scss']
})
export class TrainerAccessComponent implements OnInit {
  readonly maxReason = MAX_REASON;
  readonly accessOptions: { value: AccessFilter; label: string }[] = [
    { value: 'ALL', label: 'Everyone' },
    { value: 'ANY', label: 'With access' },
    { value: 'GRANT', label: 'Through a grant' },
    { value: 'ROLE', label: 'Through job role' },
    { value: 'NONE', label: 'No access' },
  ];

  staff: TrainerAccessStaff[] = [];
  filtered: TrainerAccessStaff[] = [];
  departments: string[] = [];
  counts = { any: 0, grant: 0, role: 0, endingSoon: 0 };
  loading = true;

  search = '';
  department: string | null = null;
  access: AccessFilter = 'ALL';

  /** The person whose access is being granted or whose end date is being changed. */
  grantTarget: TrainerAccessStaff | null = null;
  grantEndDate: Date | null = null;
  grantReason = '';
  grantError = '';

  revokeTarget: TrainerAccessStaff | null = null;
  revokeReason = '';
  revokeError = '';
  revokeCourses: TrainerOwnedCourse[] = [];
  coursesLoading = false;

  saving = false;

  historyTarget: TrainerAccessStaff | null = null;
  history: TrainingAuditEntry[] = [];
  historyLoading = false;

  /** The date picker refuses days before today. */
  readonly disabledDate = (d: Date): boolean => startOfDay(d) < startOfDay(new Date());

  constructor(private trainerAccess: TrainerAccessService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.trainerAccess.list().subscribe({
      next: list => {
        this.staff = list;
        this.departments = [...new Set(list.map(s => s.department || NO_DEPARTMENT))].sort();
        this.recount();
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.message.error('Could not load staff.');
      }
    });
  }

  // === filters ==============================================================

  applyFilters(): void {
    const q = this.search.trim().toLowerCase();
    this.filtered = this.staff.filter(s =>
      (!q || [s.name, s.email, s.jobRole ?? ''].some(v => v.toLowerCase().includes(q)))
      && (!this.department || (s.department || NO_DEPARTMENT) === this.department)
      && this.matchesAccess(s));
  }

  get hasFilters(): boolean {
    return !!this.search.trim() || !!this.department || this.access !== 'ALL';
  }

  clearFilters(): void {
    this.search = '';
    this.department = null;
    this.access = 'ALL';
    this.applyFilters();
  }

  private matchesAccess(s: TrainerAccessStaff): boolean {
    switch (this.access) {
      case 'ANY': return s.viaGrant || s.viaRole;
      case 'GRANT': return s.viaGrant;
      case 'ROLE': return s.viaRole;
      case 'NONE': return !s.viaGrant && !s.viaRole;
      default: return true;
    }
  }

  private recount(): void {
    this.counts = {
      any: this.staff.filter(s => s.viaGrant || s.viaRole).length,
      grant: this.staff.filter(s => s.viaGrant).length,
      role: this.staff.filter(s => s.viaRole).length,
      endingSoon: this.staff.filter(s => this.endingSoon(s)).length,
    };
  }

  // === grant / change end date (FR-20-01) ===================================

  openGrant(s: TrainerAccessStaff): void {
    this.grantTarget = s;
    this.grantEndDate = s.viaGrant && s.grantEndDate ? parseDate(s.grantEndDate) : null;
    this.grantReason = '';
    this.grantError = '';
  }

  closeGrant(): void {
    if (!this.saving) {
      this.grantTarget = null;
    }
  }

  /** True when changing the end date of a grant already in force. */
  get isChange(): boolean {
    return !!this.grantTarget?.viaGrant;
  }


  /** What confirming will do, in one sentence. */
  get grantSummary(): string {
    const name = this.grantTarget?.name ?? '';
    const until = this.grantEndDate
      ? `until the end of ${formatDate(this.grantEndDate, 'd MMM yyyy', 'en')}`
      : 'with no end date';
    return this.isChange
      ? `${name}'s Trainer access will continue ${until}.`
      : `${name} will be able to upload SOPs, review generated material and quizzes, and assign their own courses, ${until}.`;
  }

  confirmGrant(): void {
    const target = this.grantTarget;
    if (!target || this.reasonMissing(this.grantReason, e => this.grantError = e)) return;
    this.saving = true;
    this.grantError = '';
    const endDate = this.grantEndDate ? formatDate(this.grantEndDate, 'yyyy-MM-dd', 'en') : null;
    this.trainerAccess.grant(target.staffId, { endDate, reason: this.grantReason.trim() }).subscribe({
      next: updated => {
        this.saving = false;
        this.grantTarget = null;
        this.replace(updated);
        this.message.success(target.viaGrant
          ? `Trainer access for ${updated.name} now ${updated.grantEndDate ? 'ends after ' + formatDate(updated.grantEndDate, 'd MMM yyyy', 'en') : 'has no end date'}.`
          : `${updated.name} now has Trainer access. It takes effect at their next sign-in, or within 15 minutes.`);
      },
      error: (e: HttpErrorResponse) => {
        this.saving = false;
        this.grantError = errorMessage(e);
      }
    });
  }

  // === revoke (FR-20-01, FR-20-07) ==========================================

  openRevoke(s: TrainerAccessStaff): void {
    this.revokeTarget = s;
    this.revokeReason = '';
    this.revokeError = '';
    this.revokeCourses = [];
    if (this.revokeBlocked) {
      this.coursesLoading = true;
      this.trainerAccess.ownedCourses(s.staffId).subscribe({
        next: courses => { this.revokeCourses = courses; this.coursesLoading = false; },
        error: () => { this.coursesLoading = false; }
      });
    }
  }

  closeRevoke(): void {
    if (!this.saving) {
      this.revokeTarget = null;
    }
  }

  /** Revoking would leave courses without an owner who can manage them. */
  get revokeBlocked(): boolean {
    const s = this.revokeTarget;
    return !!s && !s.viaRole && s.ownedCourses > 0;
  }


  confirmRevoke(): void {
    const target = this.revokeTarget;
    if (!target || this.revokeBlocked || this.reasonMissing(this.revokeReason, e => this.revokeError = e)) return;
    this.saving = true;
    this.revokeError = '';
    this.trainerAccess.revoke(target.staffId, this.revokeReason.trim()).subscribe({
      next: updated => {
        this.saving = false;
        this.revokeTarget = null;
        this.replace(updated);
        this.message.success(updated.viaRole
          ? `Trainer access grant revoked for ${updated.name}. They keep access through their job role.`
          : `Trainer access revoked for ${updated.name}. It stops within 15 minutes.`);
      },
      error: (e: HttpErrorResponse) => {
        this.saving = false;
        this.revokeError = errorMessage(e);
      }
    });
  }

  // === history (FR-20-01, NFR-007) ==========================================

  openHistory(s: TrainerAccessStaff): void {
    this.historyTarget = s;
    this.history = [];
    this.historyLoading = true;
    this.trainerAccess.history(s.staffId).subscribe({
      next: entries => { this.history = entries; this.historyLoading = false; },
      error: () => { this.historyLoading = false; this.message.error('Could not load the history.'); }
    });
  }

  closeHistory(): void {
    this.historyTarget = null;
  }

  // === helpers ==============================================================

  actionOf(action: string): { label: string; color: string } {
    return ACTIONS[action] ?? { label: action, color: 'gray' };
  }

  /** Every change needs a reason (FR-20-01): says so in the dialog and in a pop-up, and stops the change. */
  private reasonMissing(reason: string, show: (error: string) => void): boolean {
    const error = !reason.trim() ? 'A reason is required. Say why you are making this change.'
      : reason.length > MAX_REASON ? `Keep the reason under ${MAX_REASON} characters.` : '';
    if (error) {
      show(error);
      this.message.warning(error);
    }
    return !!error;
  }

  /** A grant in force whose last day is within the next 7 days. */
  endingSoon(s: TrainerAccessStaff): boolean {
    if (!s.viaGrant || !s.grantEndDate) return false;
    const days = (parseDate(s.grantEndDate).getTime() - startOfDay(new Date()).getTime()) / 86_400_000;
    return days <= ENDING_SOON_DAYS;
  }

  private replace(updated: TrainerAccessStaff): void {
    this.staff = this.staff.map(s => s.staffId === updated.staffId ? updated : s);
    this.recount();
    this.applyFilters();
  }
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** A yyyy-MM-dd date from the API as a local date. */
function parseDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function errorMessage(e: HttpErrorResponse): string {
  return e.error?.message || 'Something went wrong. Please try again.';
}
