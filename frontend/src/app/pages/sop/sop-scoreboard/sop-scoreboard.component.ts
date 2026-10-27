import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, exhaustMap, filter, forkJoin, of, timer } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { SopScoreboardService } from '../../../services/sop-scoreboard.service';
import {
  CompetencyScore, EnrollmentProgress, Scoreboard, ScoreboardRecord, ScoreboardStatus
} from '../../../models/sop-scoreboard.model';
import { LearningModuleState } from '../../../models/sop-learning.model';

type StatusFilter = 'ALL' | ScoreboardStatus | 'OVERDUE';

/** One bar of the department chart. */
interface DepartmentScore {
  name: string;
  /** Average score of the department's scored enrolments; null before anyone has a score. */
  average: number | null;
  scored: number;
  certified: number;
  enrolled: number;
  retraining: number;
}

interface Kpis {
  enrolled: number;
  certified: number;
  inProgress: number;
  retraining: number;
  notStarted: number;
  overdue: number;
  failedAttempts: number;
  courses: number;
  certificationRate: number;
}

const REFRESH_SECONDS = 30;
const NO_DEPARTMENT = 'No department';

const STATUS_TAGS: Record<ScoreboardStatus, { label: string; color: string; icon: string }> = {
  COMPLETED: { label: 'Certified', color: 'green', icon: 'check-circle' },
  IN_PROGRESS: { label: 'In progress', color: 'blue', icon: 'sync' },
  RETRAINING: { label: 'Retraining', color: 'orange', icon: 'redo' },
  NOT_STARTED: { label: 'Not started', color: 'default', icon: 'minus-circle' },
  WITHDRAWN: { label: 'Withdrawn', color: 'default', icon: 'stop' },
};

const MODULE_TAGS: Record<LearningModuleState, { label: string; color: string }> = {
  PASSED: { label: 'Passed', color: 'green' },
  RETRAINING: { label: 'Retraining', color: 'orange' },
  AVAILABLE: { label: 'Not attempted', color: 'default' },
  LOCKED: { label: 'Locked', color: 'default' },
  UNAVAILABLE: { label: 'Being updated', color: 'default' },
};

/**
 * The Training Scoreboard (FR-10): live progress of staff on SOP courses, including those in
 * retraining (FR-10-01, FR-10-04, FR-10-05), filtered by department, course and status
 * (FR-10-03), with each enrolment's module-by-module history (FR-10-02). Trainers see their own
 * courses; HR sees every course. Refreshes itself every 30 seconds while open.
 */
