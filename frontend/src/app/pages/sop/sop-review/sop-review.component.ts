import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzMessageService } from 'ng-zorro-antd/message';
import { SopService } from '../../../services/sop.service';
import { ReviewStatus, SopDocument, SopModule, SopQuizQuestion } from '../../../models/sop.model';

@Component({
  selector: 'app-sop-review',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzCollapseModule, NzButtonModule, NzInputModule, NzTagModule, NzSelectModule,
    NzIconModule, NzSpinModule, NzDividerModule, NzEmptyModule
  ],
  templateUrl: './sop-review.component.html',
  styleUrls: ['./sop-review.component.scss']
})
export class SopReviewComponent implements OnInit {

  completedSops: SopDocument[] = [];
  selectedSopId: number | null = null;
  document?: SopDocument;
  modules: SopModule[] = [];
  loading = false;

  // per-module UI state
  showModuleReject: Record<number, boolean> = {};
  moduleRejectReason: Record<number, string> = {};
  showQuizReject: Record<number, boolean> = {};
  quizRejectReason: Record<number, string> = {};
  busy: Record<number, boolean> = {};

  constructor(
    private sopService: SopService,
    private route: ActivatedRoute,
    private router: Router,
    private message: NzMessageService
  ) {}

  ngOnInit(): void {
    this.sopService.list().subscribe(docs => {
      this.completedSops = docs.filter(d => d.generationStatus === 'COMPLETED');
      const idParam = this.route.snapshot.paramMap.get('id');
      if (idParam) {
        this.selectedSopId = +idParam;
        this.loadDetail(this.selectedSopId);
      }
    });
  }

  onSelectSop(id: number): void {
    this.selectedSopId = id;
    this.router.navigate(['/sop/review', id]);
    this.loadDetail(id);
  }

  loadDetail(id: number): void {
    this.loading = true;
    this.sopService.detail(id).subscribe({
      next: detail => {
        this.document = detail.document;
        this.modules = detail.modules;
        this.loading = false;
      },
      error: () => { this.loading = false; this.message.error('Could not load SOP detail.'); }
    });
  }

  // --- material review (FR-09) ---
  saveModule(m: SopModule): void {
    this.busy[m.id] = true;
    this.sopService.updateModule(m.id, m.title, m.content).subscribe({
      next: updated => { this.replaceModule(updated); this.busy[m.id] = false; this.message.success('Module saved.'); },
      error: () => { this.busy[m.id] = false; this.message.error('Save failed.'); }
    });
  }

  approveModule(m: SopModule): void {
    this.busy[m.id] = true;
    this.sopService.approveModule(m.id).subscribe({
      next: updated => { this.replaceModule(updated); this.busy[m.id] = false; this.message.success('Module approved.'); },
      error: () => { this.busy[m.id] = false; this.message.error('Approve failed.'); }
    });
  }

  confirmRejectModule(m: SopModule): void {
    const reason = (this.moduleRejectReason[m.id] || '').trim();
    if (!reason) { this.message.warning('Please enter a reason to guide regeneration.'); return; }
    this.busy[m.id] = true;
    this.message.loading('Regenerating module with AI…', { nzDuration: 0 });
    this.sopService.rejectModule(m.id, reason).subscribe({
      next: updated => {
        this.message.remove();
        this.replaceModule(updated);
        this.showModuleReject[m.id] = false;
        this.moduleRejectReason[m.id] = '';
        this.busy[m.id] = false;
        this.message.success('Module regenerated.');
      },
      error: err => {
        this.message.remove();
        this.busy[m.id] = false;
        this.message.error(err?.error?.message || 'Regeneration failed.');
      }
    });
  }

  // --- quiz review (FR-10) ---
  saveQuestion(q: SopQuizQuestion): void {
    this.sopService.updateQuestion(q.id, q).subscribe({
      next: () => this.message.success('Question saved.'),
      error: () => this.message.error('Save failed.')
    });
  }

  approveQuiz(m: SopModule): void {
    this.busy[m.id] = true;
    this.sopService.approveQuiz(m.id).subscribe({
      next: () => {
        m.quiz.forEach(q => q.reviewStatus = 'APPROVED');
        this.busy[m.id] = false;
        this.message.success('Quiz approved.');
      },
      error: () => { this.busy[m.id] = false; this.message.error('Approve failed.'); }
    });
  }

  confirmRejectQuiz(m: SopModule): void {
    const reason = (this.quizRejectReason[m.id] || '').trim();
    if (!reason) { this.message.warning('Please enter a reason to guide quiz regeneration.'); return; }
    this.busy[m.id] = true;
    this.message.loading('Regenerating quiz with AI…', { nzDuration: 0 });
    this.sopService.rejectQuiz(m.id, reason).subscribe({
      next: () => {
        this.message.remove();
        this.showQuizReject[m.id] = false;
        this.quizRejectReason[m.id] = '';
        this.busy[m.id] = false;
        this.message.success('Quiz regenerated.');
        if (this.selectedSopId) this.loadDetail(this.selectedSopId); // refresh new questions
      },
      error: err => {
        this.message.remove();
        this.busy[m.id] = false;
        this.message.error(err?.error?.message || 'Quiz regeneration failed.');
      }
    });
  }

  statusColor(status: ReviewStatus): string {
    switch (status) {
      case 'APPROVED': return 'green';
      case 'REJECTED': return 'red';
      case 'REGENERATED': return 'blue';
      default: return 'orange';
    }
  }

  private replaceModule(updated: SopModule): void {
    const idx = this.modules.findIndex(x => x.id === updated.id);
    if (idx >= 0) this.modules[idx] = updated;
  }
}
