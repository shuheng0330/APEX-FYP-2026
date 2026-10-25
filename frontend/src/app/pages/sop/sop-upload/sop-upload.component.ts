import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzProgressModule } from 'ng-zorro-antd/progress';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { SopService } from '../../../services/sop.service';
import { SopDocument, SopGenerationStatus } from '../../../models/sop.model';
import { CourseView, MAX_UPLOAD_MB, RESTORE_QUESTION, SOP_FILE_ACCEPT, SOP_FILE_TYPES } from '../sop.util';

@Component({
  selector: 'app-sop-upload',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzButtonModule, NzInputModule, NzFormModule, NzTableModule,
    NzTagModule, NzIconModule, NzSpinModule, NzEmptyModule, NzProgressModule, NzPopconfirmModule, NzAlertModule,
    NzModalModule, NzRadioModule, NzSelectModule
  ],
  templateUrl: './sop-upload.component.html',
  styleUrls: ['./sop-upload.component.scss']
})
export class SopUploadComponent implements OnInit, OnDestroy {

  title = '';
  version = '';
  departmentTag = '';
  /** Upload as the next version of this course rather than as a new one (FR-06-06). */
  newVersionOf: number | null = null;
  selectedFile: File | null = null;
  uploading = false;

  documents: SopDocument[] = [];
  private pollSub?: Subscription;

  /** Your current courses, or the ones you archived (FR-06-14). */
  view: CourseView = 'CURRENT';
  archived: SopDocument[] = [];
  loadingArchived = false;
  busy: Record<number, boolean> = {};
  readonly restoreQuestion = RESTORE_QUESTION;
  readonly fileAccept = SOP_FILE_ACCEPT;
  readonly fileTypes = SOP_FILE_TYPES;
  readonly maxUploadMb = MAX_UPLOAD_MB;

  constructor(
    private sopService: SopService,
    private message: NzMessageService,
    private modal: NzModalService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.startPolling();
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = interval(5000).pipe(
      startWith(0),
      switchMap(() => this.sopService.list(true))
    ).subscribe({
      next: docs => {
        this.documents = docs;
        // Stop polling once nothing is in progress — restarts on next upload.
        if (!docs.some(d => this.isInProgress(d.generationStatus))) {
          this.pollSub?.unsubscribe();
        }
      },
      error: () => {}
    });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files.length ? input.files[0] : null;
    // FR-06-12: refused here as well as on the server, so a large file isn't sent for nothing.
    if (file && file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      this.message.warning(`This file is ${(file.size / (1024 * 1024)).toFixed(1)} MB. SOP files can be up to ${MAX_UPLOAD_MB} MB.`);
      input.value = '';
      this.selectedFile = null;
      return;
    }
    this.selectedFile = file;
  }

  upload(): void {
    if (!this.selectedFile) {
      this.message.warning(`Please choose a ${SOP_FILE_TYPES} first.`);
      return;
    }
    const form = new FormData();
    form.append('file', this.selectedFile);
    if (this.title.trim()) form.append('title', this.title.trim());
    if (this.version.trim()) form.append('version', this.version.trim());
    if (this.departmentTag.trim()) form.append('departmentTag', this.departmentTag.trim());
    if (this.newVersionOf) form.append('newVersionOf', String(this.newVersionOf));

    this.uploading = true;
    this.sopService.upload(form).subscribe({
      next: doc => {
        this.uploading = false;
        this.message.success(doc.courseStatus === 'DRAFT'
          ? `Uploaded as draft v${doc.versionNo} of "${doc.title}" and queued for AI generation. Staff keep the live version until you publish it.`
          : 'Uploaded and queued for AI generation.');
        this.title = ''; this.version = ''; this.departmentTag = ''; this.newVersionOf = null;
        this.selectedFile = null;
        this.documents = [doc, ...this.documents.filter(d => d.id !== doc.id)];
        this.startPolling(); // resume live updates for the new document
      },
      error: err => {
        this.uploading = false;
        this.message.error(err?.error?.message || 'Upload failed.');
      }
    });
  }

  retry(doc: SopDocument): void {
    this.sopService.regenerate(doc.id).subscribe({
      next: () => this.message.info('Regenerating…'),
      error: err => this.message.error(err?.error?.message || 'Could not restart generation.')
    });
  }

  delete(doc: SopDocument): void {
    this.sopService.delete(doc.id).subscribe({
      next: () => {
        this.documents = this.documents.filter(d => d.id !== doc.id);
        this.message.success(doc.courseStatus === 'DRAFT'
          ? `Draft v${doc.versionNo} discarded. The live version is unchanged.` : 'SOP deleted.');
        if (doc.courseStatus === 'DRAFT') {
          this.startPolling(); // the live version no longer has a draft
        }
      },
      error: err => this.message.error(err?.error?.message || 'Delete failed.')
    });
  }

