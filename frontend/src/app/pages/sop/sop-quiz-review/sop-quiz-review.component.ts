import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzMessageService } from 'ng-zorro-antd/message';
import { SopService } from '../../../services/sop.service';
import { CourseView } from '../sop.util';
import { CourseArchivedNoteComponent } from '../course-archived-note/course-archived-note.component';
import {
  QuizDifficulty, QuizQuestionType, QuizReviewAction, QuizReviewEvent, SopDocument, SopModule, SopQuizQuestion
} from '../../../models/sop.model';

/** Review state of a module's quiz, derived from the status of its questions. */
type QuizState = 'NONE' | 'PENDING' | 'REGENERATED' | 'REJECTED' | 'APPROVED';

/** A question being edited; the correct answer is held as an option index so option text can change. */
interface QuestionDraft {
  questionType: QuizQuestionType;
  questionText: string;
  options: string[];
  correctIndex: number;
  answerText: string;
  explanation: string;
  difficulty: QuizDifficulty | null;
}

const STATE_META: Record<QuizState, { label: string; color: string }> = {
  NONE:        { label: 'No quiz',     color: 'default' },
  PENDING:     { label: 'Pending',     color: 'orange' },
  REGENERATED: { label: 'Regenerated', color: 'cyan' },
  REJECTED:    { label: 'Rejected',    color: 'red' },
  APPROVED:    { label: 'Approved',    color: 'green' }
};

const ACTION_META: Record<QuizReviewAction, { label: string; color: string }> = {
  APPROVED:    { label: 'Approved',       color: 'green' },
  REJECTED:    { label: 'Rejected',       color: 'red' },
  REGENERATED: { label: 'AI regenerated', color: 'cyan' },
  EDITED:      { label: 'Edited',         color: 'blue' }
};

/**
 * Trainer quiz review gate (FR-08): pick a course, review each module's quiz, then
 * approve it, edit a question, or reject it with a reason so the AI rewrites it.
 * Staff only ever see approved quizzes (FR-08-08).
 */
@Component({
  selector: 'app-sop-quiz-review',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzButtonModule, NzInputModule, NzTagModule, NzSelectModule, NzRadioModule,
    NzIconModule, NzSpinModule, NzEmptyModule, CourseArchivedNoteComponent
  ],
  templateUrl: './sop-quiz-review.component.html',
  styleUrls: ['./sop-quiz-review.component.scss']
})
export class SopQuizReviewComponent implements OnInit {

  completedSops: SopDocument[] = [];
  /** The picker lists current courses, or archived ones (FR-06-14, FR-06-16). */
  view: CourseView = 'CURRENT';
  selectedSopId: number | null = null;
  document?: SopDocument;
  modules: SopModule[] = [];
  selectedModuleId: number | null = null;
  history: QuizReviewEvent[] = [];
  loading = false;
  historyLoading = false;

  showReject = false;
  rejectReason = '';
  busy = false;
  /** A rejected quiz is being rewritten by the AI; the request can take a minute. */
  regenerating = false;

  /** The question being edited, or null; {@link adding} is set instead for a new question. */
  editingId: number | null = null;
  adding = false;
  draft: QuestionDraft | null = null;
  saving = false;

  readonly difficulties: { value: QuizDifficulty; label: string }[] = [
    { value: 'EASY', label: 'Easy' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'HARD', label: 'Hard' }
  ];
  readonly types: { value: QuizQuestionType; label: string }[] = [
    { value: 'MULTIPLE_CHOICE', label: 'Multiple choice' },
    { value: 'TRUE_FALSE', label: 'True / False' },
    { value: 'FILL_IN_THE_BLANK', label: 'Fill in the blank' }
  ];

  constructor(
    private sopService: SopService,
    private route: ActivatedRoute,
    private router: Router,
    private message: NzMessageService
  ) {}