@Component({
  selector: 'app-sop-scoreboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    NzButtonModule, NzDrawerModule, NzEmptyModule, NzIconModule, NzInputModule, NzSelectModule, NzSpinModule,
    NzSwitchModule, NzTableModule, NzTagModule
  ],
  templateUrl: './sop-scoreboard.component.html',
  styleUrls: ['./sop-scoreboard.component.scss']
})
export class SopScoreboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  readonly refreshSeconds = REFRESH_SECONDS;
  readonly ticks = [0, 25, 50, 75, 100];
  readonly statusOptions: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All statuses' },
    { value: 'COMPLETED', label: 'Certified' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'RETRAINING', label: 'Retraining' },
    { value: 'NOT_STARTED', label: 'Not started' },
    { value: 'WITHDRAWN', label: 'Withdrawn' },
    { value: 'OVERDUE', label: 'Overdue' },
  ];

  data?: Scoreboard;
  loading = true;
  refreshing = false;
  autoRefresh = true;
  updatedAt?: Date;

  search = '';
  department: string | null = null;
  courseId: number | null = null;
  status: StatusFilter = 'ALL';

  filtered: ScoreboardRecord[] = [];
  kpis: Kpis = this.kpisOf([]);
  departmentScores: DepartmentScore[] = [];
  chartView: 'chart' | 'table' = 'chart';
  hovered: DepartmentScore | null = null;

  detailOpen = false;
  detailLoading = false;
  detail?: EnrollmentProgress;
  /** The staff member's final scores as the Staff Appraisal module reads them (FR-10-06). */
  competency: CompetencyScore[] = [];

  constructor(private scoreboardService: SopScoreboardService) {}

  ngOnInit(): void {
    // Load now, then quietly every 30 seconds while auto-refresh is on and the tab is visible.
    timer(0, REFRESH_SECONDS * 1000).pipe(
      filter(tick => tick === 0 || (this.autoRefresh && !document.hidden)),
      exhaustMap(tick => {
        this.refreshing = tick > 0;
        return this.scoreboardService.scoreboard(tick > 0).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => this.receive(data));
  }

  refreshNow(): void {
    this.refreshing = true;
    this.scoreboardService.scoreboard(true).pipe(catchError(() => of(null))).subscribe(data => this.receive(data));
  }

  private receive(data: Scoreboard | null): void {
    this.loading = false;
    this.refreshing = false;
    if (data) {
      this.data = data;
      this.updatedAt = new Date();
      this.applyFilters();
    }
  }

  // --- filters (FR-10-03): one row that scopes the tiles, the chart and the table ----------

  applyFilters(): void {
    const q = this.search.trim().toLowerCase();
    this.filtered = (this.data?.records ?? []).filter(r =>
      (!q || r.staffName.toLowerCase().includes(q) || (r.jobRole ?? '').toLowerCase().includes(q))
      && (!this.department || (r.department ?? NO_DEPARTMENT) === this.department)
      && (!this.courseId || r.sopDocumentId === this.courseId)
      && (this.status === 'ALL' || (this.status === 'OVERDUE' ? r.overdue : r.status === this.status)));
    this.kpis = this.kpisOf(this.filtered);
    this.departmentScores = this.departmentsOf(this.filtered);
    if (this.hovered && !this.departmentScores.some(d => d.name === this.hovered!.name)) {
      this.hovered = null;
    }
  }

  get hasFilters(): boolean {
    return !!this.search.trim() || !!this.department || !!this.courseId || this.status !== 'ALL';
  }

  clearFilters(): void {
    this.search = '';
    this.department = null;
    this.courseId = null;
    this.status = 'ALL';
    this.applyFilters();
  }

  get departmentOptions(): string[] {
    const names = this.data?.departments ?? [];
    return this.data?.records.some(r => !r.department) ? [...names, NO_DEPARTMENT] : names;
  }

  /** Whose training the page shows (FR-10-10). */
  get scopeNote(): string {
    switch (this.data?.scope) {
      case 'ALL': return 'Every course.';
      case 'TEAM': return 'Your team: the staff who report to you directly, on every course.';
      case 'OWN_AND_TEAM': return 'The courses you own, and your team: the staff who report to you directly.';
      default: return 'The courses you own.';
    }
  }

  /** Staff who have left the company stay listed but are not counted in the figures (FR-10-12). */
  get leaversShown(): number {
    return this.filtered.filter(r => r.leftCompany).length;
  }

  private kpisOf(all: ScoreboardRecord[]): Kpis {
    const records = all.filter(r => !r.leftCompany);
    const count = (status: ScoreboardStatus) => records.filter(r => r.status === status).length;
    const certified = count('COMPLETED');
    // Withdrawn staff can no longer be certified, so they don't count against the rate (FR-06-15).
    const certifiable = records.length - count('WITHDRAWN');
    return {
      enrolled: records.length,
      certified,
      inProgress: count('IN_PROGRESS'),
      retraining: count('RETRAINING'),
      notStarted: count('NOT_STARTED'),
      overdue: records.filter(r => r.overdue).length,
      failedAttempts: records.reduce((sum, r) => sum + r.retrainingAttempts, 0),
      courses: new Set(records.map(r => r.sopDocumentId)).size,
      certificationRate: certifiable ? Math.round(100 * certified / certifiable) : 0,
    };
  }

  /** Average score per department, highest first; departments without scores go last. */
  private departmentsOf(records: ScoreboardRecord[]): DepartmentScore[] {
    const byName = new Map<string, DepartmentScore & { total: number }>();
    for (const r of records.filter(x => !x.leftCompany)) { // FR-10-12
      const name = r.department ?? NO_DEPARTMENT;
      const d = byName.get(name) ?? { name, average: null, scored: 0, certified: 0, enrolled: 0, retraining: 0, total: 0 };
      d.enrolled++;
      d.certified += r.status === 'COMPLETED' ? 1 : 0;
      d.retraining += r.status === 'RETRAINING' ? 1 : 0;
      if (r.score != null) {
        d.scored++;
        d.total += r.score;
      }
      byName.set(name, d);
    }
    return [...byName.values()]
      .map(({ total, ...d }) => ({ ...d, average: d.scored ? Math.round(total / d.scored) : null }))
      .sort((a, b) => (b.average ?? -1) - (a.average ?? -1) || a.name.localeCompare(b.name));
  }

  barLabel(d: DepartmentScore): string {
    return `${d.name}: ${d.average === null ? 'no scores yet' : 'average score ' + d.average + '%'}, `
      + `${d.certified} of ${d.enrolled} certified${d.retraining ? ', ' + d.retraining + ' retraining' : ''}`;
  }

  // --- table ------------------------------------------------------------------

  readonly sortStaff = (a: ScoreboardRecord, b: ScoreboardRecord) => a.staffName.localeCompare(b.staffName);
  readonly sortDepartment = (a: ScoreboardRecord, b: ScoreboardRecord) =>
    (a.department ?? '').localeCompare(b.department ?? '');
  readonly sortCourse = (a: ScoreboardRecord, b: ScoreboardRecord) => a.courseTitle.localeCompare(b.courseTitle);
  readonly sortModules = (a: ScoreboardRecord, b: ScoreboardRecord) => this.percent(a) - this.percent(b);
  readonly sortScore = (a: ScoreboardRecord, b: ScoreboardRecord) => (a.score ?? -1) - (b.score ?? -1);
  readonly sortRetraining = (a: ScoreboardRecord, b: ScoreboardRecord) => a.retrainingAttempts - b.retrainingAttempts;
  readonly sortAttempts = (a: ScoreboardRecord, b: ScoreboardRecord) => a.attempts - b.attempts;
  readonly sortLastAttempt = (a: ScoreboardRecord, b: ScoreboardRecord) =>
    (a.lastAttemptAt ?? '').localeCompare(b.lastAttemptAt ?? '');
  readonly sortDue = (a: ScoreboardRecord, b: ScoreboardRecord) =>
    (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999');

  percent(r: ScoreboardRecord): number {
    return r.moduleCount ? Math.round(100 * r.modulesPassed / r.moduleCount) : 0;
  }

  statusTag(status: ScoreboardStatus): { label: string; color: string; icon: string } {
    return STATUS_TAGS[status];
  }

  moduleTag(state: LearningModuleState): { label: string; color: string } {
    return MODULE_TAGS[state];
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('');
  }

  // --- details drawer (FR-10-02) ---------------------------------------------

  openDetail(r: ScoreboardRecord): void {
    this.detailOpen = true;
    this.detailLoading = true;
    this.detail = undefined;
    this.competency = [];
    forkJoin({
      detail: this.scoreboardService.enrollment(r.enrollmentId),
      competency: this.scoreboardService.competencyScores(r.staffId).pipe(catchError(() => of([] as CompetencyScore[])))
    }).subscribe({
      next: ({ detail, competency }) => {
        this.detail = detail;
        this.competency = competency;
        this.detailLoading = false;
      },
      error: () => { this.detailLoading = false; this.detailOpen = false; }
    });
  }

  closeDetail(): void {
    this.detailOpen = false;
  }

  // --- export -------------------------------------------------------------------

  /** Downloads the filtered records as a CSV file. */
  exportCsv(): void {
    const header = ['Staff', 'Job role', 'Department', 'Course', 'Modules passed', 'Modules', 'Status', 'Overdue',
      'Score (%)', 'Final score (%)', 'Retraining attempts', 'Quiz attempts', 'Last attempt', 'Due date', 'Mandatory'];
    const rows = this.filtered.map(r => [
      r.staffName, r.jobRole, r.department, r.courseTitle, r.modulesPassed, r.moduleCount,
      STATUS_TAGS[r.status].label, r.overdue ? 'Yes' : 'No', r.score, r.finalScore, r.retrainingAttempts, r.attempts,
      r.lastAttemptAt ? formatDate(r.lastAttemptAt, 'yyyy-MM-dd HH:mm', 'en-US') : '', r.dueDate, r.mandatory ? 'Yes' : 'No'
    ]);
    const csv = [header, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `training-scoreboard-${formatDate(new Date(), 'yyyy-MM-dd', 'en-US')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}

/** Quotes a CSV cell, and stops spreadsheets treating text such as "=..." as a formula. */
function csvCell(value: unknown): string {
  let text = value == null ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(text)) {
    text = "'" + text;
  }
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
}
