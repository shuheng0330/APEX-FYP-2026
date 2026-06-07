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
import { NzMessageService } from 'ng-zorro-antd/message';
import { SopService } from '../../../services/sop.service';
import { SopDocument, SopGenerationStatus } from '../../../models/sop.model';

@Component({
  selector: 'app-sop-upload',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzButtonModule, NzInputModule, NzFormModule, NzTableModule,
    NzTagModule, NzIconModule, NzSpinModule, NzEmptyModule, NzProgressModule, NzPopconfirmModule
  ],
  templateUrl: './sop-upload.component.html',
  styleUrls: ['./sop-upload.component.scss']
})
export class SopUploadComponent implements OnInit, OnDestroy {

  title = '';
  version = '';
  departmentTag = '';
  selectedFile: File | null = null;
  uploading = false;

  documents: SopDocument[] = [];
  private pollSub?: Subscription;

  constructor(
    private sopService: SopService,
    private message: NzMessageService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.startPolling();
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = interval(5000).pipe(
      startWith(0),
      switchMap(() => this.sopService.list())
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
    this.selectedFile = input.files && input.files.length ? input.files[0] : null;
  }

  upload(): void {
    if (!this.selectedFile) {
      this.message.warning('Please choose a .doc, .docx, or .pdf file first.');
      return;
    }
    const form = new FormData();
    form.append('file', this.selectedFile);
    if (this.title.trim()) form.append('title', this.title.trim());
    if (this.version.trim()) form.append('version', this.version.trim());
    if (this.departmentTag.trim()) form.append('departmentTag', this.departmentTag.trim());

    this.uploading = true;
    this.sopService.upload(form).subscribe({
      next: doc => {
        this.uploading = false;
        this.message.success('Uploaded. AI generation has started.');
        this.title = ''; this.version = ''; this.departmentTag = '';
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
        this.message.success('SOP deleted.');
      },
      error: err => this.message.error(err?.error?.message || 'Delete failed.')
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

  generationLabel(status: SopGenerationStatus): string {
    switch (status) {
      case 'PENDING': return 'Uploaded';
      case 'PARSING': return 'Parsing';
      case 'GENERATING': return 'Generating';
      case 'COMPLETED': return 'Ready for Review';
      case 'FAILED': return 'Failed';
    }
  }
}
