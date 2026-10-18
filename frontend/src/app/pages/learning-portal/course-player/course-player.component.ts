import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, of, switchMap } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRateModule } from 'ng-zorro-antd/rate';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzToolTipModule } from 'ng-zorro-antd/tooltip';
import { SopLearningService } from '../../../services/sop-learning.service';
import {
  LearningCourseDetail, LearningMaterial, LearningModule, LearningQuestion, QuestionResult, QuizResult
} from '../../../models/sop-learning.model';
import { SopContentViewComponent } from '../../../components/sop-content-view/sop-content-view.component';
import { daysUntil } from '../learning-portal.util';

type View = 'overview' | 'material' | 'quiz' | 'result';

const MAX_COMMENT = 2000;

/**
 * Takes one SOP course (FR-11): modules unlock in order (FR-11-01); each module's material is
 * watermarked and cannot be copied; a quiz ends each module (FR-11-02); a fail puts the module
 * into practice mode until it is passed (FR-11-04); progress is saved on the server so the
 * learner resumes where they left off (FR-11-10); passing every module awards the SOP
 * certificate (FR-11-03), after which the learner can rate the course (FR-11-06).
 */
@Component({
  selector: 'app-course-player',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, SopContentViewComponent,
    NzButtonModule, NzIconModule, NzInputModule, NzRateModule, NzSpinModule, NzTagModule, NzToolTipModule
  ],
  templateUrl: './course-player.component.html',
  styleUrls: ['./course-player.component.scss']
})
export class CoursePlayerComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  readonly maxComment = MAX_COMMENT;
  readonly letters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
  readonly phases: { key: View; label: string }[] = [
    { key: 'material', label: 'Material' },
    { key: 'quiz', label: 'Quiz' },
    { key: 'result', label: 'Results' },
  ];

  courseId = 0;
  detail?: LearningCourseDetail;
  loading = true;
  /** Set when the course cannot be opened, e.g. the learner is not enrolled. */
  loadError = '';
  view: View = 'overview';
  starting = false;

  activeModuleId: number | null = null;
  material?: LearningMaterial;
  materialLoading = false;
  watermark = '';

  questions: LearningQuestion[] = [];
  quizLoading = false;
  qIndex = 0;
  answers: Record<number, string> = {};
  submitting = false;
  result?: QuizResult;

  feedbackRating = 0;
  feedbackComment = '';
  savingFeedback = false;

  constructor(
    private route: ActivatedRoute,
    private learningService: SopLearningService,
    private message: NzMessageService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.courseId = Number(params.get('id'));
      this.view = 'overview';
      this.activeModuleId = null;
      this.load(true);
    });
  }

  /** Loads the course; on first load, resumes at the module the learner is up to (FR-11-10). */
  load(resume = false): void {
    this.loading = !this.detail || resume;
    this.loadError = '';
    this.learningService.course(this.courseId).subscribe({
      next: detail => {
        this.setDetail(detail);
        this.loading = false;
        if (resume && detail.course.status === 'IN_PROGRESS' && this.currentModule) {
          this.openModule(this.currentModule);
        } else if (this.activeModule && !this.canOpen(this.activeModule)) {
          // e.g. the trainer is updating this module's quiz
          this.showOverview();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.loadError = err?.error?.message || 'This course could not be opened.';
      }
    });
  }

  private setDetail(detail: LearningCourseDetail): void {
    this.detail = detail;
    this.feedbackRating = detail.feedbackRating ?? 0;
    this.feedbackComment = detail.feedbackComment ?? '';
    this.watermark = this.buildWatermark(detail.learnerName);
  }

  // --- course ---------------------------------------------------------------

  get modules(): LearningModule[] {
    return this.detail?.modules ?? [];
  }

  /** The first module the learner can work on: available, or in practice mode after a fail. */
  get currentModule(): LearningModule | undefined {
    return this.modules.find(m => m.state === 'AVAILABLE' || m.state === 'RETRAINING');
  }

  get activeModule(): LearningModule | undefined {
    return this.modules.find(m => m.id === this.activeModuleId);
  }

  get progressPercent(): number {
    const c = this.detail?.course;
    return c && c.moduleCount ? Math.round(100 * c.modulesPassed / c.moduleCount) : 0;
  }

  get daysLeft(): number | null {
    const due = this.detail?.course.dueDate;
    return due ? daysUntil(due) : null;
  }

  get statusText(): string {
    const c = this.detail?.course;
    switch (c?.status) {
      case 'COMPLETED': return `Completed · ${c.finalScore ?? 0}%`;
      case 'IN_PROGRESS': return `In progress · ${this.progressPercent}%`;
      case 'WITHDRAWN': return 'Withdrawn';
      default: return c?.available === false ? 'Not open yet' : 'Not started';
    }
  }

  get modeLabel(): string {
    const c = this.detail?.course;
    return c?.selfEnrolled ? 'Self-enrolled' : c?.mode === 'IMMEDIATE' ? 'Start immediately' : 'Assigned';
  }

  get dueText(): string {
    const c = this.detail?.course;
    if (!c) {
      return '';
    }
    if (c.status === 'COMPLETED') {
      return 'Completed ' + formatDate(c.completedAt!, 'd MMM yyyy', 'en-US');
    }
    if (c.status === 'WITHDRAWN') {
      return 'No longer due';
    }
    const left = this.daysLeft;
    if (!c.dueDate || left === null) {
      return c.status === 'NOT_STARTED' && c.daysToComplete
        ? `${c.daysToComplete} days from when you start` : 'No due date';
    }
    const date = formatDate(c.dueDate, 'd MMM yyyy', 'en-US');
    return left < 0 ? `${date} (overdue)` : left === 0 ? `${date} (today)` : `${date} (${left} day${left === 1 ? '' : 's'} left)`;
  }

  /** Index of the current step in the Material, Quiz, Results strip. */
  get phaseIndex(): number {
    return this.phases.findIndex(p => p.key === this.view);
  }

  /** Starts the course (which starts the learner's completion window), then opens a module. */
  startCourse(target?: LearningModule): void {
    this.starting = true;
    this.learningService.start(this.courseId).subscribe({
      next: detail => {
        this.starting = false;
        this.setDetail(detail);
        const module = target ? this.modules.find(m => m.id === target.id) : this.currentModule;
        if (module) {
          this.openModule(module);
        }
      },
      error: () => { this.starting = false; }
    });
  }

  showOverview(): void {
    this.view = 'overview';
    this.activeModuleId = null;
  }

  // --- material ---------------------------------------------------------------

  canOpen(m: LearningModule): boolean {
    return !!this.detail?.course.available && m.state !== 'LOCKED' && m.state !== 'UNAVAILABLE';
  }

  openModule(m: LearningModule): void {
    if (!this.canOpen(m)) {
      return;
    }
    if (this.detail?.course.status === 'NOT_STARTED') {
      this.startCourse(m);
      return;
    }
    this.activeModuleId = m.id;
    this.view = 'material';
    this.result = undefined;
    if (this.material?.moduleId === m.id) {
      return;
    }
    this.material = undefined;
    this.materialLoading = true;
    this.learningService.material(m.id).subscribe({
      next: material => { this.material = material; this.materialLoading = false; },
      error: () => { this.materialLoading = false; this.showOverview(); this.load(); }
    });
  }

  /** The module after the active one, if the learner can open it now. */
  get nextModule(): LearningModule | undefined {
    const index = this.modules.findIndex(m => m.id === this.activeModuleId);
    const next = index >= 0 ? this.modules[index + 1] : undefined;
    return next && this.canOpen(next) ? next : undefined;
  }

  /** Blocks copying the protected material (FR-11 content protection). */
  block(event: Event): void {
    event.preventDefault();
  }

  // --- quiz -------------------------------------------------------------------

  takeQuiz(): void {
    const m = this.activeModule;
    if (!m) {
      return;
    }
    this.quizLoading = true;
    this.learningService.quiz(m.id).subscribe({
      next: questions => {
        this.questions = questions;
        this.qIndex = 0;
        this.answers = {};
        this.result = undefined;
        this.quizLoading = false;
        this.view = 'quiz';
      },
      error: () => { this.quizLoading = false; this.load(); }
    });
  }

  get question(): LearningQuestion | undefined {
    return this.questions[this.qIndex];
  }

  /** Choices for a question; true/false questions always offer both. */
  optionsOf(q: LearningQuestion): string[] {
    if (q.type === 'TRUE_FALSE' && !q.options?.length) {
      return ['True', 'False'];
    }
    return q.options ?? [];
  }

  answer(q: LearningQuestion, value: string): void {
    this.answers = { ...this.answers, [q.id]: value };
  }

  isAnswered(q: LearningQuestion): boolean {
    return !!this.answers[q.id]?.trim();
  }

  get answeredCount(): number {
    return this.questions.filter(q => this.isAnswered(q)).length;
  }

  get allAnswered(): boolean {
    return this.questions.length > 0 && this.answeredCount === this.questions.length;
  }

  previousQuestion(): void {
    this.qIndex = Math.max(0, this.qIndex - 1);
  }

  nextQuestion(): void {
    this.qIndex = Math.min(this.questions.length - 1, this.qIndex + 1);
  }

  goToQuestion(index: number): void {
    this.qIndex = index;
  }

  submitQuiz(): void {
    const m = this.activeModule;
    if (!m || !this.allAnswered) {
      return;
    }
    this.submitting = true;
    // Refresh the course with the result so module states (and the next module) are current.
    this.learningService.submit(m.id, this.answers).pipe(
      switchMap(result => this.learningService.course(this.courseId).pipe(
        map(detail => ({ result, detail: detail as LearningCourseDetail | undefined })),
        catchError(() => of({ result, detail: undefined }))
      ))
    ).subscribe({
      next: ({ result, detail }) => {
        if (detail) {
          this.setDetail(detail);
        }
        this.submitting = false;
        this.result = result;
        this.view = 'result';
        if (result.courseCompleted) {
          this.message.success('Course completed. Your certificate is ready.');
        }
      },
      error: () => { this.submitting = false; }
    });
  }

  resultOf(q: LearningQuestion): QuestionResult | undefined {
    return this.result?.questions.find(r => r.questionId === q.id);
  }

  difficultyColor(difficulty?: string | null): string {
    switch (difficulty) {
      case 'EASY': return 'green';
      case 'MEDIUM': return 'gold';
      case 'HARD': return 'red';
      default: return 'default';
    }
  }

  typeLabel(type: string): string {
    switch (type) {
      case 'TRUE_FALSE': return 'True or false';
      case 'FILL_IN_THE_BLANK': return 'Fill in the blank';
      default: return 'Multiple choice';
    }
  }

  // --- certificate and feedback --------------------------------------------

  saveFeedback(): void {
    if (!this.feedbackRating) {
      this.message.warning('Choose a star rating first.');
      return;
    }
    this.savingFeedback = true;
    this.learningService.feedback(this.courseId, { rating: this.feedbackRating, comment: this.feedbackComment.trim() })
      .subscribe({
        next: detail => {
          this.savingFeedback = false;
          this.setDetail(detail);
          this.message.success('Thank you for your feedback.');
        },
        error: () => { this.savingFeedback = false; }
      });
  }

  /** Opens the certificate on its own page and prints it (or saves it as a PDF). */
  printCertificate(): void {
    const c = this.detail?.certificate;
    if (!c) {
      return;
    }
    const win = window.open('', '_blank', 'width=1000,height=720');
    if (!win) {
      this.message.warning('Allow pop-ups for this site to print the certificate.');
      return;
    }
    const issued = formatDate(c.completedAt, 'd MMMM yyyy', 'en-US');
    win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(c.certificateNo)}</title>
<style>
  @page { size: A4 landscape; margin: 14mm; }
  body { margin: 0; font-family: 'Open Sans', Arial, sans-serif; color: #172033; }
  .cert { position: relative; box-sizing: border-box; height: 178mm; padding: 18mm 22mm; border: 3px solid #13c2a8; outline: 1px solid #13c2a8; outline-offset: -9px; text-align: center; }
  .brand { color: #e2183d; font-size: 13px; font-weight: 800; letter-spacing: 3px; }
  h1 { margin: 10mm 0 2mm; font-size: 34px; letter-spacing: 1px; }
  .lead { margin: 0; color: #667085; font-size: 14px; }
  .name { margin: 8mm 0 3mm; font-size: 30px; font-weight: 800; }
  .course { margin: 3mm 0 0; font-size: 19px; font-weight: 700; }
  .score { display: inline-block; margin-top: 8mm; padding: 6px 18px; border-radius: 20px; background: #e6fffb; color: #0f766e; font-weight: 800; }
  .meta { position: absolute; left: 22mm; right: 22mm; bottom: 16mm; display: flex; justify-content: space-between; font-size: 12px; color: #667085; }
  .meta strong { display: block; color: #172033; font-size: 13px; }
</style></head><body>
<div class="cert">
  <div class="brand">TBM · APEX TRAINING</div>
  <h1>Certificate of Completion</h1>
  <p class="lead">This certifies that</p>
  <div class="name">${esc(c.learnerName)}</div>
  <p class="lead">has successfully completed the SOP course</p>
  <p class="course">${esc(c.courseTitle)}${c.version ? ' (v' + esc(c.version) + ')' : ''}</p>
  <div class="score">Final score ${c.finalScore ?? '-'}% · pass mark ${c.passMark}%</div>
  <div class="meta">
    <div><strong>${esc(issued)}</strong>Date issued</div>
    <div><strong>TBM Training (APEX)</strong>Issued by</div>
    <div><strong>${esc(c.certificateNo)}</strong>Certificate no.</div>
  </div>
</div>
</body></html>`);
    win.document.close();
    win.focus();
    win.print();
  }

  // --- helpers ----------------------------------------------------------------

  /** A tiled, faint "name · date" watermark over the material. */
  private buildWatermark(name: string): string {
    const label = `${name} · ${formatDate(new Date(), 'd MMM yyyy', 'en-US')}`;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="150">`
      + `<text x="150" y="80" text-anchor="middle" transform="rotate(-28 150 75)" font-family="Open Sans, Arial, sans-serif" `
      + `font-size="12" font-weight="600" fill="rgba(23,32,51,0.07)">${esc(label)}</text></svg>`;
    return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`;
  }

  stateLabel(m: LearningModule): string {
    switch (m.state) {
      case 'PASSED': return `Passed · ${m.passedScore}%`;
      case 'RETRAINING': return `Practice mode · best ${m.bestScore ?? 0}%`;
      case 'LOCKED': return 'Locked: pass the previous module first';
      case 'UNAVAILABLE': return 'Being updated by the trainer';
      default: return `${m.questionCount} quiz question${m.questionCount === 1 ? '' : 's'}`;
    }
  }
}

/** Escapes text for HTML and SVG markup. */
function esc(text: string): string {
  return text.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch] as string));
}
