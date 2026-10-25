import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
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
import { TrainerAccessService } from '../../../../services/trainer-access.service';
import {
  CourseOwner, CourseOwnerStatus, TrainerAccessStaff, TrainingAuditEntry
} from '../../../../models/trainer-access.model';
import { MAX_REASON, auditAction, errorMessage, reasonMissing, showRefusal } from '../trainer-access.util';

type CourseView = 'NEEDS_OWNER' | 'ALL';

interface StatusLook { label: string; color: string; hint: string; }

const STATUS: Record<CourseOwnerStatus, StatusLook> = {
  OK: { label: 'Owner has access', color: 'green', hint: 'The owner can still edit, review and assign this course.' },
  NO_TRAINER_ACCESS: {
    label: 'Owner lost Trainer access', color: 'orange',
    hint: 'The owner is still on staff but no longer has Trainer access, so nobody can change or assign this course.'
  },
  OWNER_LEFT: {
    label: 'Owner has left', color: 'red',
    hint: "The owner's account is inactive or removed, so nobody can change or assign this course."
  },
  NO_OWNER: { label: 'No owner', color: 'default', hint: 'This course has never had an owner, so nobody can change or assign it.' },
};

/**
 * The Course owners tab of Trainer Access (FR-20-06). Lists every course with its owner, and lets the Super Admin
 * give a course whose owner has left or lost Trainer access to someone who has it. Only the new owner is told.
 */
@Component({
  selector: 'app-course-owners',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzAlertModule, NzButtonModule, NzDrawerModule, NzEmptyModule, NzIconModule, NzInputModule, NzModalModule,
    NzRadioModule, NzSelectModule, NzSpinModule, NzTableModule, NzTagModule, NzTimelineModule
  ],
  templateUrl: './course-owners.component.html',
  styleUrls: ['./course-owners.component.scss']
})
export class CourseOwnersComponent implements OnChanges {
  /** Whether the tab is open. The list loads each time it opens, so changes made on the Staff tab show. */
  @Input() active = false;
  /** Active staff from the Staff tab; those with Trainer access can take a course over. */
  @Input() staff: TrainerAccessStaff[] = [];
  /** A course changed owner, so the Staff tab's course counts are out of date. */
  @Output() transferred = new EventEmitter<void>();

  readonly maxReason = MAX_REASON;
  readonly actionOf = auditAction;
  readonly viewOptions: { value: CourseView; label: string }[] = [
    { value: 'NEEDS_OWNER', label: 'Needs a new owner' },
    { value: 'ALL', label: 'All courses' },
  ];

  courses: CourseOwner[] = [];
  filtered: CourseOwner[] = [];
  counts = { all: 0, needsOwner: 0 };
  loading = false;
  view: CourseView = 'NEEDS_OWNER';
  search = '';

  transferTarget: CourseOwner | null = null;
  newOwnerId: string | null = null;
  transferReason = '';
  transferError = '';
  saving = false;

  historyTarget: CourseOwner | null = null;
  history: TrainingAuditEntry[] = [];
  historyLoading = false;

  constructor(private trainerAccess: TrainerAccessService, private message: NzMessageService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['active'] && this.active) {
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.trainerAccess.courseOwners().subscribe({
      next: list => {
        this.courses = list;
        this.recount();
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.message.error('Could not load the courses.');
      }
    });
  }

  applyFilters(): void {
    const q = this.search.trim().toLowerCase();
    this.filtered = this.courses.filter(c =>
      (this.view === 'ALL' || c.needsNewOwner)
      && (!q || [c.title, c.ownerName ?? ''].some(v => v.toLowerCase().includes(q))));
  }

  get emptyText(): string {
    if (!this.courses.length) return 'No courses yet';
    if (this.search.trim()) return 'No course matches this search';
    return 'Every course has an owner with Trainer access';
  }

  statusOf(c: CourseOwner): StatusLook {
    return STATUS[c.status] ?? { label: c.status, color: 'default', hint: '' };
  }

  // === transfer (FR-20-06) ==================================================

  openTransfer(c: CourseOwner): void {
    this.transferTarget = c;
    this.newOwnerId = null;
    this.transferReason = '';
    this.transferError = '';
  }

  closeTransfer(): void {
    if (!this.saving) {
      this.transferTarget = null;
    }
  }

  /** Active staff with Trainer access, other than the current owner. */
  get candidates(): TrainerAccessStaff[] {
    const ownerId = this.transferTarget?.ownerId;
    return this.staff.filter(s => (s.viaRole || s.viaGrant) && s.staffId !== ownerId);
  }

  /** What confirming will do, in a sentence or two. */
  get transferSummary(): string {
    const course = this.transferTarget;
    const to = this.candidates.find(s => s.staffId === this.newOwnerId);
    if (!course || !to) return 'Choose who takes this course over.';
    return `"${course.title}" moves to ${to.name}, who can then edit, review and assign it. `
      + 'Staff already enrolled keep their progress.';
  }

  confirmTransfer(): void {
    const course = this.transferTarget;
    if (!course) return;
    const show = (error: string) => this.transferError = error;
    const to = this.candidates.find(s => s.staffId === this.newOwnerId);
    if (!to) {
      showRefusal('Choose who takes this course over.', this.message, show);
      return;
    }
    if (reasonMissing(this.transferReason, this.message, show)) return;
    this.saving = true;
    this.transferError = '';
    this.trainerAccess.transferCourse(course.courseId, { newOwnerId: to.staffId, reason: this.transferReason.trim() })
      .subscribe({
        next: updated => {
          this.saving = false;
          this.transferTarget = null;
          this.courses = this.courses.map(c => c.courseId === updated.courseId ? updated : c);
          this.recount();
          this.applyFilters();
          this.message.success(`"${updated.title}" now belongs to ${to.name}.`);
          this.transferred.emit();
        },
        error: (e: HttpErrorResponse) => {
          this.saving = false;
          this.transferError = errorMessage(e);
        }
      });
  }

  // === history (NFR-007) ====================================================

  openHistory(c: CourseOwner): void {
    this.historyTarget = c;
    this.history = [];
    this.historyLoading = true;
    this.trainerAccess.courseHistory(c.courseId).subscribe({
      next: entries => { this.history = entries; this.historyLoading = false; },
      error: () => { this.historyLoading = false; this.message.error('Could not load the history.'); }
    });
  }

  closeHistory(): void {
    this.historyTarget = null;
  }

  private recount(): void {
    this.counts = { all: this.courses.length, needsOwner: this.courses.filter(c => c.needsNewOwner).length };
  }
}
