import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, of, switchMap, catchError } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { SopAssignmentService } from '../../../services/sop-assignment.service';
import {
  AssignableCourse, Assignment, AssignmentOutcome, AssignmentPreview, AssignmentRequest, Audience, AudienceGroup,
  SopAssignmentMode
} from '../../../models/sop-assignment.model';

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Assign Training (FR-09): choose a ready course, who takes it (everyone, departments, job roles
 * or named staff, with suggestions from the SOP's department), how it reaches them, the pass mark
 * and the dates, then preview and assign. Trainers see only their own courses (FR-09-07).
 */
@Component({
  selector: 'app-sop-assign',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzButtonModule, NzDatePickerModule, NzEmptyModule, NzIconModule, NzInputNumberModule, NzPopconfirmModule,
    NzRadioModule, NzSelectModule, NzSpinModule, NzSwitchModule, NzTagModule
  ],
  templateUrl: './sop-assign.component.html',
  styleUrls: ['./sop-assign.component.scss']
})
export class SopAssignComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly previewTrigger = new Subject<void>();

  courses: AssignableCourse[] = [];
  audience: Audience = { departments: [], roles: [], staff: [] };
  history: Assignment[] = [];
  loading = true;
  historyLoading = false;

  selectedCourseId: number | null = null;
  audienceMode: 'everyone' | 'specific' = 'specific';
  departmentIds: number[] = [];
  roleIds: number[] = [];
  staffIds: string[] = [];
  mode: SopAssignmentMode = 'PORTAL';
  mandatory = false;
  passMark = 70;
  effectiveDate: Date = this.today();
  deadline: Date | null = new Date(this.today().getTime() + 30 * DAY_MS);
  daysToComplete = 14;

  preview: AssignmentPreview | null = null;
  previewLoading = false;
  assigning = false;
  savingVisibility = false;

  constructor(private assignments: SopAssignmentService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.previewTrigger.pipe(
      debounceTime(300),
      switchMap(() => {
        const request = this.audienceReady ? this.buildRequest() : null;
        if (!request) {
          return of(null);
        }
        this.previewLoading = true;
        return this.assignments.preview(request).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(preview => {
      this.preview = preview;
      this.previewLoading = false;
    });

    this.assignments.audience().subscribe({ next: a => (this.audience = a) });
    this.loadCourses();
    this.loadHistory();
  }

  private loadCourses(): void {
    this.assignments.courses().subscribe({
      next: courses => { this.courses = courses; this.loading = false; },
      error: () => { this.loading = false; this.message.error('Could not load courses.'); }
    });
  }

  private loadHistory(): void {
    this.historyLoading = true;
    this.assignments.history(this.selectedCourseId ?? undefined).subscribe({
      next: h => { this.history = h; this.historyLoading = false; },
      error: () => { this.history = []; this.historyLoading = false; }
    });
  }

  // --- course ---------------------------------------------------------------

  get selectedCourse(): AssignableCourse | undefined {
    return this.courses.find(c => c.id === this.selectedCourseId);
  }

  selectCourse(course: AssignableCourse): void {
    if (!course.ready || this.assigning) return;
    this.selectedCourseId = course.id;
    this.loadHistory();
    this.refreshPreview();
  }

  toggleVisibility(open: boolean): void {
    const course = this.selectedCourse;
    if (!course) return;
    this.savingVisibility = true;
    this.assignments.setVisibility(course.id, open ? 'OPEN' : 'PRIVATE').subscribe({
      next: updated => {
        this.courses = this.courses.map(c => (c.id === updated.id ? updated : c));
        this.savingVisibility = false;
        this.message.success(open
          ? 'Course is now Open: any staff member can enrol from the Learning Portal.'
          : 'Course is now Private: only assigned staff can see it.');
      },
      error: err => { this.savingVisibility = false; this.message.error(err?.error?.message || 'Could not change visibility.'); }
    });
  }

  // --- audience ---------------------------------------------------------------

  /** Departments and job roles matching the SOP's department that are not chosen yet (FR-09-01). */
  get suggestions(): { departments: AudienceGroup[]; roles: AudienceGroup[] } {
    const course = this.selectedCourse;
    if (!course) return { departments: [], roles: [] };
    return {
      departments: this.audience.departments.filter(d =>
        course.recommendedDepartmentIds.includes(d.id) && !this.departmentIds.includes(d.id)),
      roles: this.audience.roles.filter(r => course.recommendedRoleIds.includes(r.id) && !this.roleIds.includes(r.id))
    };
  }

  addSuggestions(): void {
    const s = this.suggestions;
    this.departmentIds = [...this.departmentIds, ...s.departments.map(d => d.id)];
    this.refreshPreview();
  }

  get audienceReady(): boolean {
    return this.audienceMode === 'everyone'
      || this.departmentIds.length > 0 || this.roleIds.length > 0 || this.staffIds.length > 0;
  }

  refreshPreview(): void {
    if (!this.selectedCourseId || !this.audienceReady) {
      this.preview = null;
      return;
    }
    this.previewTrigger.next();
  }

  outcomeLabel(outcome: AssignmentOutcome): string {
    return outcome === 'NEW' ? 'New' : outcome === 'UPDATE' ? 'Already enrolled: terms updated' : 'Completed: not changed';
  }

  outcomeColor(outcome: AssignmentOutcome): string {
    return outcome === 'NEW' ? 'green' : outcome === 'UPDATE' ? 'blue' : 'default';
  }

  // --- dates ------------------------------------------------------------------

  private today(): Date {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d;
  }

  disablePastDates = (d: Date): boolean => d.getTime() < this.today().getTime() - 1;

  disableBeforeEffective = (d: Date): boolean => {
    const start = new Date(this.effectiveDate);
    start.setHours(0, 0, 0, 0);
    return d.getTime() < start.getTime();
  };

  onEffectiveDateChange(): void {
    if (this.deadline && this.deadline < this.effectiveDate) {
      this.deadline = new Date(this.effectiveDate);
    }
  }

  /** "Start immediately": everyone finishes by effective date + days, capped by the deadline. */
  get immediateDue(): Date | null {
    if (!this.deadline) return null;
    const window = new Date(this.effectiveDate.getTime() + this.daysToComplete * DAY_MS);
    return window < this.deadline ? window : this.deadline;
  }

  get daysUntilDeadline(): number | null {
    return this.deadline ? Math.round((this.deadline.getTime() - this.today().getTime()) / DAY_MS) : null;
  }

  // --- assign -----------------------------------------------------------------

  get validationMessage(): string | null {
    const course = this.selectedCourse;
    if (!course) return 'Select a course.';
    if (!this.audienceReady) return 'Choose who should take it.';
    if (!this.deadline) return 'Set a deadline.';
    if (!this.daysToComplete || this.daysToComplete < 1 || this.daysToComplete > 365) return 'Days to complete must be 1 to 365.';
    if (!this.passMark || this.passMark < 1 || this.passMark > 100) return 'Pass mark must be 1 to 100.';
    if (this.preview && this.preview.newCount + this.preview.updateCount === 0) {
      return this.preview.completedCount ? 'Everyone selected has already completed this course.' : 'Nobody matches this audience.';
    }
    return null;
  }

  get recipientCount(): number {
    return this.preview ? this.preview.newCount + this.preview.updateCount : 0;
  }

  private buildRequest(): AssignmentRequest | null {
    if (!this.selectedCourseId) return null;
    const everyone = this.audienceMode === 'everyone';
    return {
      sopDocumentId: this.selectedCourseId,
      everyone,
      departmentIds: everyone ? [] : this.departmentIds,
      roleIds: everyone ? [] : this.roleIds,
      staffIds: everyone ? [] : this.staffIds,
      mode: this.mode,
      mandatory: this.mandatory,
      effectiveDate: formatDate(this.effectiveDate, 'yyyy-MM-dd', 'en-US'),
      deadline: this.deadline ? formatDate(this.deadline, 'yyyy-MM-dd', 'en-US') : '',
      daysToComplete: this.daysToComplete,
      passMark: this.passMark
    };
  }

  assign(): void {
    const request = this.buildRequest();
    if (!request || this.validationMessage) return;
    this.assigning = true;
    this.assignments.assign(request).subscribe({
      next: result => {
        this.assigning = false;
        const parts = [`${result.assigned} newly assigned`];
        if (result.updated) parts.push(`${result.updated} updated`);
        if (result.skippedCompleted) parts.push(`${result.skippedCompleted} already completed`);
        this.message.success(`Assigned "${result.assignment.courseTitle}": ${parts.join(', ')}. It now shows in their Learning Portal.`);
        this.loadCourses();
        this.loadHistory();
        this.refreshPreview();
      },
      error: err => { this.assigning = false; this.message.error(err?.error?.message || 'Assignment failed.'); }
    });
  }

  modeLabel(mode: SopAssignmentMode): string {
    return mode === 'IMMEDIATE' ? 'Start immediately' : 'Learning Portal';
  }
}
