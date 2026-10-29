import { Component, DestroyRef, OnInit, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
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
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTimelineModule } from 'ng-zorro-antd/timeline';
import { TrainerAccessService } from '../../../services/trainer-access.service';
import { TrainerAccessStaff, TrainerOwnedCourse, TrainingAuditEntry } from '../../../models/trainer-access.model';
import { CourseOwnersComponent } from './course-owners/course-owners.component';
import { MAX_REASON, auditAction, errorMessage, reasonMissing, showRefusal } from './trainer-access.util';

type AccessFilter = 'ALL' | 'ANY' | 'GRANT' | 'ROLE' | 'NONE';

const ENDING_SOON_DAYS = 7;
const NO_DEPARTMENT = 'No department';
const STAFF_TAB = 0;
const OWNERS_TAB = 1;

/**
 * Manage Trainer Access (UC-43). The Super Admin gives individual staff Trainer access on top of their
 * job role, optionally until a last day, and revokes it; every change needs a reason and is recorded
 * (FR-20-01). Revoking someone who owns courses hands them to a chosen trainer in the same step (FR-20-07),
 * and the Course owners tab finds a new owner for courses whose owner has left or lost access (FR-20-06).
 */
@Component({
  selector: 'app-trainer-access',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzAlertModule, NzButtonModule, NzDatePickerModule, NzDrawerModule, NzEmptyModule, NzIconModule, NzInputModule,
    NzModalModule, NzRadioModule, NzSelectModule, NzSpinModule, NzTableModule, NzTabsModule, NzTagModule, NzTimelineModule,
    CourseOwnersComponent
  ],
  templateUrl: './trainer-access.component.html',
  styleUrls: ['./trainer-access.component.scss']
})
export class TrainerAccessComponent implements OnInit {
  readonly maxReason = MAX_REASON;
  readonly actionOf = auditAction;
  readonly ownersTab = OWNERS_TAB;
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

  /** The open tab: Staff, or Course owners (?tab=owners, where the lapse alert links). */
  tabIndex = STAFF_TAB;
  @ViewChild(CourseOwnersComponent) private courseOwners?: CourseOwnersComponent;

  /** The person whose access is being granted or whose end date is being changed. */
  grantTarget: TrainerAccessStaff | null = null;
  grantEndDate: Date | null = null;
  grantReason = '';
  grantError = '';

  revokeTarget: TrainerAccessStaff | null = null;
  revokeReason = '';
  revokeError = '';
  revokeCourses: TrainerOwnedCourse[] = [];
  /** Who takes over the courses of someone losing Trainer access (FR-20-07). */
  revokeTransferToId: string | null = null;
  coursesLoading = false;

  saving = false;

  historyTarget: TrainerAccessStaff | null = null;
  history: TrainingAuditEntry[] = [];
  historyLoading = false;

  /** The date picker refuses days before today. */
  readonly disabledDate = (d: Date): boolean => startOfDay(d) < startOfDay(new Date());

  constructor(private trainerAccess: TrainerAccessService, private message: NzMessageService,
              private route: ActivatedRoute, private router: Router, private destroyRef: DestroyRef) {}

  ngOnInit(): void {
    this.load();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.tabIndex = params.get('tab') === 'owners' ? OWNERS_TAB : STAFF_TAB;
      // A Trainer access request's notification names the person (FR-15-06).
      const search = params.get('search');
      if (search) this.search = search;
    });
  }

  /** Keeps the open tab in the address, so a refresh or a shared link opens the same tab. */
  onTabChange(index: number): void {
    this.tabIndex = index;
    this.router.navigate([], {
      relativeTo: this.route, queryParams: { tab: index === OWNERS_TAB ? 'owners' : null }, replaceUrl: true
    });
  }

  /** Reloads the staff list, and the course list when its tab is open. */
  refresh(): void {
    this.load();
    if (this.tabIndex === OWNERS_TAB) {
      this.courseOwners?.load();
    }
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
    if (!target || reasonMissing(this.grantReason, this.message, e => this.grantError = e)) return;
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
    this.revokeTransferToId = null;
    if (this.revokeNeedsTransfer) {
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

  /** Revoking would leave their courses with nobody to look after them, so someone must take them over. */
  get revokeNeedsTransfer(): boolean {
    const s = this.revokeTarget;
    return !!s && !s.viaRole && s.ownedCourses > 0;
  }

  /** Who can take the courses over: active staff with Trainer access, other than the person losing it. */
  get transferCandidates(): TrainerAccessStaff[] {
    const leaving = this.revokeTarget?.staffId;
    return this.staff.filter(s => (s.viaRole || s.viaGrant) && s.staffId !== leaving);
  }

  /** What confirming will do, in a sentence or two. */
  get revokeSummary(): string {
    const target = this.revokeTarget;
    if (!target) return '';
    if (target.viaRole) return 'Their grant ends now.';
    const loses = `${target.name} loses Trainer access within 15 minutes.`;
    if (!this.revokeNeedsTransfer) return loses;
    const to = this.transferCandidates.find(s => s.staffId === this.revokeTransferToId);
    if (!to) return `Choose who takes over their courses. ${loses}`;
    const one = target.ownedCourses === 1;
    return `${one ? 'Their course moves' : `Their ${target.ownedCourses} courses move`} to ${to.name}, `
      + `who can then edit, review and assign ${one ? 'it' : 'them'}. ${loses}`;
  }

  confirmRevoke(): void {
    const target = this.revokeTarget;
    if (!target) return;
    const show = (error: string) => this.revokeError = error;
    const transferTo = this.revokeNeedsTransfer
      ? this.transferCandidates.find(s => s.staffId === this.revokeTransferToId) ?? null
      : null;
    if (this.revokeNeedsTransfer && !transferTo) {
      showRefusal('Choose who takes over their courses.', this.message, show);
      return;
    }
    if (reasonMissing(this.revokeReason, this.message, show)) return;
    this.saving = true;
    this.revokeError = '';
    this.trainerAccess.revoke(target.staffId, this.revokeReason.trim(), transferTo?.staffId ?? null).subscribe({
      next: updated => {
        this.saving = false;
        this.revokeTarget = null;
        if (transferTo) {
          this.load(); // the new owner's course count changed too
          this.message.success(`Trainer access revoked for ${updated.name}. Their courses now belong to ${transferTo.name}.`);
          return;
        }
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
