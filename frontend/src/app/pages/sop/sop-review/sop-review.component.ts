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
import { NzInputNumberModule } from 'ng-zorro-antd/input-number';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { SopService } from '../../../services/sop.service';
import {
  ContentBlockType, ContentSection, MaterialReviewAction, MaterialReviewEvent, ReviewStatus, RichContent, SopDocument, SopModule
} from '../../../models/sop.model';
import { environment } from '../../../../environments/environment';
import { SopContentViewComponent, parseRichContent } from '../../../components/sop-content-view/sop-content-view.component';
import {
  CourseView, MAX_DURATION_MINUTES, MIN_DURATION_MINUTES, courseOptionLabel, courseReady, draftQuestion, formatDuration, startsDraft
} from '../sop.util';
import { CourseArchivedNoteComponent } from '../course-archived-note/course-archived-note.component';
import { CourseVersionNoteComponent } from '../course-version-note/course-version-note.component';

/** How each entry in a module's material history is shown (FR-07-14). */
const HISTORY_META: Record<MaterialReviewAction, { label: string; color: string }> = {
  GENERATED:   { label: 'Generated',       color: 'geekblue' },
  EDITED:      { label: 'Edited',          color: 'blue' },
  APPROVED:    { label: 'Approved',        color: 'green' },
  REJECTED:    { label: 'Rejected',        color: 'red' },
  REGENERATED: { label: 'AI regenerated',  color: 'cyan' },
  COPIED:      { label: 'Copied to draft', color: 'purple' }
};

@Component({
  selector: 'app-sop-review',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule, DragDropModule,
    NzCollapseModule, NzButtonModule, NzInputModule, NzTagModule, NzSelectModule,
    NzIconModule, NzSpinModule, NzDividerModule, NzEmptyModule, NzProgressModule, NzInputNumberModule, SopContentViewComponent,
    NzRadioModule, NzModalModule, NzDrawerModule, CourseArchivedNoteComponent, CourseVersionNoteComponent
  ],
  templateUrl: './sop-review.component.html',
  styleUrls: ['./sop-review.component.scss']
})
export class SopReviewComponent implements OnInit {

  completedSops: SopDocument[] = [];
  /** The picker lists current courses, or archived ones (FR-06-14, FR-06-16). */
  view: CourseView = 'CURRENT';
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

  // course settings (FR-07-15), edited by the course's owner
  readonly formatDuration = formatDuration;
  settingsPassMark = 70;
  settingsHours = 1;
  settingsMinutes = 0;
  savingSettings = false;
  busy: Record<number, boolean> = {};

  // material review history (FR-07-14)
  historyOpen = false;
  historyModule?: SopModule;
  history: MaterialReviewEvent[] = [];
  historyLoading = false;

  readonly optionLabel = courseOptionLabel;
  /** After a change moved to a new draft, the module to open there. */
  private pendingModuleOrder: number | null = null;

  constructor(
    private sopService: SopService,
    private route: ActivatedRoute,
    private router: Router,
    private message: NzMessageService,
    private modal: NzModalService
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
    if (view === this.view) return;
    this.view = view;
    this.document = undefined;
    this.modules = [];
    this.selectedSopId = null;
    this.router.navigate(['/sop/review']);
    this.loadList();
  }

  // --- versions (FR-07-16 to FR-07-18) ---

  /** Every module's material and quiz is approved, so a draft can be published. */
  get ready(): boolean {
    return courseReady(this.modules);
  }

  /** The draft is live now; it stays open, as the course's live version. */
  onPublished(doc: SopDocument): void {
    this.loadList();
    this.loadDetail(doc.id);
  }

  /** The draft is gone: back to the live version. */
  onDiscarded(draft: SopDocument): void {
    this.loadList();
    if (draft.liveVersionId) {
      this.onSelectSop(draft.liveVersionId);
    } else {
      this.document = undefined;
      this.modules = [];
      this.selectedSopId = null;
      this.router.navigate(['/sop/review']);
    }
  }

  /** After a change that started a new draft version, open the draft at the same module. */
  private followDraft(draftId: number, moduleOrder: number, note: string): void {
    this.message.success(note);
    this.pendingModuleOrder = moduleOrder;
    this.selectedSopId = draftId;
    this.loadList();
    this.router.navigate(['/sop/review', draftId]);
    this.loadDetail(draftId);
  }

