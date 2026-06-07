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
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzMessageService } from 'ng-zorro-antd/message';
import { SopService } from '../../../services/sop.service';
import { ReviewStatus, RichContent, SopDocument, SopModule } from '../../../models/sop.model';

@Component({
  selector: 'app-sop-review',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzCollapseModule, NzButtonModule, NzInputModule, NzTagModule, NzSelectModule,
    NzIconModule, NzSpinModule, NzDividerModule, NzEmptyModule, NzProgressModule
  ],
  templateUrl: './sop-review.component.html',
  styleUrls: ['./sop-review.component.scss']
})
export class SopReviewComponent implements OnInit {

  completedSops: SopDocument[] = [];
  selectedSopId: number | null = null;
  document?: SopDocument;
  modules: SopModule[] = [];
  selectedModuleId: number | null = null;
  loading = false;

  // per-module UI state
  showModuleReject: Record<number, boolean> = {};
  moduleRejectReason: Record<number, string> = {};
  editMode: Record<number, boolean> = {};
  editText: Record<number, string> = {};
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
        if (!this.selectedModuleId || !this.modules.some(m => m.id === this.selectedModuleId)) {
          this.selectedModuleId = this.modules[0]?.id ?? null;
        }
        this.loading = false;
      },
      error: () => { this.loading = false; this.message.error('Could not load SOP detail.'); }
    });
  }

  // --- material review (FR-09) ---

  toggleEdit(m: SopModule): void {
    if (!this.editMode[m.id]) {
      this.editText[m.id] = this.extractPlainText(m);
      this.editMode[m.id] = true;
    } else {
      this.editMode[m.id] = false;
    }
  }

  saveModule(m: SopModule): void {
    if (this.editMode[m.id]) {
      // Wrap edited plain text back into the minimal rich structure.
      const rich: RichContent = {
        summary: '',
        learningObjectives: [],
        tools: [],
        sections: [{ type: 'paragraph', heading: 'Content', body: this.editText[m.id] || '' }],
        keyTerms: []
      };
      m.content = JSON.stringify(rich);
    }
    this.busy[m.id] = true;
    this.sopService.updateModule(m.id, m.title, m.content).subscribe({
      next: updated => {
        this.replaceModule(updated);
        this.editMode[m.id] = false;
        this.busy[m.id] = false;
        this.message.success('Module saved.');
      },
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

  // --- content helpers ---

  parseContent(m: SopModule): RichContent | null {
    try {
      const parsed = JSON.parse(m.content);
      if (parsed && typeof parsed === 'object' && 'sections' in parsed) {
        return parsed as RichContent;
      }
    } catch { /* fall through */ }
    return null;
  }

  private extractPlainText(m: SopModule): string {
    const rich = this.parseContent(m);
    if (!rich) return m.content;
    const parts: string[] = [];
    if (rich.summary) parts.push(rich.summary);
    for (const s of rich.sections ?? []) {
      if (s.heading) parts.push('\n' + s.heading);
      if (s.body) parts.push(s.body);
      if (s.items?.length) parts.push(s.items.map((item, i) => `${i + 1}. ${item}`).join('\n'));
      if (s.rows?.length) parts.push(s.rows.map(r => r.join(' | ')).join('\n'));
    }
    return parts.join('\n\n').trim();
  }

  statusColor(status: ReviewStatus): string {
    switch (status) {
      case 'APPROVED': return 'green';
      case 'REJECTED': return 'red';
      case 'REGENERATED': return 'blue';
      default: return 'orange';
    }
  }

  selectModule(module: SopModule): void {
    this.selectedModuleId = module.id;
    // Exit edit mode when switching modules
    this.editMode[module.id] = false;
  }

  get selectedModule(): SopModule | undefined {
    return this.modules.find(m => m.id === this.selectedModuleId);
  }

  get approvedMaterialCount(): number {
    return this.modules.filter(m => m.reviewStatus === 'APPROVED').length;
  }

  get overallProgress(): number {
    if (!this.modules.length) return 0;
    return Math.round((this.approvedMaterialCount / this.modules.length) * 100);
  }

  private replaceModule(updated: SopModule): void {
    const idx = this.modules.findIndex(x => x.id === updated.id);
    if (idx >= 0) this.modules[idx] = updated;
  }
}
