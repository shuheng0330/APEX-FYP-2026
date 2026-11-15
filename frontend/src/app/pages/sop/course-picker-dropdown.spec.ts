import { Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideNzIconsTesting } from 'ng-zorro-antd/icon/testing';
import { NzSelectComponent } from 'ng-zorro-antd/select';
import { of } from 'rxjs';
import { Scoreboard } from '../../models/sop-scoreboard.model';
import { SopDocument } from '../../models/sop.model';
import { SopScoreboardService } from '../../services/sop-scoreboard.service';
import { SopService } from '../../services/sop.service';
import { SopQuizReviewComponent } from './sop-quiz-review/sop-quiz-review.component';
import { SopReviewComponent } from './sop-review/sop-review.component';
import { SopScoreboardComponent } from './sop-scoreboard/sop-scoreboard.component';
import { COURSE_OPTION_HEIGHT_PX, courseOptionLabel, courseOptionModules, courseOptionVersion } from './sop.util';

/** VR-11: the course pickers' lists show the whole course name, with the version in its own column. */
describe('Course picker lists', () => {
  const LONG = 'SOP Front Office Reception Cash Handling & Financial Transactions';
  const course = (id: number, title: string, versionNo: number, courseStatus: SopDocument['courseStatus'], moduleCount: number) => ({
    id, title, generationStatus: 'COMPLETED', moduleCount, createdAt: '2026-11-12T00:00:00Z', editable: true, passMark: 70,
    durationMinutes: 60, durationSet: true, courseStatus, enrolledCount: 0, activeEnrollmentCount: 0, queuePosition: 0, courseId: id, versionNo
  } as SopDocument);
  const courses = [course(2, LONG, 2, 'DRAFT', 5), course(1, LONG, 1, 'LIVE', 1), course(3, 'sample_sop', 1, 'LIVE', 5)];

  const overlay = () => document.querySelector('.cdk-overlay-container');
  afterEach(() => overlay()?.replaceChildren());

  it('splits the version and module count from the name, and keeps the full label for search and hover', () => {
    expect(courseOptionVersion(courses[0])).toBe('v2 draft');
    expect(courseOptionVersion(courses[1])).toBe('v1');
    expect(courseOptionModules(courses[0])).toBe('5 modules');
    expect(courseOptionModules(courses[1])).toBe('1 module');
    expect(courseOptionLabel(courses[0])).toBe(`${LONG} · v2 draft (5 modules)`);
  });

  /** Opens the page's course picker and returns it with its options. */
  const openPicker = <T>(fixture: ComponentFixture<T>) => {
    fixture.detectChanges(); tick(); fixture.detectChanges();
    const pickers = fixture.debugElement.queryAll(By.directive(NzSelectComponent))
      .filter(d => (d.componentInstance as NzSelectComponent).nzDropdownClassName === 'course-picker-dropdown');
    expect(pickers.length).withContext('one course picker on the page').toBe(1);
    const select = pickers[0].componentInstance as NzSelectComponent;
    (pickers[0].nativeElement as HTMLElement).querySelector<HTMLElement>('.ant-select-selector')!.click();
    tick(); fixture.detectChanges(); tick(); fixture.detectChanges();
    const options = Array.from(document.querySelectorAll<HTMLElement>('.cdk-overlay-container nz-option-item'));
    return { select, options };
  };

  const text = (el: Element, selector: string) => el.querySelector(selector)?.textContent?.trim();

  /** What every course picker's list shares. */
  const expectWideTwoColumnList = (select: NzSelectComponent, options: HTMLElement[]) => {
    expect(select.nzDropdownMatchSelectWidth).toBeFalse();
    expect(select.nzOptionHeightPx).toBe(COURSE_OPTION_HEIGHT_PX);
    const pane = overlay()!.querySelector<HTMLElement>('.cdk-overlay-pane.course-picker-dropdown')!;
    expect(pane.querySelector('.ant-select-dropdown')).withContext('the list is inside the course picker pane').not.toBeNull();
    // 520px wide, or the screen less 16px each side, and never narrower than the box.
    const expectedWidth = Math.max(parseFloat(pane.style.minWidth || '0'), Math.min(520, window.innerWidth - 32));
    expect(pane.getBoundingClientRect().width).toBeCloseTo(expectedWidth, 0);
    const title = options[0].querySelector<HTMLElement>('.course-option-title')!;
    expect(title.textContent!.trim()).toBe(LONG);
    // The global style is loaded: the name wraps onto at most two lines, and the version column never wraps.
    expect(getComputedStyle(options[0].querySelector('.ant-select-item-option-content')!).whiteSpace).toBe('normal');
    expect(getComputedStyle(title).webkitLineClamp).toBe('2');
    expect(getComputedStyle(options[0].querySelector('.course-option-meta')!).whiteSpace).toBe('nowrap');
  };

  describe('on the review pages', () => {
    let sop: jasmine.SpyObj<SopService>;

    const create = <T>(component: Type<T>) => {
      sop = jasmine.createSpyObj<SopService>('SopService', ['list', 'detail', 'materialHistory', 'quizHistory']);
      sop.list.and.returnValue(of(courses));
      sop.detail.and.callFake((id: number) => of({ document: courses.find(c => c.id === id)!, modules: [] }));
      sop.materialHistory.and.returnValue(of([]));
      sop.quizHistory.and.returnValue(of([]));
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]), provideNoopAnimations(), provideNzIconsTesting(), provideHttpClient(), provideHttpClientTesting(),
          { provide: SopService, useValue: sop },
          { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } }
        ]
      });
      spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      return TestBed.createComponent(component);
    };

    for (const [page, component] of [['Generated Material Review', SopReviewComponent], ['Quiz Review', SopQuizReviewComponent]] as const) {
      it(`${page}: shows each whole name with its version and module count, and opening a course still works`, fakeAsync(() => {
        const fixture = create<SopReviewComponent | SopQuizReviewComponent>(component);
        const { select, options } = openPicker(fixture);

        expectWideTwoColumnList(select, options);
        expect(select.nzPlacement).toBe('bottomRight');
        expect(options.map(o => [text(o, '.course-option-title'), text(o, '.course-option-version')])).toEqual([
          [LONG, 'v2 draft'], [LONG, 'v1'], ['sample_sop', 'v1']
        ]);
        expect(text(options[0], '.course-option-meta span:last-child')).toBe('5 modules');
        expect(text(options[1], '.course-option-meta span:last-child')).toBe('1 module');
        expect(options[0].getAttribute('title')).toBe(`${LONG} · v2 draft (5 modules)`);

        options[2].click(); tick(); fixture.detectChanges();
        expect(fixture.componentInstance.selectedSopId).toBe(3);
        expect(sop.detail).toHaveBeenCalledWith(3);
        fixture.destroy();
        discardPeriodicTasks();
      }));
    }
  });

  describe('on the Training Scoreboard', () => {
    const scoreboard: Scoreboard = {
      courses: [
        { id: 2, title: LONG, archived: false, versionNo: 2, superseded: false },
        { id: 1, title: LONG, archived: false, versionNo: 1, superseded: true },
        { id: 4, title: 'Old SOP', archived: true, versionNo: 3, superseded: true }
      ],
      departments: ['Sales Department'], records: [], scope: 'ALL', generatedAt: '2026-11-12T00:00:00Z'
    };

    it('shows each whole name with its version and state, and still filters by course', fakeAsync(() => {
      const service = jasmine.createSpyObj<SopScoreboardService>('SopScoreboardService', ['scoreboard', 'enrollment']);
      service.scoreboard.and.returnValue(of(scoreboard));
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]), provideNoopAnimations(), provideNzIconsTesting(), provideHttpClient(), provideHttpClientTesting(),
          { provide: SopScoreboardService, useValue: service }
        ]
      });
      const fixture = TestBed.createComponent(SopScoreboardComponent);
      const { select, options } = openPicker(fixture);

      expectWideTwoColumnList(select, options);
      expect(options.map(o => [text(o, '.course-option-title'), text(o, '.course-option-version'), text(o, '.course-option-meta span:last-child')]))
        .toEqual([[LONG, 'v2', ''], [LONG, 'v1', 'replaced'], ['Old SOP', 'v3', 'replaced, archived']]);

      options[1].click(); tick(); fixture.detectChanges();
      expect(fixture.componentInstance.courseId).toBe(1);
      fixture.destroy();
      discardPeriodicTasks();
    }));
  });
});
