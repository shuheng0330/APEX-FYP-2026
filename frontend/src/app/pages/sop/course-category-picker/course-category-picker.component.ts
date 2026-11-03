import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { CourseCategory } from '../../../models/course-category.model';
import { CourseCategoryService } from '../../../services/course-category.service';

/** As many as the server takes for one course. */
export const MAX_CATEGORIES_PER_COURSE = 5;

/**
 * Choose a course's categories and its main one (FR-19-05), on Upload SOP and Review Generated Training. Retired categories
 * aren't offered, except ones the course already has, which it keeps (FR-19-03).
 */
@Component({
  selector: 'app-course-category-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, NzIconModule, NzSelectModule],
  templateUrl: './course-category-picker.component.html',
  styleUrls: ['./course-category-picker.component.scss']
})
export class CourseCategoryPickerComponent implements OnInit {
  @Input() ids: number[] = [];
  @Output() idsChange = new EventEmitter<number[]>();
  @Input() primaryId: number | null = null;
  @Output() primaryIdChange = new EventEmitter<number | null>();
  @Input() placeholder = 'Choose categories';

  readonly max = MAX_CATEGORIES_PER_COURSE;
  categories: CourseCategory[] = [];
  loading = true;
  /** The categories the course had when the picker opened, retired ones among them. */
  private had = new Set<number>();

  constructor(private service: CourseCategoryService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.had = new Set(this.ids);
    this.service.list().subscribe({
      next: list => {
        this.categories = list;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        this.message.error(err?.error?.message || 'Could not load the categories.');
      }
    });
  }

  get offered(): CourseCategory[] {
    return this.categories.filter(c => !c.retired || this.had.has(c.id));
  }

  get chosen(): CourseCategory[] {
    return this.ids.map(id => this.categories.find(c => c.id === id)).filter((c): c is CourseCategory => !!c);
  }

  get mainId(): number | null {
    return this.primaryId !== null && this.ids.includes(this.primaryId) ? this.primaryId : this.ids[0] ?? null;
  }

  setIds(ids: number[]): void {
    this.ids = ids;
    this.idsChange.emit(ids);
    if (this.primaryId === null || !ids.includes(this.primaryId)) {
      this.setPrimary(ids[0] ?? null);
    }
  }

  setPrimary(id: number | null): void {
    this.primaryId = id;
    this.primaryIdChange.emit(id);
  }
}