  /** A restored course is current again, and its owner can change it. */
  onRestored(doc: SopDocument): void {
    this.view = 'CURRENT';
    this.loadList();
    this.loadDetail(doc.id);
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
        this.resetSettings();
        // A link to an archived course opens it under the Archived list, and a restored one under Current.
        const view: CourseView = detail.document.courseStatus === 'ARCHIVED' ? 'ARCHIVED' : 'CURRENT';
        if (view !== this.view) {
          this.view = view;
          this.loadList();
        }
        const order = this.pendingModuleOrder;
        this.pendingModuleOrder = null;
        const byOrder = order == null ? undefined : this.modules.find(m => m.moduleOrder === order);
        if (byOrder) {
          this.selectedModuleId = byOrder.id;
        } else if (!this.selectedModuleId || !this.modules.some(m => m.id === this.selectedModuleId)) {
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

  // --- course settings (FR-07-15) ---

  /** Fills the settings form from the course. */
  private resetSettings(): void {
    const doc = this.document;
    if (!doc) return;
    this.settingsPassMark = doc.passMark;
    this.settingsHours = Math.floor(doc.durationMinutes / 60);
    this.settingsMinutes = doc.durationMinutes % 60;
  }

  get settingsDurationMinutes(): number {
    return (this.settingsHours ?? 0) * 60 + (this.settingsMinutes ?? 0);
  }

  /** Why the settings can't be saved, or null when they can. */
  get settingsError(): string | null {
    const passMark = this.settingsPassMark;
    if (passMark == null || !Number.isInteger(passMark) || passMark < 1 || passMark > 100) {
      return 'The pass mark must be between 1 and 100.';
    }
    const minutes = this.settingsDurationMinutes;
    if (!Number.isFinite(minutes) || minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
      return 'The estimated duration must be between 30 minutes and 24 hours.';
    }
    return null;
  }

  saveSettings(): void {
    const doc = this.document;
    if (!doc?.editable || this.settingsError || this.savingSettings) return;
    this.savingSettings = true;
    this.sopService.updateSettings(doc.id, { passMark: this.settingsPassMark, durationMinutes: this.settingsDurationMinutes })
      .subscribe({
        next: updated => {
          this.savingSettings = false;
          this.document = updated;
          this.resetSettings();
          this.message.success('Course settings saved. They apply to new assignments and enrolments.');
        },
        // The error pop-up gives the server's reason; the form keeps what was typed.
        error: () => { this.savingSettings = false; }
      });
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
    if (startsDraft(this.document)) {
      this.modal.confirm({ ...draftQuestion(this.document!), nzOkText: 'Start draft', nzOnOk: () => this.rejectModuleNow(m, reason) });
      return;
    }
    this.rejectModuleNow(m, reason);
  }

  private rejectModuleNow(m: SopModule, reason: string): void {
    this.busy[m.id] = true;
    this.message.loading('Regenerating module with AI…', { nzDuration: 0 });
    this.sopService.rejectModule(m.id, reason).subscribe({
      next: updated => {
        this.message.remove();
        this.showModuleReject[m.id] = false;
        this.moduleRejectReason[m.id] = '';
        this.busy[m.id] = false;
        if (updated.sopDocumentId !== this.document?.id) {
          // Staff are taking the course, so the regenerated module is in a new draft version (FR-07-13).
          this.followDraft(updated.sopDocumentId, updated.moduleOrder,
            'Module regenerated in the new draft. Staff keep the live version until you publish it.');
          return;
        }
        this.replaceModule(updated);
        this.message.success('Module regenerated.');
      },
      error: err => {
        this.message.remove();
        this.busy[m.id] = false;
        this.message.error(err?.error?.message || 'Regeneration failed.');
      }
    });
  }

  // --- material history (FR-07-14) ---

  openHistory(m: SopModule): void {
    this.historyModule = m;
    this.history = [];
    this.historyOpen = true;
    this.historyLoading = true;
    this.sopService.materialHistory(m.id).subscribe({
      next: events => {
        if (this.historyModule?.id === m.id) {
          this.history = events;
          this.historyLoading = false;
        }
      },
      // The error pop-up gives the server's reason.
      error: () => {
        if (this.historyModule?.id === m.id) {
          this.historyLoading = false;
        }
      }
    });
  }

  closeHistory(): void {
    this.historyOpen = false;
  }

  historyLabel(action: MaterialReviewAction): string { return HISTORY_META[action]?.label ?? action; }
  historyColor(action: MaterialReviewAction): string | undefined { return HISTORY_META[action]?.color; }

  /** Who acted: a person, or the AI for a regeneration. A generation's detail says how it was made. */
  historyBy(e: MaterialReviewEvent): string | null {
    if (e.actorName) return 'by ' + e.actorName;
    return e.action === 'REGENERATED' ? 'by the AI' : null;
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
