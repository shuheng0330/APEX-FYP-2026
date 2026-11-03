import { Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouteReuseStrategy, convertToParamMap, provideRouter, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationRouteReuseStrategy, NotificationTarget, localNotificationUrl, notificationSelectionChanged } from './notification-navigation';

describe('Notification target validation', () => {
  const translate = { instant: (key: string) => key } as TranslateService;
  const target = (params: Record<string, string | string[]> = {}) => new NotificationTarget(convertToParamMap(params), translate);
  it('selects only authorised IDs and consumes URL selections once', () => {
    const link = target({ reviewPeriodId: '2' });
    expect(link.choose([{ id: 1 }, { id: 2 }], 'reviewPeriodId', p => p.id, 1)).toBe(2);
    expect(link.choose([{ id: 1 }, { id: 2 }], 'reviewPeriodId', p => p.id, 1)).toBe(1);
  });
  for (const value of ['0', '-1', '1.5', '1e2', '01', 'NaN', '9007199254740992', ['1', '2']]) {
    it(`rejects malformed/ambiguous IDs ${value}`, () => {
      const link = target({ planId: value }); expect(() => link.take('planId')).toThrow(); expect(link.unavailable).toBeTrue();
    });
  }
  it('does not silently select a different record or period', () => {
    expect(() => target({ reviewPeriodId: '3' }).choose([{ id: 2 }], 'reviewPeriodId', p => p.id, 2)).toThrow();
    expect(() => target({ reviewPeriodId: '3', assessmentId: '7' }).record([{ id: 7, reviewPeriodId: 2 }], 'assessmentId')).toThrow();
    expect(() => target({ assessmentId: '8' }).record([{ id: 7, reviewPeriodId: 2 }], 'assessmentId')).toThrow();
  });
  it('validates fresh server data before it reaches an existing drawer', () => {
    const read = jasmine.createSpy('get').and.returnValue(of({ id: 8, reviewPeriodId: 2 }));
    const link = target(); let accepted = false;
    link.load({ id: 7, reviewPeriodId: 2 }, read).subscribe({ next: () => accepted = true, error: () => {} });
    expect(read).toHaveBeenCalledOnceWith(7); expect(accepted).toBeFalse(); expect(link.unavailable).toBeTrue();
  });
  it('does not load or create anything when there is no record selector', () => {
    const read = jasmine.createSpy('get'); target().load(null, read).subscribe(value => expect(value).toBeNull());
    expect(read).not.toHaveBeenCalled();
  });
  it('rejects unauthorised tabs rather than substituting another task', () => {
    expect(() => target({ tab: 'attitude' }).takeTab(['reviews'])).toThrow();
    expect(() => target({ tab: 'attitude', notice: '1' }).validateReviewTab(['reviews'])).toThrow();
    expect(() => target({ tab: 'attitude' }).validateReviewTab(['reviews'])).not.toThrow();
  });
  it('allows local app paths but rejects external, protocol-relative and malformed paths', () => {
    expect(localNotificationUrl('/training?id=3')).toBeTrue();
    for (const link of ['https://example.com', '//example.com', '/\\evil', 'javascript:alert(1)', '/x\n'])
      expect(localNotificationUrl(link)).toBeFalse();
  });
});

@Component({ standalone: true, template: '<input [value]="answer"><span>{{record}}</span>' })
class NotificationWorkspace {
  record = inject(ActivatedRoute).snapshot.queryParamMap.get('assessmentId');
  answer = 'Unsaved recommendation';
  leave: boolean | Promise<boolean> = true;
  canLeave() { return this.leave; }
}

describe('Notification same-workspace navigation', () => {
  let router: Router;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([
      { path: 'tasks', component: NotificationWorkspace, data: { notificationWorkspace: true },
        runGuardsAndResolvers: notificationSelectionChanged, canDeactivate: [(c: NotificationWorkspace) => c.canLeave()] },
      { path: 'training', component: NotificationWorkspace }
    ], withRouterConfig({ canceledNavigationResolution: 'computed' })), provideLocationMocks(), { provide: RouteReuseStrategy, useClass: NotificationRouteReuseStrategy }] });
    router = TestBed.inject(Router);
  });
  it('reloads authorised record links, including repeated notices to the same record', async () => {
    const harness = await RouterTestingHarness.create();
    const first = await harness.navigateByUrl('/tasks?assessmentId=1&notice=a', NotificationWorkspace);
    const second = await harness.navigateByUrl('/tasks?assessmentId=2&notice=b', NotificationWorkspace);
    expect(second).not.toBe(first); expect(second.record).toBe('2');
    expect(await harness.navigateByUrl('/tasks?assessmentId=2&notice=c', NotificationWorkspace)).not.toBe(second);
  });
  it('keeps employee, URL and input unchanged when the discard check is cancelled', async () => {
    const harness = await RouterTestingHarness.create();
    const first = await harness.navigateByUrl('/tasks?assessmentId=1&notice=a', NotificationWorkspace);
    first.leave = Promise.resolve(false); first.answer = 'Keep this input';
    expect(await router.navigateByUrl('/tasks?assessmentId=2&notice=b')).toBeFalse();
    expect(router.url).toBe('/tasks?assessmentId=1&notice=a'); expect(first.record).toBe('1'); expect(first.answer).toBe('Keep this input');
  });
  it('blocks a busy workspace, then permits navigation when the task finishes', async () => {
    const harness = await RouterTestingHarness.create();
    const first = await harness.navigateByUrl('/tasks?assessmentId=1', NotificationWorkspace); first.leave = false;
    expect(await router.navigateByUrl('/tasks?assessmentId=2')).toBeFalse(); first.leave = true;
    const second = await harness.navigateByUrl('/tasks?assessmentId=2', NotificationWorkspace); expect(second.record).toBe('2');
  });
  it('leaves normal tab navigation and unrelated training route reuse unchanged', async () => {
    const harness = await RouterTestingHarness.create();
    const first = await harness.navigateByUrl('/tasks?tab=reviews', NotificationWorkspace);
    expect(await harness.navigateByUrl('/tasks?tab=attitude', NotificationWorkspace)).toBe(first);
    const training = await harness.navigateByUrl('/training?notice=1', NotificationWorkspace);
    expect(await harness.navigateByUrl('/training?notice=2', NotificationWorkspace)).toBe(training);
  });
  it('revalidates browser Back/Forward without discarding cancelled input', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tasks?assessmentId=1&notice=a', NotificationWorkspace);
    router.initialNavigation();
    const second = await harness.navigateByUrl('/tasks?assessmentId=2&notice=b', NotificationWorkspace); second.leave = false;
    const location = TestBed.inject(Location); location.back();
    await new Promise(resolve => setTimeout(resolve, 10)); await harness.fixture.whenStable();
    expect(router.url).toBe('/tasks?assessmentId=2&notice=b'); expect(second.answer).toBe('Unsaved recommendation');
    second.leave = true; location.back(); await new Promise(resolve => setTimeout(resolve, 10)); await harness.fixture.whenStable();
    expect(router.url).toBe('/tasks?assessmentId=1&notice=a');
    location.forward(); await new Promise(resolve => setTimeout(resolve, 10)); await harness.fixture.whenStable();
    expect(router.url).toBe('/tasks?assessmentId=2&notice=b');
  });
});
