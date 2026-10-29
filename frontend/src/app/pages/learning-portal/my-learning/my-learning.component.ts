import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { SopLearningService } from '../../../services/sop-learning.service';
import { LearningCourse, LearningHome } from '../../../models/sop-learning.model';
import { categoryTheme, daysUntil, CategoryTheme } from '../learning-portal.util';
import { UpcomingSessionsComponent } from '../../training/upcoming-sessions/upcoming-sessions.component';

type Source = 'all' | 'assigned' | 'open';
type StatusFilter = 'All' | 'Mandatory' | 'Optional' | 'In Progress' | 'Completed';
type StatusGroup = 'Mandatory' | 'In Progress' | 'Optional' | 'Completed' | 'Withdrawn';

interface CourseGroup {
  key: string;
  label: string;
  note: string;
  tone: string;
  courses: LearningCourse[];
}

const STATUS_GROUPS: { key: StatusGroup; note: string; tone: string }[] = [
  { key: 'Mandatory', note: 'Required, not started yet', tone: 'danger' },
  { key: 'In Progress', note: 'Pick up where you left off', tone: 'warning' },
  { key: 'Optional', note: 'Take these any time', tone: 'muted' },
  { key: 'Completed', note: 'Finished and recorded', tone: 'positive' },
  { key: 'Withdrawn', note: 'Archived, or replaced by a new version; your progress is kept', tone: 'muted' },
];

/** A course the learner is still expected to finish: not completed, and not withdrawn (FR-06-15). */
function isActive(c: LearningCourse): boolean {
  return c.status === 'NOT_STARTED' || c.status === 'IN_PROGRESS';
}

/** Cards shown per group before "See all". */
const PREVIEW = 4;

/**
 * The staff Learning Portal home (FR-11): a summary of the learner's training (FR-11-07),
 * courses to start now with their due dates (FR-11-08), their courses filtered by status and
 * category and grouped by either (FR-11-09), and Open courses they can enrol in (FR-11-05).
 */
@Component({
  selector: 'app-my-learning',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    NzButtonModule, NzEmptyModule, NzIconModule, NzInputModule, NzSpinModule, UpcomingSessionsComponent
  ],
  templateUrl: './my-learning.component.html',
  styleUrls: ['./my-learning.component.scss']
})
export class MyLearningComponent implements OnInit {
  readonly statusFilters: StatusFilter[] = ['All', 'Mandatory', 'Optional', 'In Progress', 'Completed'];
  readonly preview = PREVIEW;

  home: LearningHome = { courses: [], openCourses: [] };
  loading = true;
  enrolling: Record<number, boolean> = {};

  search = '';
  source: Source = 'all';
  statusFilter: StatusFilter = 'All';
  groupBy: 'status' | 'category' = 'status';
  activeCategories: string[] = [];
  /** Each category's own colour, from the courses shown. */
  private categoryColors = new Map<string, string>();
  expanded: Record<string, boolean> = {};

  // Derived view, rebuilt whenever the data or a filter changes.
  tiles: { label: string; value: number; tone: string }[] = [];
  assignedCount = 0;
  categories: string[] = [];
  startNow: LearningCourse[] = [];
  groups: CourseGroup[] = [];
  openCourses: LearningCourse[] = [];
  shownCount = 0;

  constructor(
    private learningService: SopLearningService,
    private router: Router,
    private message: NzMessageService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.learningService.home().subscribe({
      next: home => {
        this.home = home;
        this.loading = false;
        this.refresh();
      },
      error: () => { this.loading = false; }
    });
  }

  // --- filters --------------------------------------------------------------

  setSource(source: Source): void {
    this.source = source;
    this.refresh();
  }

  setStatus(filter: StatusFilter): void {
    this.statusFilter = filter;
    this.refresh();
  }

  setGroupBy(groupBy: 'status' | 'category'): void {
    this.groupBy = groupBy;
    this.refresh();
  }

  toggleCategory(category: string): void {
    this.activeCategories = this.activeCategories.includes(category)
      ? this.activeCategories.filter(c => c !== category)
      : [...this.activeCategories, category];
    this.refresh();
  }

  clearCategories(): void {
    this.activeCategories = [];
    this.refresh();
  }

  clearSearch(): void {
    this.search = '';
    this.refresh();
  }

