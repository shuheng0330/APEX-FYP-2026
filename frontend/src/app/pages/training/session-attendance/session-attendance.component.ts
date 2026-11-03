import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { AudiencePerson } from '../../../models/sop-assignment.model';
import {
  TrainingAttendanceEntry, TrainingAttendanceRow, TrainingAttendanceSheet, TrainingAttendanceStatus, TrainingBatchResult,
  TrainingEvidence, TrainingSession
} from '../../../models/training-session.model';
import { SopAssignmentService } from '../../../services/sop-assignment.service';
import { TrainingSessionService } from '../../../services/training-session.service';
import { saveFile, sessionFileName } from '../training.util';

type Mark = TrainingAttendanceStatus | null;

/** Evidence per session (decision B, 28 Oct 2026); the server checks the same. */
const MAX_FILES = 5;
const MAX_BYTES = 10 * 1024 * 1024;
/** The largest HRDC attendance file the server reads. */
const MAX_SHEET_BYTES = 2 * 1024 * 1024;

/**
 * A session's attendance, for its trainer and HR (FR-14): mark each person Attended or Absent with any quiz score,
 * add walk-ins, attach evidence, then submit once everyone is marked. From then on, or after the trainer's 7 days,
 * only HR changes it, giving a reason that is logged. The Super Admin sees it without changing it. Everyone can also be
 * marked at once from the HRDC attendance file (FR-14-05 to FR-14-07).
 */
@Component({
  selector: 'app-session-attendance',
  standalone: true,
  imports: [
    CommonModule, FormsModule, NzAlertModule, NzButtonModule, NzIconModule, NzInputModule, NzInputNumberModule,
    NzPopconfirmModule, NzSelectModule, NzTagModule
  ],
  templateUrl: './session-attendance.component.html',
  styleUrls: ['./session-attendance.component.scss']
})
export class SessionAttendanceComponent implements OnChanges {
  @Input({ required: true }) session!: TrainingSession;
  /** The attendance was submitted: the session's own details have changed. */
  @Output() changed = new EventEmitter<void>();

  readonly maxFiles = MAX_FILES;
  sheet: TrainingAttendanceSheet | null = null;
  loading = false;
  busy = false;
  /** Marks changed here and not saved yet, by staff id. */
  draft = new Map<string, { status: Mark; quizScore: number | null }>();
  search = '';
  /** HR's reason, needed once only HR can change the attendance. */
  reason = '';
  staff: AudiencePerson[] = [];
  walkIn: string | null = null;
  downloading = false;
  /** What the last uploaded attendance file did, or why it was refused. */
  batch: TrainingBatchResult | null = null;
  batchError: string | null = null;

  constructor(private sessions: TrainingSessionService, private assignments: SopAssignmentService,
              private message: NzMessageService) {}

  ngOnChanges(changes: SimpleChanges): void {
    const before: TrainingSession | undefined = changes['session']?.previousValue;
    if (!before || before.id !== this.session.id) {
      this.sheet = null;
      this.draft.clear();
      this.search = '';
      this.reason = '';
      this.walkIn = null;
      this.batch = null;
      this.batchError = null;
      this.load();
    }
  }

  /** The signed-in user may change the attendance now, with a reason when only HR can. */
  get editable(): boolean {
    return !!this.sheet && (this.sheet.canRecord || this.sheet.canChangeWithReason);
  }

  get needsReason(): boolean {
    return !!this.sheet && this.sheet.hrOnly;
  }

  get reasonGiven(): boolean {
    return !this.needsReason || !!this.reason.trim();
  }

  get rows(): TrainingAttendanceRow[] {
    const q = this.search.trim().toLowerCase();
    const rows = this.sheet?.rows ?? [];
    const ic = q.replace(/[\s-]/g, '');
    return q ? rows.filter(r => r.name.toLowerCase().includes(q)
      || (r.departmentName ?? '').toLowerCase().includes(q)
      || (!!ic && (r.idNumber ?? '').toLowerCase().includes(ic))) : rows;
  }

