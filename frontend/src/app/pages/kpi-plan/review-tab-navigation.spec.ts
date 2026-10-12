import { Component, DestroyRef, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ReviewTabNavigation } from './review-tab-navigation';
import { OrganisationReviewTab, TeamReviewTab, organisationReviewTabs, teamReviewTabs } from './review-task-directory';

let permissions: string[];
@Component({ standalone: true, template: '<input [value]="answer"><span>{{tab}}</span>' })
class TabWorkspace {
  answer = 'Unsaved input';
  checks = 0;
  leave: boolean | Promise<boolean> = true;
  readonly route = inject(ActivatedRoute);
  tab: TeamReviewTab | OrganisationReviewTab | null = null;
  readonly navigation = new ReviewTabNavigation(inject(Router), this.route, inject(DestroyRef),
    () => this.route.snapshot.routeConfig!.path === 'team' ? teamReviewTabs(p => permissions.includes(p)) : organisationReviewTabs(p => permissions.includes(p)),
    () => this.tab, tab => this.tab = tab, () => { this.checks++; return this.leave; });
  constructor() { this.tab = this.navigation.initial(); }
  ngOnInit() { this.navigation.connect(); }
}

describe('Review tab deep links and history', () => {
  let router: Router;
  beforeEach(() => {
    permissions = ['CAN_REVIEW_INDIVIDUAL_KPI', 'CAN_REVIEW_KPI_ASSESSMENT', 'CAN_REVIEW_ATTITUDE_EVALUATION',
      'CAN_APPROVE_DEPARTMENT_KPI', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'];
    TestBed.configureTestingModule({ providers: [provideRouter([
      { path: 'team', component: TabWorkspace, canDeactivate: [(component: TabWorkspace) => component.leave] },
      { path: 'organisation', component: TabWorkspace, canDeactivate: [(component: TabWorkspace) => component.leave] }
    ]), provideLocationMocks()] });
    router = TestBed.inject(Router);
  });
  async function settle(harness: RouterTestingHarness) {
    // Location popstate and promises returned by edit confirmations finish outside fixture stability.
    await new Promise(resolve => setTimeout(resolve, 0));
    await harness.fixture.whenStable(); harness.detectChanges();
  }
  it('loads and refreshes every permitted tab without changing its query value', async () => {
    const harness = await RouterTestingHarness.create();
    for (const [path, tabs] of [['team', teamReviewTabs(() => true)], ['organisation', organisationReviewTabs(() => true)]] as const) {
      for (const tab of tabs) {
        const workspace = await harness.navigateByUrl(`/${path}?tab=${tab}`, TabWorkspace);
        await settle(harness);
        expect(workspace.tab).toBe(tab);
        // Recreating the component represents a browser refresh of its deep link.
        await harness.navigateByUrl('/' + (path === 'team' ? 'organisation' : 'team'));
        const refreshed = await harness.navigateByUrl(`/${path}?tab=${tab}`, TabWorkspace);
        await settle(harness); expect(refreshed.tab).toBe(tab);
      }
    }
  });
  it('defaults missing, unknown and unauthorised tabs and exposes no tab without permission', async () => {
    permissions = ['CAN_REVIEW_ATTITUDE_EVALUATION', 'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'];
    const harness = await RouterTestingHarness.create();
    for (const url of ['/team', '/team?tab=unknown', '/team?tab=reviews']) {
      const workspace = await harness.navigateByUrl(url, TabWorkspace); await settle(harness);
      expect(workspace.tab).toBe('attitude');
      expect(router.url).not.toContain('tab=reviews'); expect(router.url).not.toContain('unknown');
    }
    const org = await harness.navigateByUrl('/organisation?tab=department', TabWorkspace); await settle(harness);
    expect(org.tab).toBe('assistance'); expect(router.url).toContain('tab=assistance');
    permissions = [];
    const none = await harness.navigateByUrl('/team?tab=attitude', TabWorkspace); await settle(harness);
    expect(none.tab).toBeNull(); expect(router.url).toBe('/team');
  });
  it('keeps buttons, browser back/forward and unrelated query context in sync', async () => {
    const harness = await RouterTestingHarness.create('/team?tab=reviews&context=keep');
    router.initialNavigation();
    const workspace = harness.routeDebugElement!.componentInstance as TabWorkspace;
    workspace.navigation.select('assessments'); await settle(harness);
    expect(workspace.tab).toBe('assessments'); expect(router.url).toContain('context=keep');
    workspace.navigation.select('attitude'); await settle(harness);
    expect(router.url).toBe('/team?tab=attitude&context=keep');
    TestBed.inject(Location).back(); await settle(harness);
    expect(workspace.tab).toBe('assessments');
    expect(router.url).toBe('/team?tab=assessments&context=keep');
    TestBed.inject(Location).forward(); await settle(harness);
    expect(workspace.tab).toBe('attitude');
  });
  it('restores the accepted URL and keeps inputs when browser history is blocked', async () => {
    const harness = await RouterTestingHarness.create('/team?tab=reviews');
    router.initialNavigation();
    const workspace = harness.routeDebugElement!.componentInstance as TabWorkspace;
    workspace.navigation.select('revisions'); await settle(harness);
    workspace.leave = false;
    TestBed.inject(Location).back(); await settle(harness);
    expect(workspace.tab).toBe('revisions'); expect(router.url).toBe('/team?tab=revisions');
    expect(workspace.answer).toBe('Unsaved input');
  });
  it('uses one unsaved confirmation and restores the URL when guide navigation is cancelled', async () => {
    const harness = await RouterTestingHarness.create('/organisation?tab=revisions');
    const workspace = harness.routeDebugElement!.componentInstance as TabWorkspace;
    let resolve!: (allowed: boolean) => void;
    workspace.leave = new Promise<boolean>(r => resolve = r);
    await router.navigateByUrl('/organisation?tab=assistance');
    expect(workspace.tab).toBe('revisions'); expect(workspace.navigation.pending).toBeTrue();
    workspace.navigation.select('department'); expect(workspace.checks).toBe(1);
    resolve(false); await settle(harness);
    expect(workspace.tab).toBe('revisions'); expect(router.url).toBe('/organisation?tab=revisions');
    expect(workspace.answer).toBe('Unsaved input');
    workspace.leave = Promise.resolve(true);
    workspace.navigation.select('department'); await settle(harness);
    expect(workspace.tab).toBe('department'); expect(router.url).toBe('/organisation?tab=department');
  });
  it('rejects restricted tab clicks before invoking edit checks', async () => {
    permissions = ['CAN_REVIEW_KPI_ASSESSMENT'];
    const harness = await RouterTestingHarness.create('/team?tab=assessments');
    const workspace = harness.routeDebugElement!.componentInstance as TabWorkspace;
    workspace.navigation.select('revisions'); await settle(harness);
    expect(workspace.tab).toBe('assessments'); expect(workspace.checks).toBe(0);
  });
  it('preserves cross-page route guards when a task guide destination is cancelled', async () => {
    const harness = await RouterTestingHarness.create('/team?tab=assessments');
    const workspace = harness.routeDebugElement!.componentInstance as TabWorkspace;
    workspace.leave = false;
    expect(await router.navigateByUrl('/organisation?tab=revisions')).toBeFalse(); await settle(harness);
    expect(router.url).toBe('/team?tab=assessments'); expect(workspace.answer).toBe('Unsaved input');
    workspace.leave = true;
    expect(await router.navigateByUrl('/organisation?tab=revisions')).toBeTrue(); await settle(harness);
    expect(router.url).toBe('/organisation?tab=revisions');
  });
});
