import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { SopService } from '../../../services/sop.service';
import { SopDocument } from '../../../models/sop.model';

type VersionState = 'DRAFT' | 'LIVE_WITH_DRAFT' | 'LIVE_WITH_LEARNERS' | 'SUPERSEDED' | 'NONE';

/**
 * Where a course version stands, on the review pages (FR-07-16 to FR-07-18): a draft with Publish and Discard; a live
 * version whose draft is in progress; a live version staff are taking, whose quiz changes start a draft; or a version
 * a newer one replaced. A draft can only be published once every module's material and quiz is approved.
 */
@Component({
  selector: 'app-course-version-note',
  standalone: true,
  imports: [CommonModule, FormsModule, NzButtonModule, NzIconModule, NzModalModule, NzPopconfirmModule, NzRadioModule],
  templateUrl: './course-version-note.component.html',
  styleUrls: ['./course-version-note.component.scss']
})
export class CourseVersionNoteComponent {
  @Input({ required: true }) document!: SopDocument;
  /** Every module's material and quiz is approved, so a draft can be published. */
  @Input() ready = false;
  /** The draft is live now. */
  @Output() published = new EventEmitter<SopDocument>();
  /** The draft was discarded; the live version stays. */
  @Output() discarded = new EventEmitter<SopDocument>();
  /** Open another version of the course: its draft, or its live version. */
  @Output() openVersion = new EventEmitter<number>();

  publishOpen = false;
  redoCompleted = false;
  publishing = false;
  discarding = false;

  constructor(private sopService: SopService, private message: NzMessageService) {}

  get state(): VersionState {
    const d = this.document;
    if (d.courseStatus === 'DRAFT') return 'DRAFT';
    if (d.courseStatus === 'SUPERSEDED') return 'SUPERSEDED';
    if (d.courseStatus !== 'LIVE') return 'NONE';
    if (d.draftId) return 'LIVE_WITH_DRAFT';
    return d.editable && d.enrolledCount > 0 ? 'LIVE_WITH_LEARNERS' : 'NONE';
  }

  openPublish(): void {
    this.redoCompleted = false;
    this.publishOpen = true;
  }

  publish(): void {
    this.publishing = true;
    this.sopService.publish(this.document.id, this.redoCompleted).subscribe({
      next: doc => {
        this.publishing = false;
        this.publishOpen = false;
        this.message.success(`v${doc.versionNo} is live. Everyone enrolled has been notified.`);
        this.published.emit(doc);
      },
      error: () => { this.publishing = false; }
    });
  }

  discard(): void {
    const draft = this.document;
    this.discarding = true;
    this.sopService.delete(draft.id).subscribe({
      next: () => {
        this.discarding = false;
        this.message.success(`Draft v${draft.versionNo} discarded. The live version is unchanged.`);
        this.discarded.emit(draft);
      },
      error: () => { this.discarding = false; }
    });
  }
}