  ngOnInit(): void {
    this.loadList();
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.selectedSopId = +idParam;
      this.loadDetail(this.selectedSopId);
    }
  }

  private loadList(): void {
    this.sopService.list(false, this.view === 'ARCHIVED').subscribe(docs => {
      this.completedSops = docs.filter(d => d.generationStatus === 'COMPLETED');
    });
  }

  setView(view: CourseView): void {
    if (view === this.view || this.regenerating) return;
    this.view = view;
    this.document = undefined;
    this.modules = [];
    this.selectedSopId = null;
    this.selectedModuleId = null;
    this.router.navigate(['/sop/quiz-review']);
    this.loadList();
  }

  /** A restored course is current again, and its owner can change it. */
  onRestored(doc: SopDocument): void {
    this.view = 'CURRENT';
    this.loadList();
    this.loadDetail(doc.id);
  }

  /**
   * A course you can't open (another trainer's, or deleted): the error pop-up already gives the server's reason,
   * so clear the page and go back to the course list.
   */
  private onLoadFailed(e: HttpErrorResponse): void {
    this.loading = false;
    this.document = undefined;
    this.modules = [];
    this.selectedSopId = null;
    if (!e.error?.message) {
      this.message.error('Could not load the SOP.');
    }
    this.router.navigate(['/sop/quiz-review']);
  }

  onSelectSop(id: number): void {
    this.selectedSopId = id;
    this.selectedModuleId = null;
    this.router.navigate(['/sop/quiz-review', id]);
    this.loadDetail(id);
  }

  loadDetail(id: number): void {
    this.loading = true;
    this.sopService.detail(id).subscribe({
      next: detail => {
        this.document = detail.document;
        this.modules = detail.modules;
        this.loading = false;
        // A link to an archived course opens it under the Archived list, and a restored one under Current.
        const view: CourseView = detail.document.courseStatus === 'ARCHIVED' ? 'ARCHIVED' : 'CURRENT';
        if (view !== this.view) {
          this.view = view;
          this.loadList();
        }
        const keep = this.modules.find(m => m.id === this.selectedModuleId);
        const first = keep ?? this.modules[0];
        if (first) {
          this.selectModule(first);
        }
      },
      error: (e: HttpErrorResponse) => this.onLoadFailed(e)
    });
  }

  selectModule(module: SopModule): void {
    if (this.regenerating) {
      return;
    }
    this.selectedModuleId = module.id;
    this.showReject = false;
    this.rejectReason = '';
    this.cancelEdit();
    this.loadHistory(module.id);
  }

  get selectedModule(): SopModule | undefined {
    return this.modules.find(m => m.id === this.selectedModuleId);
  }

  private loadHistory(moduleId: number): void {
    this.historyLoading = true;
    this.sopService.quizHistory(moduleId).subscribe({
      next: events => {
        if (moduleId === this.selectedModuleId) {
          this.history = events;
        }
        this.historyLoading = false;
      },
      error: () => { this.history = []; this.historyLoading = false; }
    });
  }

  // --- status ---------------------------------------------------------------

  quizState(module: SopModule): QuizState {
    const statuses = module.quiz.map(q => q.reviewStatus);
    if (!statuses.length) return 'NONE';
    if (statuses.every(s => s === 'APPROVED')) return 'APPROVED';
    if (statuses.includes('REGENERATED')) return 'REGENERATED';
    if (statuses.includes('REJECTED')) return 'REJECTED';
    return 'PENDING';
  }

  stateLabel(state: QuizState): string { return STATE_META[state].label; }
  stateColor(state: QuizState): string { return STATE_META[state].color; }
  actionLabel(action: QuizReviewAction): string { return ACTION_META[action].label; }
  actionColor(action: QuizReviewAction): string { return ACTION_META[action].color; }

  /** Quizzes still waiting for a decision in this course. */
  get awaitingCount(): number {
    return this.modules.filter(m => ['PENDING', 'REGENERATED'].includes(this.quizState(m))).length;
  }

  /** Version 1 is the first generation; each AI regeneration adds one. */
  get version(): number {
    return 1 + this.history.filter(e => e.action === 'REGENERATED').length;
  }

  // --- review actions ---------------------------------------------------------

  approve(): void {
    const module = this.selectedModule;
    if (!module) return;
    this.busy = true;
    this.sopService.approveQuiz(module.id).subscribe({
      next: updated => {
        this.replaceModule(updated);
        this.busy = false;
        this.showReject = false;
        this.message.success('Quiz approved. Staff can now take it.');
        this.loadHistory(updated.id);
      },
      error: err => { this.busy = false; this.message.error(err?.error?.message || 'Approve failed.'); }
    });
  }

  submitReject(): void {
    const module = this.selectedModule;
    const reason = this.rejectReason.trim();
    if (!module || !reason) return;
    this.busy = true;
    this.regenerating = true;
    this.cancelEdit();
    this.sopService.rejectQuiz(module.id, reason).subscribe({
      next: updated => {
        this.replaceModule(updated);
        this.busy = false;
        this.regenerating = false;
        this.showReject = false;
        this.rejectReason = '';
        // FR-08-04: tell the trainer the regenerated quiz is ready.
        this.message.success('The AI has rewritten the quiz. The new version is ready for review.');
        this.loadHistory(updated.id);
      },
      error: err => {
        this.busy = false;
        this.regenerating = false;
        this.message.error(err?.error?.message || 'The quiz could not be regenerated.');
      }
    });
  }

  private replaceModule(updated: SopModule): void {
    this.modules = this.modules.map(m => (m.id === updated.id ? updated : m));
  }

  // --- editing ----------------------------------------------------------------

  /** True while a question is being edited or added (one at a time). */
  get drafting(): boolean {
    return this.editingId !== null || this.adding;
  }

  startAdd(): void {
    this.cancelEdit();
    this.adding = true;
    this.draft = {
      questionType: 'MULTIPLE_CHOICE',
      questionText: '',
      options: ['', '', '', ''],
      correctIndex: 0,
      answerText: '',
      explanation: '',
      difficulty: null
    };
  }

  startEdit(q: SopQuizQuestion): void {
    const options = q.questionType === 'FILL_IN_THE_BLANK' ? [] : [...q.options];
    this.adding = false;
    this.editingId = q.id;
    this.draft = {
      questionType: q.questionType,
      questionText: q.questionText,
      options,
      correctIndex: Math.max(0, options.indexOf(q.correctAnswer)),
      answerText: q.questionType === 'FILL_IN_THE_BLANK' ? q.correctAnswer : '',
      explanation: q.explanation ?? '',
      difficulty: q.difficulty ?? null
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.adding = false;
    this.draft = null;
  }

  onTypeChange(type: QuizQuestionType): void {
    if (!this.draft) return;
    if (type === 'TRUE_FALSE') {
      this.draft.options = ['True', 'False'];
      this.draft.correctIndex = 0;
    } else if (type === 'MULTIPLE_CHOICE' && this.draft.options.length < 2) {
      this.draft.options = ['', '', '', ''];
      this.draft.correctIndex = 0;
    } else if (type === 'FILL_IN_THE_BLANK') {
      this.draft.answerText = this.draft.options[this.draft.correctIndex] ?? '';
      this.draft.options = [];
    }
  }

  addOption(): void {
    this.draft?.options.push('');
  }

  removeOption(index: number): void {
    if (!this.draft || this.draft.options.length <= 2) return;
    this.draft.options.splice(index, 1);
    if (this.draft.correctIndex >= this.draft.options.length) {
      this.draft.correctIndex = this.draft.options.length - 1;
    } else if (this.draft.correctIndex > index) {
      this.draft.correctIndex--;
    }
  }

  get canSave(): boolean {
    const d = this.draft;
    if (!d || !d.questionText.trim()) return false;
    if (d.questionType === 'FILL_IN_THE_BLANK') return !!d.answerText.trim();
    const texts = d.options.map(o => o.trim());
    return texts.length >= 2 && texts.every(Boolean) && new Set(texts).size === texts.length
      && d.correctIndex >= 0 && d.correctIndex < texts.length;
  }

  /** Saves the draft: a new question when adding, otherwise the question being edited. */
  saveDraft(): void {
    const d = this.draft;
    const module = this.selectedModule;
    if (!d || !module || !this.canSave) return;
    const fill = d.questionType === 'FILL_IN_THE_BLANK';
    const options = fill ? [] : d.options.map(o => o.trim());
    const dto: Partial<SopQuizQuestion> = {
      questionType: d.questionType,
      questionText: d.questionText.trim(),
      options,
      correctAnswer: fill ? d.answerText.trim() : options[d.correctIndex],
      explanation: d.explanation.trim(),
      difficulty: d.difficulty
    };
    const wasApproved = this.quizState(module) === 'APPROVED';
    const request = this.adding
      ? this.sopService.addQuestion(module.id, dto)
      : this.sopService.updateQuestion(this.editingId!, dto);
    const added = this.adding;
    this.saving = true;
    request.subscribe({
      next: saved => {
        this.modules = this.modules.map(m => m.id !== saved.sopModuleId ? m : {
          ...m,
          quiz: added ? [...m.quiz, saved] : m.quiz.map(x => (x.id === saved.id ? saved : x))
        });
        this.saving = false;
        this.cancelEdit();
        this.message.success(added && wasApproved
          ? 'Question added. Approve the quiz again so staff can take it.'
          : added ? 'Question added.' : 'Question saved.');
        this.loadHistory(saved.sopModuleId);
      },
      error: err => { this.saving = false; this.message.error(err?.error?.message || 'Save failed.'); }
    });
  }

  // --- display helpers ----------------------------------------------------------

  letter(index: number): string {
    return String.fromCharCode(65 + index);
  }

  typeLabel(type: QuizQuestionType): string {
    return this.types.find(t => t.value === type)?.label ?? type;
  }

  difficultyLabel(difficulty: QuizDifficulty): string {
    return this.difficulties.find(d => d.value === difficulty)?.label ?? difficulty;
  }

  difficultyColor(difficulty: QuizDifficulty): string {
    return difficulty === 'EASY' ? 'green' : difficulty === 'MEDIUM' ? 'gold' : 'red';
  }

  /** A choice question whose answer is not one of its options cannot be marked automatically. */
  answerMissing(q: SopQuizQuestion): boolean {
    return q.questionType !== 'FILL_IN_THE_BLANK' && q.options.length > 0 && !q.options.includes(q.correctAnswer);
  }
}