  /** The counts as marked here, saved or not. */
  get counts(): { attended: number; absent: number; notRecorded: number } {
    const rows = this.sheet?.rows ?? [];
    const n = (m: Mark) => rows.filter(r => this.markOf(r) === m).length;
    return { attended: n('ATTENDED'), absent: n('ABSENT'), notRecorded: n(null) };
  }

  get attendedPercent(): number {
    const total = this.sheet?.rows.length ?? 0;
    return total ? Math.round((this.counts.attended * 100) / total) : 0;
  }

  get unsaved(): number {
    return this.draft.size;
  }

  /** Staff who could be added as walk-ins: not on the list, and not whoever runs the session. */
  get addable(): AudiencePerson[] {
    const on = new Set((this.sheet?.rows ?? []).map(r => r.staffId));
    return this.staff.filter(p => !on.has(p.id) && p.id !== this.session.trainerId);
  }

  /** Evidence can be added while attendance can be recorded; after that by HR. */
  get canChangeEvidence(): boolean {
    return this.editable;
  }

  /** "10 of 12 rows matched: 8 attended, 1 absent, 1 left blank (not changed). 9 marks changed." */
  get batchSummary(): string {
    const b = this.batch;
    if (!b) return '';
    const marks = [`${b.attended} attended`, `${b.absent} absent`];
    if (b.leftBlank) marks.push(`${b.leftBlank} left blank (not changed)`);
    return `${b.matched} of ${b.rows} ${b.rows === 1 ? 'row' : 'rows'} matched: ${marks.join(', ')}. `
      + (b.changed ? `${b.changed} ${b.changed === 1 ? 'mark' : 'marks'} changed.` : 'Nothing needed changing.');
  }

  markOf(r: TrainingAttendanceRow): Mark {
    const d = this.draft.get(r.staffId);
    if (d) return d.status;
    return r.status === 'NOT_RECORDED' ? null : r.status;
  }

  scoreOf(r: TrainingAttendanceRow): number | null {
    const d = this.draft.get(r.staffId);
    return d ? d.quizScore : r.quizScore ?? null;
  }

  isChanged(r: TrainingAttendanceRow): boolean {
    return this.draft.has(r.staffId);
  }

  setMark(r: TrainingAttendanceRow, mark: Mark): void {
    // Pressing the chosen mark again clears it: Not recorded.
    const next = this.markOf(r) === mark ? null : mark;
    this.put(r, next, next === 'ATTENDED' ? this.scoreOf(r) : null);
  }

  setScore(r: TrainingAttendanceRow, score: number | null): void {
    this.put(r, this.markOf(r), score === null || score === undefined ? null : Math.round(score));
  }

  /** Everyone not marked yet is marked Attended. */
  markRestAttended(): void {
    for (const r of this.sheet?.rows ?? []) {
      if (this.markOf(r) === null) this.put(r, 'ATTENDED', null);
    }
  }

  discard(): void {
    this.draft.clear();
  }

  save(): void {
    if (!this.unsaved || !this.reasonGiven) return;
    const entries: TrainingAttendanceEntry[] = [...this.draft.entries()]
      .map(([staffId, d]) => ({ staffId, status: d.status, quizScore: d.quizScore }));
    this.run(this.sessions.recordAttendance(this.session.id, entries, this.reasonFor()), 'Attendance saved.', () => {
      this.draft.clear();
    });
  }

  addWalkIn(): void {
    if (!this.walkIn || !this.reasonGiven) return;
    const name = this.staff.find(p => p.id === this.walkIn)?.name ?? 'They';
    this.run(this.sessions.addWalkIn(this.session.id, this.walkIn, this.reasonFor()),
      `${name} was added as a walk-in, marked Attended.`, () => (this.walkIn = null));
  }