  /** Your live courses that can take a new version: those without a draft in progress (FR-06-06). */
  get versionTargets(): SopDocument[] {
    return this.documents.filter(d => d.courseStatus === 'LIVE' && !d.draftId && d.generationStatus === 'COMPLETED');
  }

  // --- archive and restore (FR-06-14 to FR-06-16) ---

  get shown(): SopDocument[] {
    return this.view === 'ARCHIVED' ? this.archived : this.documents;
  }

  setView(view: CourseView): void {
    this.view = view;
    if (view === 'ARCHIVED') {
      this.loadArchived();
    }
  }

  private loadArchived(): void {
    this.loadingArchived = true;
    this.sopService.list(true, true).subscribe({
      next: docs => { this.archived = docs; this.loadingArchived = false; },
      error: () => { this.loadingArchived = false; }
    });
  }

  /** What archiving does to the course's staff, for the confirmation (FR-06-14, FR-06-15). */
  archiveSummary(doc: SopDocument): string {
    const active = doc.activeEnrollmentCount;
    const others = doc.enrolledCount - active;
    const withdrawn = active
      ? `${active} ${active === 1 ? 'person' : 'people'} still taking it will be withdrawn and notified; their progress is kept. `
      : 'Nobody is part-way through it. ';
    const kept = others ? `Records for the ${others} other${others === 1 ? '' : 's'} enrolled stay, with any certificates. ` : '';
    return withdrawn + kept + 'It disappears from the Learning Portal, Assign Training and the review lists. You can restore it later.';
  }

  archive(doc: SopDocument): void {
    this.modal.confirm({
      nzTitle: `Archive "${doc.title}"?`,
      nzContent: this.archiveSummary(doc),
      nzOkText: 'Archive',
      nzOkDanger: true,
      nzCancelText: 'Cancel',
      nzOnOk: () => this.confirmArchive(doc)
    });
  }

  private confirmArchive(doc: SopDocument): void {
    this.busy[doc.id] = true;
    this.sopService.archive(doc.id).subscribe({
      next: archived => {
        this.busy[doc.id] = false;
        this.documents = this.documents.filter(d => d.id !== doc.id);
        this.archived = [archived, ...this.archived.filter(d => d.id !== doc.id)];
        const withdrawn = doc.activeEnrollmentCount;
        this.message.success(`"${doc.title}" archived.` + (withdrawn
          ? ` ${withdrawn} ${withdrawn === 1 ? 'person was' : 'people were'} withdrawn and notified.` : ''));
      },
      error: () => { this.busy[doc.id] = false; }
    });
  }

  restore(doc: SopDocument): void {
    this.busy[doc.id] = true;
    this.sopService.restore(doc.id).subscribe({
      next: live => {
        this.busy[doc.id] = false;
        this.archived = this.archived.filter(d => d.id !== doc.id);
        this.documents = [live, ...this.documents.filter(d => d.id !== doc.id)];
        this.message.success(`"${live.title}" is Live again.`);
      },
      error: () => { this.busy[doc.id] = false; }
    });
  }

  openReview(doc: SopDocument): void {
    this.router.navigate(['/sop/review', doc.id]);
  }

  statusColor(status: SopGenerationStatus): string {
    switch (status) {
      case 'COMPLETED': return 'green';
      case 'FAILED': return 'red';
      case 'PENDING': return 'default';
      default: return 'processing';
    }
  }

  isInProgress(status: SopGenerationStatus): boolean {
    return status === 'PENDING' || status === 'PARSING' || status === 'GENERATING';
  }

  get totalDocuments(): number {
    return this.documents.length;
  }

  get processingDocuments(): number {
    return this.documents.filter(doc => this.isInProgress(doc.generationStatus)).length;
  }

  get completedDocuments(): number {
    return this.documents.filter(doc => doc.generationStatus === 'COMPLETED').length;
  }

  get failedDocuments(): number {
    return this.documents.filter(doc => doc.generationStatus === 'FAILED').length;
  }

  generationPercent(status: SopGenerationStatus): number {
    switch (status) {
      case 'PENDING': return 15;
      case 'PARSING': return 40;
      case 'GENERATING': return 72;
      case 'COMPLETED': return 100;
      default: return 100;
    }
  }

  /** The document's stage; a waiting one shows its place in the generation queue (FR-06-11). */
  generationLabel(doc: SopDocument): string {
    switch (doc.generationStatus) {
      case 'PENDING': return doc.queuePosition > 0 ? `Queued, position ${doc.queuePosition}` : 'Uploaded';
      case 'PARSING': return 'Parsing';
      case 'GENERATING': return 'Generating';
      case 'COMPLETED': return 'Ready for Review';
      case 'FAILED': return 'Failed';
    }
  }
}
