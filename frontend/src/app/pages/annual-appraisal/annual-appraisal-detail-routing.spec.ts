import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { Component, EventEmitter } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationCancel, NavigationEnd, provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline } from '@ant-design/icons-angular/icons';
import { filter, firstValueFrom, of } from 'rxjs';
import { routes } from '../../app.routes';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { AnnualAppraisalDetailComponent } from './annual-appraisal-detail.component';
import { appraisal, candidate, performance, period, clearAppraisalNavigationState } from './annual-appraisal-test-fixtures';

@Component({ standalone: true, template: '<p>Queue or destination</p>' })
class DestinationComponent {}

describe('Appraisal detail route lifecycle', () => {
  let api: jasmine.SpyObj<AnnualAppraisalService>;
  let modal: { confirm: jasmine.Spy<NzModalService['confirm']> };
  let router: Router;
  let harness: RouterTestingHarness;
  let grants: string[];
  beforeEach(clearAppraisalNavigationState);
  afterEach(() => { harness?.fixture.destroy(); clearAppraisalNavigationState(); });
  async function setup() {
    grants = ['CAN_SUBMIT_ANNUAL_APPRAISAL', 'CAN_REVIEW_ANNUAL_APPRAISAL'];
    api = jasmine.createSpyObj('AnnualAppraisalService', ['periods', 'team', 'get', 'readiness', 'history', 'performanceHistory', 'create', 'update']);
    api.periods.and.returnValue(of([period]));
    api.team.and.returnValue(of([candidate(), candidate({ participantId: 8, appraisalId: 11, employeeId: 'carol', employeeName: 'Carol' })]));
    api.get.and.callFake(id => of(appraisal(id === 11 ? { id: 11, participantId: 8, employeeId: 'carol', employeeName: 'Carol' } : {})));
    api.history.and.returnValue(of([]));
    api.performanceHistory.and.returnValue(of([]));
    modal = { confirm: spyOn(NzModalService.prototype, 'confirm').and.stub() };
    const detailRoutes = routes.find(r => r.path === '')!.children!.filter(r =>
      r.path === 'team-performance/team-appraisals/:reviewPeriodId/:participantId' || r.path === 'appraisals/review/:appraisalId');
    await TestBed.configureTestingModule({ imports: [AnnualAppraisalDetailComponent, TranslateModule.forRoot()], providers: [
      provideRouter([...detailRoutes, { path: 'team-performance/team-appraisals', component: DestinationComponent },
        { path: 'my-performance/staff/:employeeId', component: DestinationComponent },
        { path: 'appraisals/review', component: DestinationComponent }, { path: 'access-denied', component: DestinationComponent },
        { path: 'elsewhere', component: DestinationComponent }, { path: '', component: DestinationComponent }]),
      provideLocationMocks(), provideNoopAnimations(), { provide: AnnualAppraisalService, useValue: api },
      { provide: AuthService, useValue: { userId: 'alice', hasRole: (p: string) => grants.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline] }
    ] }).compileComponents();
    router = TestBed.inject(Router); harness = await RouterTestingHarness.create();
    // Harness navigation does not bootstrap the application's popstate listener.
    router.setUpLocationChangeListener();
  }
  function answer(ok: boolean) {
    const config = modal.confirm.calls.mostRecent().args[0];
    const callback = ok ? config?.nzOnOk : config?.nzOnCancel;
    if (callback && !(callback instanceof EventEmitter)) callback(undefined);
  }
  async function waitForGuard() { await new Promise(resolve => setTimeout(resolve, 0)); expect(modal.confirm).toHaveBeenCalled(); }

  it('opens the full performance profile with its period/participant and return context after the unsaved guard permits it', async () => {
    await setup(); grants.push('CAN_VIEW_TEAM_PERFORMANCE');
    const detail = await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    detail.justification = 'Keep this recommendation';
    const cancelled = firstValueFrom(router.events.pipe(filter(event => event instanceof NavigationCancel)));
    detail.viewPerformanceProfile(); await waitForGuard(); answer(false); await cancelled;
    expect(router.url).toBe('/team-performance/team-appraisals/2/7'); expect(detail.justification).toBe('Keep this recommendation');
    const ended = firstValueFrom(router.events.pipe(filter(event => event instanceof NavigationEnd)));
    detail.viewPerformanceProfile(); await waitForGuard(); answer(true); await ended;
    const url = router.parseUrl(router.url);
    expect(url.queryParams['reviewPeriodId']).toBe('2'); expect(url.queryParams['participantId']).toBe('7');
    expect(url.queryParams['returnUrl']).toBe('/team-performance/team-appraisals/2/7');
    expect(api.create).not.toHaveBeenCalled();
  });

  it('does not infer full-profile viewing permission from appraisal submission authority', async () => {
    await setup(); const detail = await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    expect(detail.canViewPerformanceProfile).toBeFalse(); detail.viewPerformanceProfile();
    expect(router.url).toBe('/team-performance/team-appraisals/2/7');
  });

  it('loads a direct Superior URL from the authorised queue and discovers an existing Draft', async () => {
    await setup(); const detail = await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    expect(api.team).toHaveBeenCalledOnceWith(2); expect(api.get).toHaveBeenCalledOnceWith(10);
    expect(detail.selected?.id).toBe(10); expect(api.create).not.toHaveBeenCalled();
  });
  it('loads HR detail by record ID without requesting the Superior queue', async () => {
    await setup(); api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['APPROVE'] })));
    const detail = await harness.navigateByUrl('/appraisals/review/10', AnnualAppraisalDetailComponent);
    expect(detail.hrReview).toBeTrue(); expect(detail.frozen).toBeTrue(); expect(api.team).not.toHaveBeenCalled();
  });
  it('cancels navigation to another record without changing URL, employee or unsaved inputs', async () => {
    await setup(); const detail = await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    detail.justification = 'Keep this text'; const navigation = router.navigateByUrl('/team-performance/team-appraisals/2/8');
    await waitForGuard(); answer(false); expect(await navigation).toBeFalse();
    expect(router.url).toBe('/team-performance/team-appraisals/2/7'); expect(detail.candidate?.participantId).toBe(7);
    expect(detail.justification).toBe('Keep this text'); expect(api.get).toHaveBeenCalledTimes(1);
  });
  it('protects the page Back link and sidebar navigation', async () => {
    await setup(); const detail = await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    detail.justification = 'Do not discard';
    const cancelled = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationCancel)));
    detail.back(); await waitForGuard(); answer(false); await cancelled;
    expect(router.url).toBe('/team-performance/team-appraisals/2/7');
    const sidebar = router.navigateByUrl('/elsewhere'); await waitForGuard(); answer(false); expect(await sidebar).toBeFalse();
    expect(detail.justification).toBe('Do not discard');
  });
  it('supports browser Back/Forward and preserves a dirty record when browser Back is cancelled', async () => {
    await setup(); await harness.navigateByUrl('/team-performance/team-appraisals/2/7', AnnualAppraisalDetailComponent);
    await harness.navigateByUrl('/team-performance/team-appraisals/2/8', AnnualAppraisalDetailComponent);
    const location = TestBed.inject(Location);
    let ended = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationEnd)));
    location.back(); await ended; expect(router.url).toBe('/team-performance/team-appraisals/2/7');
    ended = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationEnd)));
    location.forward(); await ended; expect(router.url).toBe('/team-performance/team-appraisals/2/8');
    const detail = harness.routeDebugElement!.componentInstance as AnnualAppraisalDetailComponent;
    detail.justification = 'Unsaved Carol feedback';
    const cancelled = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationCancel)));
    location.back(); await waitForGuard(); answer(false); await cancelled;
    expect(router.url).toBe('/team-performance/team-appraisals/2/8'); expect(detail.candidate?.participantId).toBe(8);
    expect(detail.justification).toBe('Unsaved Carol feedback');
  });
  it('does not load HR data when only Superior permission is granted', async () => {
    await setup(); grants.splice(1); await router.navigateByUrl('/appraisals/review/10');
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(router.url).toBe('/access-denied'); expect(api.get).not.toHaveBeenCalled(); expect(api.periods).not.toHaveBeenCalled();
  });
  it('retains the HR modal, record and URL when sidebar, another record or browser Back is cancelled', async () => {
    await setup(); await harness.navigateByUrl('/appraisals/review');
    api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['RETURN'] })));
    const detail = await harness.navigateByUrl('/appraisals/review/10', AnnualAppraisalDetailComponent);
    detail.beginDecision('RETURN'); detail.reason = 'Keep this HR feedback'; harness.detectChanges();
    await harness.fixture.whenStable(); harness.detectChanges();
    for (const path of ['/elsewhere', '/appraisals/review/11']) {
      const navigating = router.navigateByUrl(path); await waitForGuard(); answer(false); expect(await navigating).toBeFalse();
      expect(router.url).toBe('/appraisals/review/10'); expect(detail.selected?.employeeId).toBe('bob');
      expect(detail.reason).toBe('Keep this HR feedback'); expect(detail.decision).toBe('RETURN');
    }
    const cancelled = firstValueFrom(router.events.pipe(filter(e => e instanceof NavigationCancel)));
    TestBed.inject(Location).back(); await waitForGuard(); answer(false); await cancelled;
    harness.detectChanges(); await harness.fixture.whenStable(); harness.detectChanges();
    expect(router.url).toBe('/appraisals/review/10');
    expect(document.querySelector<HTMLTextAreaElement>('#annual-decision-reason')?.value).toBe('Keep this HR feedback');
    expect(api.get).toHaveBeenCalledTimes(1);
    const accepted = router.navigateByUrl('/elsewhere'); await waitForGuard(); answer(true); expect(await accepted).toBeTrue();
    harness.detectChanges(); await harness.fixture.whenStable();
    expect(document.querySelector('.decision-modal')).toBeNull();
  });
});
