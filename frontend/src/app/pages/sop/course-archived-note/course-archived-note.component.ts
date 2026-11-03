import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { SopService } from '../../../services/sop.service';
import { SopDocument } from '../../../models/sop.model';
import { RESTORE_QUESTION } from '../sop.util';

/**
 * Says a course is archived (FR-06-14) and offers Restore (FR-06-16), on the review pages. Anyone who can open an
 * archived course there may restore it: its owner, HR or the Super Admin.
 */
@Component({
  selector: 'app-course-archived-note',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzIconModule, NzPopconfirmModule],
  templateUrl: './course-archived-note.component.html',
  styleUrls: ['./course-archived-note.component.scss']
})
export class CourseArchivedNoteComponent {
  @Input({ required: true }) document!: SopDocument;
  /** The course is Live again. */
  @Output() restored = new EventEmitter<SopDocument>();

  readonly restoreQuestion = RESTORE_QUESTION;
  restoring = false;

  constructor(private sopService: SopService, private message: NzMessageService) {}

  restore(): void {
    this.restoring = true;
    this.sopService.restore(this.document.id).subscribe({
      next: doc => {
        this.restoring = false;
        this.message.success(`"${doc.title}" is Live again.`);
        this.restored.emit(doc);
      },
      error: () => { this.restoring = false; }
    });
  }
}
