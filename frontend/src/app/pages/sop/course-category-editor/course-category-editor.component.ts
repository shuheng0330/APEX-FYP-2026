import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { CourseCategoryChip } from '../../../models/course-category.model';
import { SopDocument } from '../../../models/sop.model';
import { CourseCategoryService } from '../../../services/course-category.service';
import { CourseCategoryPickerComponent } from '../course-category-picker/course-category-picker.component';

/**
 * A course's categories on Review Generated Training (FR-19-05). Every version of the course shares them; its owner
 * changes them, unless the course is archived.
 */
@Component({
  selector: 'app-course-category-editor',
  standalone: true,
  imports: [CommonModule, NzButtonModule, NzIconModule, CourseCategoryPickerComponent],
  templateUrl: './course-category-editor.component.html',
  styleUrls: ['./course-category-editor.component.scss']
})
export class CourseCategoryEditorComponent implements OnChanges {
  @Input({ required: true }) document!: SopDocument;

  editing = false;
  saving = false;
  ids: number[] = [];
  primaryId: number | null = null;

  constructor(private service: CourseCategoryService, private message: NzMessageService) {}

  ngOnChanges(): void {
    this.editing = false; // another course was opened
  }

  get chips(): CourseCategoryChip[] {
    return this.document.categories ?? [];
  }

  get canChange(): boolean {
    return this.document.editable && this.document.courseStatus !== 'ARCHIVED';
  }

  start(): void {
    const stored = this.chips.filter(c => c.id !== null);
    this.ids = stored.map(c => c.id as number);
    this.primaryId = stored.find(c => c.primary)?.id ?? this.ids[0] ?? null;
    this.editing = true;
  }

  save(): void {
    if (!this.ids.length || this.saving) return;
    this.saving = true;
    this.service.setCourse(this.document.id, this.ids, this.primaryId).subscribe({
      next: chips => {
        this.saving = false;
        this.editing = false;
        this.document.categories = chips;
        this.message.success('Categories saved.');
      },
      error: err => {
        this.saving = false;
        this.message.error(err?.error?.message || 'Could not save the categories.');
      }
    });
  }
}
