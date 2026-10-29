import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { Observable } from 'rxjs';
import { CourseCategory, CourseCategoryOptions } from '../../../models/course-category.model';
import { CourseCategoryService } from '../../../services/course-category.service';

/**
 * Course categories (FR-19-01 to FR-19-04), as in the prototype's category manager: make one with a name, colour and
 * icon; rename and recolour; retire a default; delete a custom one. Staff with Trainer access, managers, HR and the Super
 * Admin open it; the server decides who may change which (decision D, 29 Oct 2026).
 */
@Component({
  selector: 'app-course-categories',
  standalone: true,
  imports: [CommonModule, FormsModule, NzButtonModule, NzIconModule, NzInputModule, NzModalModule, NzPopconfirmModule,
    NzSpinModule, NzTagModule],
  templateUrl: './course-categories.component.html',
  styleUrls: ['./course-categories.component.scss']
})
export class CourseCategoriesComponent implements OnInit {
  categories: CourseCategory[] = [];
  options: CourseCategoryOptions = { colors: [], icons: [] };
  loading = true;
  busy = false;

  /** The category being made (null id) or changed; null when the window is closed. */
  editing: { id: number | null; name: string; color: string; icon: string; canRename: boolean } | null = null;

  constructor(private service: CourseCategoryService, private message: NzMessageService) {}

  ngOnInit(): void {
    this.service.options().subscribe({ next: o => (this.options = o) });
    this.load();
  }

  get active(): CourseCategory[] {
    return this.categories.filter(c => !c.retired);
  }

  get retired(): CourseCategory[] {
    return this.categories.filter(c => c.retired);
  }

  openNew(): void {
    this.editing = { id: null, name: '', color: this.options.colors[0] ?? '#475569', icon: 'tag', canRename: true };
  }

  openEdit(c: CourseCategory): void {
    this.editing = { id: c.id, name: c.name, color: c.color, icon: c.icon, canRename: c.canRename };
  }

  save(): void {
    const e = this.editing;
    if (!e || this.busy || !e.name.trim()) return;
    const call = e.id === null
      ? this.service.create(e.name.trim(), e.color, e.icon)
      : this.service.update(e.id, e.name.trim(), e.color, e.icon);
    this.run(call, e.id === null ? `Category "${e.name.trim()}" made.` : 'Category saved.', () => (this.editing = null));
  }

  retire(c: CourseCategory): void {
    this.run(this.service.retire(c.id), `${c.name} retired: no longer offered, kept on its courses.`);
  }

  restore(c: CourseCategory): void {
    this.run(this.service.restore(c.id), `${c.name} is offered again.`);
  }

  remove(c: CourseCategory): void {
    this.run(this.service.delete(c.id), `${c.name} deleted.`);
  }

  deleteQuestion(c: CourseCategory): string {
    return c.courseCount
      ? `Delete ${c.name}? It comes off ${c.courseCount} ${c.courseCount === 1 ? 'course' : 'courses'}; any left with no category move to General.`
      : `Delete ${c.name}?`;
  }

  private load(): void {
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

  private run(call: Observable<unknown>, done: string, after?: () => void): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        after?.();
        this.message.success(done);
        this.load();
      },
      error: err => {
        this.busy = false;
        this.message.error(err?.error?.message || 'That did not work. Reload the page and try again.');
      }
    });
  }
}
