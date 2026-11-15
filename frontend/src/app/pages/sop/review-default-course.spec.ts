import { Location } from '@angular/common';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RouteReuseStrategy, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideNzIconsTesting } from 'ng-zorro-antd/icon/testing';
import { Observable, Subject, mergeMap, of, throwError, timer } from 'rxjs';
import { SopDocument } from '../../models/sop.model';
import { NotificationRouteReuseStrategy } from '../../services/notification-navigation';
import { SopService } from '../../services/sop.service';
import { SopQuizReviewComponent } from './sop-quiz-review/sop-quiz-review.component';
import { SopReviewComponent } from './sop-review/sop-review.component';
import { courseViewFrom, courseViewQuery } from './sop.util';

/**
 * The review pages open the newest course instead of an empty page, and the Current / Archived switch works
 * with a course open. The real router runs both addresses (/sop/review and /sop/review/5), so leaving a
 * course's address builds the page afresh, as it does in the app.
 */
describe('Review pages open the newest course', () => {
  const course = (id: number, courseStatus: SopDocument['courseStatus'], liveVersionId: number | null = null) => ({
    id, title: `Course ${id}`, generationStatus: 'COMPLETED', moduleCount: 1, createdAt: '2026-11-12T00:00:00Z', editable: true,
    passMark: 70, durationMinutes: 60, durationSet: true, courseStatus, enrolledCount: 0, activeEnrollmentCount: 0, queuePosition: 0,
    courseId: id, versionNo: 1, liveVersionId
  } as SopDocument);
  // The server lists newest first.
  const current = [course(3, 'LIVE'), course(2, 'DRAFT'), course(1, 'LIVE')];
  const archived = [course(5, 'ARCHIVED'), course(4, 'ARCHIVED')];
  const all = [...current, ...archived, course(9, 'LIVE')];

  it('keeps the Archived choice in the address as ?view=archived', () => {
    expect(courseViewQuery('ARCHIVED')).toEqual({ view: 'archived' });
    expect(courseViewQuery('CURRENT')).toEqual({});
    expect(courseViewFrom(undefined)).toBe('CURRENT');
  });

  type Page = SopReviewComponent | SopQuizReviewComponent;
  const pages: [string, string, Type<Page>][] = [
    ['Generated Material Review', '/sop/review', SopReviewComponent],
    ['Quiz Review', '/sop/quiz-review', SopQuizReviewComponent]
  ];

  for (const [name, base, component] of pages) {
    describe(name, () => {
      let sop: jasmine.SpyObj<SopService>;
      let harness: RouterTestingHarness;
      /** Courses that fail to open, as one deleted a moment ago would. */
      let failing: number[];

      /** Answers a moment later, as the server does. */
      const later = <T>(answer: () => Observable<T>) => timer(0).pipe(mergeMap(answer));

      beforeEach(async () => {
        failing = [];
        sop = jasmine.createSpyObj<SopService>('SopService', ['list', 'detail', 'materialHistory', 'quizHistory']);
        sop.list.and.callFake((_mine?: boolean, archivedOnly?: boolean) => later(() => of(archivedOnly ? archived : current)));
        sop.detail.and.callFake((id: number) => later(() => failing.includes(id)
          ? throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'Course not found.' } }))
          : of({ document: all.find(c => c.id === id)!, modules: [] })));
        sop.materialHistory.and.returnValue(of([]));
        sop.quizHistory.and.returnValue(of([]));
        TestBed.configureTestingModule({
          providers: [
            provideRouter([
              { path: 'sop/review', component: SopReviewComponent }, { path: 'sop/review/:id', component: SopReviewComponent },
              { path: 'sop/quiz-review', component: SopQuizReviewComponent }, { path: 'sop/quiz-review/:id', component: SopQuizReviewComponent }
            ]),
            { provide: RouteReuseStrategy, useClass: NotificationRouteReuseStrategy },
            provideNoopAnimations(), provideNzIconsTesting(), provideHttpClient(), provideHttpClientTesting(),
            { provide: SopService, useValue: sop }
          ]
        });
        harness = await RouterTestingHarness.create();
      });

      const open = async (url: string) => {
        await harness.navigateByUrl(url);
        return settle();
      };
      /** Lets the navigation and the server's answers (list, then course) finish, then returns the page now showing. */
      const settle = async () => {
        for (let i = 0; i < 6; i++) {
          await harness.fixture.whenStable();
          await new Promise(resolve => setTimeout(resolve, 5));
          harness.detectChanges();
        }
        return harness.routeDebugElement!.componentInstance as Page;
      };
      const address = () => TestBed.inject(Location).path();
      const emptyPage = () => harness.routeNativeElement!.querySelector('.empty-workbench');
      const opened = () => sop.detail.calls.allArgs().map(a => a[0]);

      it('opens the newest course when the address names none, and leaves the address alone', async () => {
        const page = await open(base);
        expect(page.selectedSopId).toBe(3);
        expect(page.document?.id).toBe(3);
        expect(opened()).toEqual([3]);
        expect(address()).toBe(base);
        expect(emptyPage()).toBeNull();
      });

      it('opens the course a link names, not the newest', async () => {
        const page = await open(`${base}/1`);
        expect(page.document?.id).toBe(1);
        expect(opened()).toEqual([1]);
      });

      it('shows a spinner, not the empty page, while the course list loads', async () => {
        const list = new Subject<SopDocument[]>();
        sop.list.and.returnValue(list as Observable<SopDocument[]>);
        const page = await open(base);
        expect(page.openingNewest).toBeTrue();
        expect(emptyPage()).toBeNull();
        expect(harness.routeNativeElement!.querySelector('.ant-spin-spinning, .ant-spin-blur')).not.toBeNull();
        list.next(current);
        expect(await settle()).toBe(page);
        expect(page.document?.id).toBe(3);
      });

      it('shows the empty page only when there are no courses', async () => {
        sop.list.and.returnValue(of([]));
        const page = await open(base);
        expect(page.openingNewest).toBeFalse();
        expect(opened()).toEqual([]);
        expect(emptyPage()).not.toBeNull();
      });

      it('with a course open, Archived shows the Archived list and opens its newest course', async () => {
        const first = await open(`${base}/1`);
        const navigate = spyOn(TestBed.inject(Router), 'navigate').and.callThrough();
        first.setView('ARCHIVED');
        const page = await settle();
        expect(page).not.toBe(first); // the address changed, so the page was built afresh
        expect(page.view).toBe('ARCHIVED');
        expect(page.completedSops.map(c => c.id)).toEqual([5, 4]);
        expect(page.document?.id).toBe(5);
        expect(opened()).toEqual([1, 5]); // the page that was left opened nothing more
        expect(address()).toBe(`${base}?view=archived`);
        // The switch replaces the address rather than adding a Back step.
        expect(navigate.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({ replaceUrl: true }));
      });

      it('opens on the Archived list from an address with ?view=archived, and switches back to Current', async () => {
        const page = await open(`${base}?view=archived`);
        expect(page.view).toBe('ARCHIVED');
        expect(page.document?.id).toBe(5);
        page.setView('CURRENT');
        const after = await settle();
        expect(after).toBe(page); // same address, so the same page
        expect(after.view).toBe('CURRENT');
        expect(after.document?.id).toBe(3);
        expect(address()).toBe(base);
      });

      it('does not try again when the newest course fails to open', async () => {
        failing = [3];
        const page = await open(base);
        expect(opened()).toEqual([3]);
        expect(page.document).toBeUndefined();
        expect(page.selectedSopId).toBeNull();
        expect(emptyPage()).not.toBeNull();
        expect(address()).toBe(base);
      });

      it('does not open the newest course after a linked course fails to open', async () => {
        failing = [9];
        await open(`${base}/9`);
        const page = await settle();
        expect(opened()).toEqual([9]);
        expect(page.document).toBeUndefined();
        expect(emptyPage()).not.toBeNull();
        expect(address()).toBe(base);
        // The page still works: switching lists opens a course again.
        page.setView('ARCHIVED');
        expect((await settle()).document?.id).toBe(5);
      });

      it("opens the newest course left after a new course's only draft is discarded", async () => {
        const first = await open(`${base}/2`);
        first.onDiscarded(course(2, 'DRAFT', null));
        const page = await settle();
        expect(page.document?.id).toBe(3);
        expect(opened()).toEqual([2, 3]);
        expect(address()).toBe(base);
      });

      it('drops ?view=archived when an archived course is restored', async () => {
        const page = await open(`${base}?view=archived`);
        expect(page.document?.id).toBe(5);
        sop.detail.and.returnValue(later(() => of({ document: course(5, 'LIVE'), modules: [] })));
        page.onRestored(course(5, 'LIVE'));
        const after = await settle();
        expect(after.view).toBe('CURRENT');
        expect(address()).toBe(base);
      });
    });
  }
});