  refresh(): void {
    const courses = this.home.courses;
    this.tiles = [
      { label: 'Start now', value: this.startNowOf(courses).length, tone: 'danger' },
      { label: 'Mandatory pending', value: courses.filter(c => c.mandatory && isActive(c)).length, tone: 'warning' },
      { label: 'In progress', value: courses.filter(c => c.status === 'IN_PROGRESS').length, tone: 'info' },
      { label: 'Completed', value: courses.filter(c => c.status === 'COMPLETED').length, tone: 'positive' },
      { label: 'Open courses', value: this.home.openCourses.length, tone: 'primary' },
    ];
    this.assignedCount = courses.filter(c => !c.selfEnrolled).length;
    // FR-19-06: only categories that some course here carries; colours as each category's own (FR-19-01).
    const all = [...courses, ...this.home.openCourses];
    this.categoryColors = new Map(all.flatMap(c => c.categories ?? []).map(cat => [cat.name, cat.color]));
    this.categories = [...new Set(all.flatMap(c => this.categoriesOf(c)))].sort((a, b) => a.localeCompare(b));
    this.activeCategories = this.activeCategories.filter(c => this.categories.includes(c));

    const mine = courses
      .filter(c => this.source !== 'assigned' || !c.selfEnrolled)
      .filter(c => this.matchesSearch(c) && this.matchesCategory(c) && this.matchesStatus(c));
    this.shownCount = mine.length;
    this.startNow = this.startNowOf(courses.filter(c => this.matchesSearch(c) && this.matchesCategory(c)));
    this.groups = this.groupBy === 'status'
      ? STATUS_GROUPS.map(g => ({
          key: g.key, label: g.key, note: g.note, tone: g.tone,
          courses: mine.filter(c => this.statusGroup(c) === g.key)
        }))
      : this.categories.map(cat => {
          const inCat = mine.filter(c => c.category === cat); // under its main category only, so no course shows twice
          return {
            key: cat, label: cat, tone: 'category',
            note: `${inCat.filter(c => c.mandatory).length} mandatory · ${inCat.filter(c => c.status === 'COMPLETED').length} completed`,
            courses: inCat
          };
        });
    this.groups = this.groups.filter(g => g.courses.length);
    this.openCourses = this.home.openCourses.filter(c => this.matchesSearch(c) && this.matchesCategory(c));
  }

  /** Courses assigned to start immediately and not started yet (FR-11-08), soonest due first; once started, In Progress. */
  private startNowOf(courses: LearningCourse[]): LearningCourse[] {
    return courses
      .filter(c => c.mode === 'IMMEDIATE' && c.status === 'NOT_STARTED' && c.available)
      .sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999'));
  }

  private matchesSearch(c: LearningCourse): boolean {
    const q = this.search.trim().toLowerCase();
    return !q || c.title.toLowerCase().includes(q) || c.category.toLowerCase().includes(q);
  }

  private matchesCategory(c: LearningCourse): boolean {
    return !this.activeCategories.length || this.categoriesOf(c).some(cat => this.activeCategories.includes(cat));
  }

  private categoriesOf(c: LearningCourse): string[] {
    return c.categories?.length ? c.categories.map(cat => cat.name) : [c.category];
  }

  private matchesStatus(c: LearningCourse): boolean {
    switch (this.statusFilter) {
      case 'Mandatory': return c.mandatory;
      case 'Optional': return !c.mandatory;
      case 'In Progress': return c.status === 'IN_PROGRESS';
      case 'Completed': return c.status === 'COMPLETED';
      default: return true;
    }
  }

  statusGroup(c: LearningCourse): StatusGroup {
    if (c.status === 'WITHDRAWN') { return 'Withdrawn'; }
    if (c.status === 'COMPLETED') { return 'Completed'; }
    if (c.status === 'IN_PROGRESS') { return 'In Progress'; }
    return c.mandatory ? 'Mandatory' : 'Optional';
  }

  toggleGroup(key: string): void {
    this.expanded[key] = !this.expanded[key];
  }

  // --- course actions -------------------------------------------------------

  open(course: LearningCourse): void {
    this.router.navigate(['/my-learning/course', course.sopDocumentId]);
  }

  enrol(course: LearningCourse, event?: Event): void {
    event?.stopPropagation();
    this.enrolling[course.sopDocumentId] = true;
    this.learningService.enrol(course.sopDocumentId).subscribe({
      next: () => {
        this.enrolling[course.sopDocumentId] = false;
        this.message.success(`Enrolled in "${course.title}".`);
        this.open(course);
      },
      error: () => { this.enrolling[course.sopDocumentId] = false; }
    });
  }

  // --- display helpers ------------------------------------------------------

  theme(category: string): CategoryTheme {
    return categoryTheme(category, this.categoryColors.get(category));
  }

  progress(c: LearningCourse): number {
    return c.moduleCount ? Math.round(100 * c.modulesPassed / c.moduleCount) : 0;
  }

  daysLeft(c: LearningCourse): number | null {
    return c.dueDate ? daysUntil(c.dueDate) : null;
  }

  /** Ring geometry for a progress percentage. */
  ringDash(percent: number): string {
    const circumference = 2 * Math.PI * 12;
    return `${(percent / 100) * circumference} ${circumference}`;
  }
}