  submit(): void {
    if (!this.sheet?.canSubmit || this.unsaved || !this.reasonGiven) return;
    this.run(this.sessions.submitAttendance(this.session.id, this.reasonFor()),
      'Attendance submitted. Staff can now see their own attendance.', () => this.changed.emit());
  }

  upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.sheet) return;
    if (this.sheet.evidence.length >= MAX_FILES) {
      this.message.error(`A session can have up to ${MAX_FILES} evidence files. Remove one first.`);
      return;
    }
    if (file.size > MAX_BYTES) {
      this.message.error('Evidence files can be up to 10 MB.');
      return;
    }
    this.run(this.sessions.addEvidence(this.session.id, file), `${file.name} attached.`);
  }

  /** Opens the file in a new tab; the tab is opened first, so the browser doesn't block it. */
  view(e: TrainingEvidence): void {
    const tab = window.open('', '_blank');
    this.sessions.evidenceFile(this.session.id, e.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        if (tab) {
          tab.location.href = url;
        } else {
          const a = document.createElement('a');
          a.href = url;
          a.download = e.fileName;
          a.click();
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        tab?.close();
        this.message.error('Could not open the file.');
      }
    });
  }

  removeEvidence(e: TrainingEvidence): void {
    this.run(this.sessions.removeEvidence(this.session.id, e.id), `${e.fileName} removed.`);
  }

  /** FR-14-05: everyone on the session, with any marks so far, to fill in Excel. */
  downloadTemplate(): void {
    if (this.downloading) return;
    this.downloading = true;
    this.sessions.hrdcTemplate(this.session.id).subscribe({
      next: blob => {
        this.downloading = false;
        saveFile(blob, sessionFileName('HRDC attendance', this.session));
      },
      error: () => {
        this.downloading = false;
        this.message.error('Could not download the template. Reload the calendar and try again.');
      }
    });
  }

  /** FR-14-06, FR-14-07: the filled-in file, applied in one go; a refused file's reasons stay on screen. */
  uploadSheet(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.busy || this.unsaved || !this.reasonGiven) return;
    if (file.size > MAX_SHEET_BYTES) {
      this.batch = null;
      this.batchError = 'The attendance file can be up to 2 MB.';
      return;
    }
    this.busy = true;
    this.batch = null;
    this.batchError = null;
    this.sessions.uploadAttendance(this.session.id, file, this.reasonFor()).subscribe({
      next: result => {
        this.busy = false;
        this.sheet = result.sheet;
        this.batch = result;
        if (this.needsReason) this.reason = '';
      },
      error: err => {
        this.busy = false;
        this.batchError = err?.error?.message || 'Could not read the file. Check it is the filled-in template, then try again.';
      }
    });
  }

  size(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private put(r: TrainingAttendanceRow, status: Mark, quizScore: number | null): void {
    const saved: Mark = r.status === 'NOT_RECORDED' ? null : r.status;
    if (status === saved && quizScore === (r.quizScore ?? null)) {
      this.draft.delete(r.staffId);
    } else {
      this.draft.set(r.staffId, { status, quizScore });
    }
  }

  private reasonFor(): string | undefined {
    return this.needsReason ? this.reason.trim() : undefined;
  }

  private load(): void {
    this.loading = true;
    this.sessions.attendance(this.session.id).subscribe({
      next: sheet => {
        this.sheet = sheet;
        this.loading = false;
        if (this.editable && !this.staff.length) {
          this.assignments.audience().subscribe({ next: a => (this.staff = a.staff) });
        }
      },
      error: err => {
        this.loading = false;
        this.message.error(err?.error?.message || 'Could not load the attendance.');
      }
    });
  }

  private run(call: Observable<TrainingAttendanceSheet>, done: string, after?: () => void): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: sheet => {
        this.busy = false;
        this.sheet = sheet;
        if (this.needsReason) this.reason = '';
        after?.();
        this.message.success(done);
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'That did not work. Reload the calendar and try again.');
      }
    });
  }
}
