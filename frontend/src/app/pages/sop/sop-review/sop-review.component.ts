import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
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
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { SopService } from '../../../services/sop.service';
import {
  ContentBlockType, ContentSection, ReviewStatus, RichContent, SopDocument, SopModule
} from '../../../models/sop.model';
import { environment } from '../../../../environments/environment';
import { SopContentViewComponent, parseRichContent } from '../../../components/sop-content-view/sop-content-view.component';

@Component({
  selector: 'app-sop-review',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, DragDropModule,
    NzCollapseModule, NzButtonModule, NzInputModule, NzTagModule, NzSelectModule,
    NzIconModule, NzSpinModule, NzDividerModule, NzEmptyModule, NzProgressModule, SopContentViewComponent
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
  editRich: Record<number, RichContent> = {};
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
      error: (e: HttpErrorResponse) => this.onLoadFailed(e)
    });
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
      this.message.error('Could not load SOP detail.');
    }
    this.router.navigate(['/sop/review']);
  }

  // --- material review (FR-09) ---

  toggleEdit(m: SopModule): void {
    if (!this.document?.editable) {
      return; // read-only course: only its owner can edit
    }
    if (!this.editMode[m.id]) {
      // Deep-copy the parsed rich content so edits don't mutate the view model directly.
      const rich = this.parseContent(m);
      this.editRich[m.id] = rich
        ? JSON.parse(JSON.stringify(rich))
        : { summary: m.content, learningObjectives: [], tools: [], sections: [{ type: 'paragraph', heading: 'Content', body: m.content }], keyTerms: [] };
      this.editMode[m.id] = true;
    } else {
      this.editMode[m.id] = false;
    }
  }

  saveModule(m: SopModule): void {
    if (this.editMode[m.id] && this.editRich[m.id]) {
      // Blank list entries are dropped, so removing every key term leaves none (FR-07-11).
      const rich = this.editRich[m.id];
      rich.learningObjectives = (rich.learningObjectives ?? []).filter(o => o.trim());
      rich.tools = (rich.tools ?? []).filter(t => t.trim());
      rich.keyTerms = (rich.keyTerms ?? []).filter(k => k.term.trim() || k.definition.trim());
      // Serialize the edited rich object back to JSON — structure is preserved.
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

  // --- block editing (FR-07-06) ---

  /** Insert-toolbar buttons, in the order they appear above the block list. */
  readonly blockTypes: { type: ContentBlockType; label: string; icon: string }[] = [
    { type: 'paragraph', label: 'Paragraph', icon: 'align-left' },
    { type: 'steps',     label: 'Steps',     icon: 'ordered-list' },
    { type: 'table',     label: 'Table',     icon: 'table' },
    { type: 'warnings',  label: 'Warning',   icon: 'warning' },
    { type: 'image',     label: 'Image',     icon: 'picture' },
    { type: 'video',     label: 'Video',     icon: 'video-camera' },
  ];

  /** Per-block upload spinner, keyed "moduleId:blockIndex". */
  uploading: Record<string, boolean> = {};

  addBlock(moduleId: number, type: ContentBlockType): void {
    const rich = this.editRich[moduleId];
    if (!rich) { return; }
    rich.sections = rich.sections || [];
    rich.sections.push(this.blankBlock(type));
  }

  private blankBlock(type: ContentBlockType): ContentSection {
    switch (type) {
      case 'steps':
        return { type, heading: 'Procedure', items: [''] };
      case 'warnings':
        return { type, heading: 'Critical Rules', items: [''] };
      case 'table':
        return { type, heading: 'Reference Table', headers: ['Column 1', 'Column 2'], rows: [['', '']] };
      case 'image':
        return { type, heading: 'Image', url: '', caption: '', alt: '' };
      case 'video':
        return { type, heading: 'Video', url: '', caption: '' };
      default:
        return { type: 'paragraph', heading: 'Section', body: '' };
    }
  }

  removeBlock(moduleId: number, index: number): void {
    this.editRich[moduleId]?.sections.splice(index, 1);
  }

  /** Reorder blocks by drag (FR-07-06). */
  dropBlock(moduleId: number, event: CdkDragDrop<ContentSection[]>): void {
    const sections = this.editRich[moduleId]?.sections;
    if (!sections) { return; }
    moveItemInArray(sections, event.previousIndex, event.currentIndex);
  }

  // the module's own lists: objectives, tools and key terms (FR-07-11)
  addObjective(rich: RichContent): void {
    (rich.learningObjectives ??= []).push('');
  }

  addTool(rich: RichContent): void {
    (rich.tools ??= []).push('');
  }

  addKeyTerm(rich: RichContent): void {
    (rich.keyTerms ??= []).push({ term: '', definition: '' });
  }

  removeAt(list: unknown[] | undefined, index: number): void {
    list?.splice(index, 1);
  }

  // list items within a steps / warnings block
  addItem(block: ContentSection): void {
    block.items = block.items || [];
    block.items.push('');
  }

  removeItem(block: ContentSection, index: number): void {
    block.items?.splice(index, 1);
  }

  // table structure
  addRow(block: ContentSection): void {
    const width = block.headers?.length || 1;
    block.rows = block.rows || [];
    block.rows.push(new Array(width).fill(''));
  }

  removeRow(block: ContentSection, index: number): void {
    block.rows?.splice(index, 1);
  }

  addColumn(block: ContentSection): void {
    block.headers = block.headers || [];
    block.headers.push('Column ' + (block.headers.length + 1));
    (block.rows || []).forEach(row => row.push(''));
  }

  removeColumn(block: ContentSection, index: number): void {
    block.headers?.splice(index, 1);
    (block.rows || []).forEach(row => row.splice(index, 1));
  }

  /** Upload a picked image/video and store the returned URL on the block. */
  onMediaPicked(event: Event, moduleId: number, index: number, block: ContentSection): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) { return; }
    const key = moduleId + ':' + index;
    this.uploading[key] = true;
    this.sopService.uploadMedia(file).subscribe({
      next: media => {
        block.url = media.url;
        if (!block.caption) { block.caption = media.filename; }
        if (block.type === 'image' && !block.alt) { block.alt = media.filename; }
        this.uploading[key] = false;
        // Allow re-picking the same file straight after a replace.
        input.value = '';
        this.message.success('Uploaded.');
      },
      error: err => {
        this.uploading[key] = false;
        input.value = '';
        this.message.error(err?.error?.message || 'Upload failed.');
      }
    });
  }

  /** Resolve a stored media path to a URL the browser can load. */
  mediaSrc(url?: string): string {
    if (!url) { return ''; }
    if (/^https?:\/\//i.test(url)) { return url; }
    // Stored paths are API-relative ("/api/sop/media/..."); apiBaseUrl already ends in "/api".
    return environment.apiBaseUrl.replace(/\/api$/, '') + url;
  }

  // --- content helpers ---

  parseContent(m: SopModule): RichContent | null {
    return parseRichContent(m.content);
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
