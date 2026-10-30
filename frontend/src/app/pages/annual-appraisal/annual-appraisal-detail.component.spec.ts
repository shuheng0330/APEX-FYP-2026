import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LeftOutline, RightOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { Observable, of, Subject, throwError } from 'rxjs';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { AnnualAppraisal, AnnualAppraisalActivity, AnnualAppraisalCandidate, AnnualAppraisalReadiness, AnnualAppraisalSnapshot, AnnualPerformanceHistory } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';
import { AnnualAppraisalDetailComponent } from './annual-appraisal-detail.component';
import { AnnualAppraisalPerformanceComponent } from './annual-appraisal-performance.component';

import { appraisal, candidate, performance, period, readiness, clearAppraisalNavigationState } from './annual-appraisal-test-fixtures';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

describe('Full-page annual appraisal details', () => {
  let fixture: ComponentFixture<AnnualAppraisalDetailComponent>;
  let component: AnnualAppraisalDetailComponent;
  let api: jasmine.SpyObj<AnnualAppraisalService>;
  let modal: { confirm: jasmine.Spy<NzModalService['confirm']> };
  let hrReview: boolean;
  let permissions: string[];
  let params: Subject<ParamMap>;
  let route: { snapshot: { data: { hrReview: boolean }; paramMap: ParamMap }; paramMap: Observable<ParamMap> };
  async function setup(hr = false, grants?: string[]) {
    hrReview = hr; permissions = grants ?? [hr ? 'CAN_REVIEW_ANNUAL_APPRAISAL' : 'CAN_SUBMIT_ANNUAL_APPRAISAL'];
    api = jasmine.createSpyObj('AnnualAppraisalService', ['periods', 'team', 'reviews', 'get', 'readiness', 'history', 'performanceHistory', 'create', 'update', 'submit', 'approve', 'overrideApprove', 'returnForRevision']);
    api.periods.and.returnValue(of([period])); api.team.and.returnValue(of([candidate()])); api.reviews.and.returnValue(of([]));
    api.get.and.returnValue(of(appraisal())); api.readiness.and.returnValue(of(readiness())); api.history.and.returnValue(of([]));
    api.performanceHistory.and.returnValue(of([]));
    api.create.and.returnValue(of(appraisal())); api.update.and.returnValue(of(appraisal({ lockVersion: 2 })));
    api.submit.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: [] })));
    params = new Subject<ParamMap>();
    route = { snapshot: { data: { hrReview }, paramMap: convertToParamMap({}) }, paramMap: params.asObservable() };
    await TestBed.configureTestingModule({ imports: [AnnualAppraisalDetailComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), provideNoopAnimations(), { provide: ActivatedRoute, useValue: route },
      { provide: AnnualAppraisalService, useValue: api }, { provide: AuthService, useValue: { userId: hr ? 'david' : 'alice', hasRole: (p: string) => permissions.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LeftOutline, RightOutline, LoadingOutline] }
    ] }).compileComponents();
    sessionStorage.removeItem(`annual-appraisal-period:${hr ? 'hr:david' : 'team:alice'}`);
    fixture = TestBed.createComponent(AnnualAppraisalDetailComponent); component = fixture.componentInstance;
    modal = { confirm: spyOn(fixture.debugElement.injector.get(NzModalService), 'confirm').and.stub() };
    fixture.detectChanges();
  }
  function open(row = candidate()) {
    api.team.and.returnValue(of([row]));
    route.snapshot.paramMap = convertToParamMap(hrReview ? { appraisalId: '10' } : { reviewPeriodId: '2', participantId: String(row.participantId) });
    params.next(route.snapshot.paramMap);
  }
  function answerConfirmation(ok: boolean) {
    const config = modal.confirm.calls.mostRecent().args[0];
    const callback = ok ? config?.nzOnOk : config?.nzOnCancel;
    if (callback && !(callback instanceof EventEmitter)) return callback(undefined);
  }
  async function openHr(overrides: Partial<AnnualAppraisal> = {}) {
    await setup(true);
    api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), submittedAt: '2026-10-20T10:00:00Z',
      allowedActions: ['APPROVE', 'OVERRIDE_AND_APPROVE', 'RETURN'], ...overrides })));
    open(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
  }
  async function renderModal() {
    fixture.detectChanges(); await fixture.whenStable();
    // APEX completes overlay focus/restore outside Angular's stability tracking.
    await new Promise(resolve => setTimeout(resolve, 0)); fixture.detectChanges();
  }
  function submission(id: number, action: AnnualAppraisalActivity['action'] = 'SUBMITTED'): AnnualAppraisalActivity {
    return { id, action, actorId: 'alice', occurredAt: '2026-10-20T12:00:00Z', reason: null,
      recommendation: 'SALARY_INCREMENT', justification: 'Consistent delivery', finalDecision: null, snapshot: performance() };
  }
  function resubmittedAppraisal(): AnnualAppraisal {
    return appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), recommendation: 'PROMOTION',
      justification: 'Updated delivery evidence', submittedAt: '2026-10-20T12:00:00Z', submittedLate: true,
      latestReturnReason: 'Stale current reason must not be used', allowedActions: ['APPROVE', 'OVERRIDE_AND_APPROVE', 'RETURN'],
      activity: [submission(1), { ...submission(2, 'RETURNED'), reason: 'Explain the change\nInclude supporting evidence' },
        { ...submission(3, 'DRAFT_SAVED'), recommendation: 'BOTH', justification: 'Unsaved intermediate rationale' },
        { ...submission(4, 'RESUBMITTED'), recommendation: 'PROMOTION', justification: 'Updated delivery evidence' }] });
  }
  function detailSectionOrder(): string[] {
    const selectors = [['return', '.current-return-reason'], ['decision', '.appraisal-decision-summary'],
      ['results', 'app-annual-appraisal-performance'], ['trend', 'app-annual-performance-trend'],
      ['history', '.appraisal-history'], ['recommendation', '.appraisal-recommendation'],
      ['activity', '.appraisal-secondary'], ['actions', '.hr-decisions']] as const;
    const page = fixture.nativeElement.querySelector('.appraisal-detail-page') as HTMLElement;
    return [...page.children].flatMap(element => selectors.filter(([, selector]) => element.matches(selector)).map(([name]) => name));
  }
  beforeEach(clearAppraisalNavigationState);
  afterEach(() => { fixture?.destroy(); clearAppraisalNavigationState(); });

  it('loads only the authorised workspace, never inferring access from Super Admin', async () => {
    await setup(false, ['Super Admin', 'ROLE_USER']); open();
    expect(api.periods).not.toHaveBeenCalled(); expect(api.team).not.toHaveBeenCalled();
    expect(component.authorised).toBeFalse();
  });
  it('opens an unsaved appraisal without creating a persisted Draft or loading every employee history', async () => {
    await setup(); open(candidate({ appraisalId: null, status: null }));
    expect(api.readiness).toHaveBeenCalledOnceWith(7); expect(api.create).not.toHaveBeenCalled();
    expect(api.history).toHaveBeenCalledOnceWith(7); expect(component.dirty).toBeFalse(); expect(component.readonly).toBeFalse();
    fixture.detectChanges(); expect(detailSectionOrder()).toEqual(['results', 'trend', 'history', 'recommendation']);
    expect(fixture.nativeElement.querySelectorAll('#annual-justification').length).toBe(1);
  });
  it('saves incomplete Drafts before readiness, including after a validation attempt', async () => {
    await setup(); open(candidate({ appraisalId: null, status: null }));
    component.readiness = readiness(false); component.showValidation = true;
    component.recommendation = null; component.justification = ''; component.saveDraft();
    expect(api.create).toHaveBeenCalledWith({ participantId: 7, recommendation: null, justification: '' });
    expect(api.submit).not.toHaveBeenCalled();
  });
  for (const complete of [true, false]) {
    it(`shows only Draft beside the title after saving, with results ${complete ? 'complete' : 'pending'} shown separately`, async () => {
      await setup(); const results = readiness(complete);
      api.readiness.and.returnValue(of(results)); api.create.and.returnValue(of(appraisal({ readiness: results })));
      open(candidate({ appraisalId: null, status: null, readiness: results })); component.saveDraft(); fixture.detectChanges();
      const root: HTMLElement = fixture.nativeElement;
      const statuses = root.querySelectorAll('.summary-title .status');
      expect(statuses.length).toBe(1); expect(statuses[0].getAttribute('data-status')).toBe('DRAFT');
      expect(root.querySelector('.results-readiness')?.textContent).toContain(complete ? 'ANNUAL_APPRAISAL.RESULTS_COMPLETE' : 'ANNUAL_APPRAISAL.RESULTS_PENDING');
      expect(component.nextStepKey).toBe(complete ? 'ANNUAL_APPRAISAL.NEXT.DRAFT_READY' : 'ANNUAL_APPRAISAL.NEXT.DRAFT');
      expect(component.canStartSubmit).toBe(complete); expect(api.submit).not.toHaveBeenCalled();
    });
  }
  it('keeps Returned as the only appraisal badge without hiding readiness or the return reason', async () => {
    await setup(); api.get.and.returnValue(of(appraisal({ status: 'RETURNED', latestReturnReason: 'Explain the recommendation' })));
    open(); fixture.detectChanges(); const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('.summary-title .status').length).toBe(1);
    expect(root.querySelector('.summary-title .status')?.getAttribute('data-status')).toBe('RETURNED');
    expect(root.querySelector('.results-readiness')?.textContent).toContain('ANNUAL_APPRAISAL.RESULTS_COMPLETE');
    expect(root.textContent).toContain('Explain the recommendation');
  });
  for (const hr of [false, true]) for (const status of ['PENDING_REVIEW', 'APPROVED'] as const) {
    it(`keeps the return notice inactive and limits the comparison to pending HR resubmissions for ${hr ? 'HR' : 'Superior'} ${status}`, async () => {
      await setup(hr);
      const reason = 'Please justify the proposed promotion';
      const returned = { ...submission(1, 'RETURNED'), reason };
      const value = appraisal({ status, snapshot: performance(), latestReturnReason: reason, allowedActions: [],
        activity: [returned, submission(2, 'RESUBMITTED')] });
      api.get.and.returnValue(of(value)); open(); fixture.detectChanges();
      const page = fixture.nativeElement.querySelector('.appraisal-detail-page') as HTMLElement;
      expect(page.querySelector('.current-return-reason')).toBeNull();
      if (hr && status === 'PENDING_REVIEW') expect(page.querySelector('.previous-return-reason p')?.textContent).toBe(reason);
      else expect(page.textContent).not.toContain(reason);
      component.activityVisible = true; fixture.detectChanges();
      expect(page.querySelector('.activity-timeline .event-reason')?.textContent).toBe(reason);
      expect(component.selected?.latestReturnReason).toBe(reason);
      expect(component.selected?.activity).toEqual(value.activity);
    });
  }
  for (const scenario of [
    { hr: false, status: 'DRAFT', order: ['results', 'trend', 'history', 'recommendation', 'activity'] },
    { hr: false, status: 'PENDING_REVIEW', order: ['results', 'trend', 'history', 'recommendation', 'activity'] },
    { hr: false, status: 'RETURNED', order: ['return', 'results', 'trend', 'history', 'recommendation', 'activity'] },
    { hr: false, status: 'APPROVED', order: ['decision', 'results', 'trend', 'history', 'activity'] },
    { hr: true, status: 'PENDING_REVIEW', order: ['results', 'recommendation', 'trend', 'history', 'activity', 'actions'] },
    { hr: true, status: 'RETURNED', order: ['return', 'results', 'recommendation', 'trend', 'history', 'activity'] },
    { hr: true, status: 'APPROVED', order: ['decision', 'results', 'trend', 'history', 'activity'] }
  ] as const) {
    it(`uses the actual DOM hierarchy for ${scenario.hr ? 'HR' : 'Superior'} ${scenario.status}`, async () => {
      await setup(scenario.hr);
      api.get.and.returnValue(of(appraisal({ status: scenario.status, snapshot: performance(), sourceChanged: scenario.status === 'RETURNED',
        latestReturnReason: 'Explain the recommendation.\nInclude supporting evidence.', submissionBlockers: ['Check current results'],
        allowedActions: scenario.status === 'PENDING_REVIEW' ? ['RETURN'] : scenario.status === 'APPROVED' ? [] : ['SAVE_DRAFT'] })));
      open(); component.error = 'System notice'; fixture.detectChanges();
      expect(detailSectionOrder()).toEqual([...scenario.order]);
      const root: HTMLElement = fixture.nativeElement;
      expect(root.querySelectorAll('.appraisal-recommendation').length).toBe(scenario.status === 'APPROVED' ? 0 : 1);
      expect(component.historyVisible).toBeFalse(); expect(component.activityVisible).toBeFalse();
      if (scenario.status === 'RETURNED') {
        const reason = root.querySelector<HTMLElement>('.current-return-reason')!;
        expect(root.querySelectorAll('.current-return-reason').length).toBe(1);
        expect(reason.querySelector('p')?.textContent).toBe('Explain the recommendation.\nInclude supporting evidence.');
        expect(getComputedStyle(reason.querySelector('p')!).whiteSpace).toBe('pre-wrap');
        for (const notice of root.querySelectorAll('.appraisal-detail-heading, .error-box, .draft-readiness:not(.current-return-reason)')) {
          expect(notice.compareDocumentPosition(reason) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        }
        expect(root.querySelectorAll('#annual-justification').length).toBe(scenario.hr ? 0 : 1);
      }
      if (scenario.hr && scenario.status !== 'APPROVED') {
        expect(root.querySelector('.appraisal-recommendation')?.textContent).toContain('ANNUAL_APPRAISAL.RECOMMENDATION_READ_ONLY_HELP');
        expect(root.querySelector('.appraisal-recommendation textarea')).toBeNull();
      }
    });
  }
  for (const hr of [false, true]) {
    it(`keeps the top return reason but prohibits edits in a Closed ${hr ? 'HR' : 'Superior'} Returned view`, async () => {
      await setup(hr);
      api.periods.and.returnValue(of([{ ...period, status: 'CLOSED' }]));
      api.get.and.returnValue(of(appraisal({ status: 'RETURNED', reviewPeriodStatus: 'CLOSED', latestReturnReason: 'Clarify the recommendation', allowedActions: [] })));
      open(); fixture.detectChanges();
      expect(detailSectionOrder()[0]).toBe('return'); expect(component.readonly).toBeTrue();
      expect(fixture.nativeElement.querySelector('.hr-decisions')).toBeNull();
      if (!hr) expect(fixture.nativeElement.querySelector('fieldset').disabled).toBeTrue();
      component.saveDraft(); expect(api.update).not.toHaveBeenCalled();
    });
    for (const override of [false, true]) {
      it(`shows one read-only ${override ? 'override' : 'normal approval'} summary for ${hr ? 'HR' : 'Superior'} with recorded details`, async () => {
        await setup(hr);
        const value = appraisal({ status: 'APPROVED', snapshot: { ...performance(), finalScore: 0 }, allowedActions: [],
          recommendation: 'SALARY_INCREMENT', finalDecision: override ? 'NO_RECOMMENDATION' : 'SALARY_INCREMENT',
          justification: 'Consistent delivery\nStrong teamwork', overrideReason: override ? 'Recorded budget constraint\nDiscuss next year' : null,
          submittedAt: '2026-10-20T10:00:00Z', reviewedAt: '2026-10-21T10:00:00Z', submittedLate: true, reviewedLate: true });
        api.get.and.returnValue(of(value)); open(); fixture.detectChanges();
        const root: HTMLElement = fixture.nativeElement;
        const summary = root.querySelector<HTMLElement>('.appraisal-decision-summary')!;
        expect(root.querySelectorAll('.appraisal-decision-summary').length).toBe(1);
        expect(summary.querySelector('h3')?.textContent).toBe('ANNUAL_APPRAISAL.DECISION_SUMMARY');
        expect(summary.querySelector('.recorded-recommendation')?.textContent).toBe('ANNUAL_APPRAISAL.RECOMMENDATIONS.SALARY_INCREMENT');
        expect(summary.querySelector('.recorded-final-decision')?.textContent).toBe('ANNUAL_APPRAISAL.RECOMMENDATIONS.' + value.finalDecision);
        expect(summary.querySelector('.recorded-justification')?.textContent).toBe(value.justification);
        expect(summary.querySelector('.recorded-override-reason')?.textContent ?? null).toBe(value.overrideReason);
        expect(summary.textContent).toContain('20 Oct 2026'); expect(summary.textContent).toContain('21 Oct 2026');
        const latePills = summary.querySelectorAll('.status.overdue');
        expect(latePills.length).toBe(2);
        for (const pill of latePills) {
          expect(pill.textContent).toBe('ANNUAL_APPRAISAL.LATE');
          expect(getComputedStyle(pill).backgroundColor).toBe('rgb(255, 242, 240)');
          expect(getComputedStyle(pill).borderRadius).toBe('20px');
        }
        expect(summary.querySelector('button, textarea, input, nz-select')).toBeNull();
        expect(root.querySelector('.appraisal-recommendation')).toBeNull(); expect(root.querySelector('.hr-decisions')).toBeNull();
        expect(root.querySelector('.score-grid .final-score dd')?.textContent).toBe('0.00');
        expect(component.selected).toEqual(value); expect(api.update).not.toHaveBeenCalled();
      });
    }
  }
  it('does not infer absent Approved decisions, justification or dates', async () => {
    await openHr({ status: 'APPROVED', recommendation: null, finalDecision: null, justification: null,
      submittedAt: null, reviewedAt: null, allowedActions: [] });
    const summary: HTMLElement = fixture.nativeElement.querySelector('.appraisal-decision-summary');
    expect(summary.querySelector('.recorded-recommendation')?.textContent).toBe('-');
    expect(summary.querySelector('.recorded-final-decision')?.textContent).toBe('-');
    expect(summary.querySelector('.recorded-justification')?.textContent).toBe('-');
    expect(summary.querySelector('.recorded-override-reason')).toBeNull();
    expect(summary.textContent).not.toContain('undefined'); expect(summary.textContent).not.toContain('RECOMMENDATIONS.null');
  });
  it('removes the current return notice after resubmission without deleting previous return events', async () => {
    await setup(); const reason = 'Explain the proposed change';
    const returned = appraisal({ status: 'RETURNED', latestReturnReason: reason, activity: [{ ...submission(1, 'RETURNED'), reason }] });
    api.get.and.returnValue(of(returned)); open(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.current-return-reason')).not.toBeNull();
    component.justification = 'Additional supporting evidence';
    api.update.and.returnValue(of({ ...returned, justification: component.justification, lockVersion: 2 }));
    api.submit.and.returnValue(of({ ...returned, status: 'PENDING_REVIEW', snapshot: performance(), justification: component.justification,
      allowedActions: [], activity: [...returned.activity, submission(2, 'RESUBMITTED')] }));
    component.requestSubmit(); answerConfirmation(true); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.current-return-reason')).toBeNull();
    expect(component.selected?.latestReturnReason).toBe(reason);
    component.activityVisible = true; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.event-reason')?.textContent).toBe(reason);
    expect(api.submit).toHaveBeenCalledOnceWith(10, { expectedLockVersion: 2 });
  });
  for (const hr of [false, true]) {
    it(`keeps the ${hr ? 'HR' : 'Superior'} deadline in the right-hand action area with the correct date and overdue notice`, async () => {
      await setup(hr); api.get.and.returnValue(of(appraisal({ status: hr ? 'PENDING_REVIEW' : 'DRAFT',
        recommendationDeadline: '2026-10-26', recommendationOverdue: true, hrReviewOverdue: true })));
      open(); fixture.detectChanges();
      const root: HTMLElement = fixture.nativeElement;
      const deadline = root.querySelector('.detail-tools .detail-deadline');
      expect(deadline?.textContent).toContain(hr ? 'ANNUAL_APPRAISAL.HR_DEADLINE' : 'ANNUAL_APPRAISAL.SUPERIOR_DEADLINE');
      expect(deadline?.textContent).toContain(hr ? '10 Nov 2026' : '26 Oct 2026');
      const overdue = deadline?.querySelector('.status.overdue');
      expect(overdue?.textContent).toContain('ANNUAL_APPRAISAL.OVERDUE');
      expect(getComputedStyle(overdue!).backgroundColor).toBe('rgb(255, 242, 240)');
      expect(getComputedStyle(overdue!).borderRadius).toBe('20px');
      expect(root.querySelector('.detail-context .detail-deadline')).toBeNull();
      component.selected!.recommendationOverdue = false; component.selected!.hrReviewOverdue = false; fixture.detectChanges();
      expect(deadline?.querySelector('.overdue')).toBeNull();
      expect(deadline?.textContent).toContain(hr ? '10 Nov 2026' : '26 Oct 2026');
    });
  }
  it('validates required recommendation and justification, but accepts explicit No Recommendation', async () => {
    await setup(); open(candidate({ appraisalId: null, status: null })); component.requestSubmit();
    expect(api.create).not.toHaveBeenCalled(); expect(component.showValidation).toBeTrue();
    component.recommendation = 'NO_RECOMMENDATION'; component.justification = 'No change is recommended.';
    component.requestSubmit(); expect(api.create).toHaveBeenCalled();
  });
  it('saves edits then submits with the fresh server token and server-calculated overdue warning', async () => {
    await setup(); open(candidate()); component.justification = 'Updated delivery';
    api.update.and.returnValue(of(appraisal({ justification: 'Updated delivery', lockVersion: 9, recommendationOverdue: true })));
    component.requestSubmit();
    expect(api.update).toHaveBeenCalledWith(10, jasmine.objectContaining({ expectedLockVersion: 1 }));
    expect(modal.confirm.calls.mostRecent().args[0]?.nzContent).toBe('ANNUAL_APPRAISAL.SUBMIT_LATE_HELP');
    expect(api.submit).not.toHaveBeenCalled(); answerConfirmation(true);
    expect(api.submit).toHaveBeenCalledOnceWith(10, { expectedLockVersion: 9 }); expect(component.readonly).toBeTrue();
  });
  it('cancel preserves the saved recommendation without submitting', async () => {
    await setup(); open(candidate()); component.justification = 'Updated';
    api.update.and.returnValue(of(appraisal({ justification: 'Updated', lockVersion: 2 })));
    component.requestSubmit(); expect(answerConfirmation(false)).toBeUndefined();
    expect(component.justification).toBe('Updated'); expect(api.submit).not.toHaveBeenCalled(); expect(component.confirming).toBeFalse();
  });
  it('does not submit after the backend removes SUBMIT or requires meaningful returned changes', async () => {
    await setup(); open(candidate()); component.justification = 'Whitespace';
    api.update.and.returnValue(of(appraisal({ status: 'RETURNED', allowedActions: ['SAVE_DRAFT'], submissionBlockers: ['Make and save a change'] })));
    component.requestSubmit(); expect(modal.confirm).not.toHaveBeenCalled(); expect(api.submit).not.toHaveBeenCalled();
    expect(component.error).toContain('Make and save a change');
  });
  it('keeps unsaved input and freezes writes on a 409 conflict', async () => {
    await setup(); open(candidate()); component.justification = 'Do not lose this';
    api.update.and.returnValue(throwError(() => ({ status: 409 })));
    component.saveDraft(); expect(component.justification).toBe('Do not lose this'); expect(component.accessChanged).toBeTrue();
    expect(component.readonly).toBeTrue(); component.saveDraft(); expect(api.update).toHaveBeenCalledTimes(1);
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const field = fixture.nativeElement.querySelector('#annual-justification') as HTMLTextAreaElement | null;
    expect(field?.value).toBe('Do not lose this'); expect(field?.disabled || field?.closest('fieldset')?.disabled).toBeTrue();
  });
  it('blocks duplicate saves, navigation and submission while a request is running', async () => {
    await setup(); open(candidate()); const response = new Subject<AnnualAppraisal>(); api.update.and.returnValue(response);
    component.justification = 'Changed'; component.saveDraft(); component.saveDraft(); component.requestSubmit();
    expect(component.canLeave()).toBeFalse(); expect(api.update).toHaveBeenCalledTimes(1); response.next(appraisal()); response.complete();
    expect(component.busy).toBeFalse();
  });
  it('discovers a competing first Draft through the scoped list rather than retrying duplicate creation', async () => {
    await setup(); open(candidate({ appraisalId: null, status: null }));
    component.justification = 'Unsaved draft'; api.create.and.returnValue(throwError(() => ({ status: 409 })));
    component.saveDraft(); expect(component.accessChanged).toBeTrue(); expect(component.justification).toBe('Unsaved draft');
    api.team.and.returnValue(of([candidate()]));
    const refresh = component.refreshDetail(); answerConfirmation(true); await refresh;
    expect(component.selected?.id).toBe(10); expect(api.team).toHaveBeenCalledTimes(2); expect(api.get).toHaveBeenCalledWith(10); expect(api.create).toHaveBeenCalledTimes(1);
  });
  it('cancelled navigation and refresh preserve the current employee, period and input', async () => {
    await setup(); open(); component.justification = 'Unsaved';
    const guard = component.canLeave(); answerConfirmation(false); expect(await guard).toBeFalse();
    const refresh = component.refreshDetail(); answerConfirmation(false); await refresh;
    expect(component.periodId).toBe(2); expect(component.candidate?.participantId).toBe(7);
    expect(component.justification).toBe('Unsaved'); expect(api.team).toHaveBeenCalledTimes(1);
    const accepted = component.refreshDetail(); answerConfirmation(true); await accepted;
    expect(component.justification).toBe('Consistent delivery'); expect(api.team).toHaveBeenCalledTimes(2);
  });
  it('loads history for the selected employee only and shares the response with the history disclosure', async () => {
    await setup(); open(candidate()); component.toggleHistory(); component.toggleHistory(); component.toggleHistory();
    expect(api.history).toHaveBeenCalledOnceWith(7);
  });
  it('does not block editing when history fails and ignores history from a previously selected employee', async () => {
    await setup(); const previous = new Subject<AnnualAppraisal[]>(); api.history.and.returnValue(previous);
    open(candidate()); expect(component.busy).toBeFalse(); expect(component.historyLoading).toBeTrue();
    api.get.and.returnValue(of(appraisal({ participantId: 8, employeeId: 'carol' }))); api.history.and.returnValue(throwError(() => ({ status: 503 })));
    open(candidate({ participantId: 8, employeeId: 'carol' })); expect(component.historyError).toBeTruthy(); expect(component.readonly).toBeFalse();
    previous.next([appraisal({ status: 'APPROVED' })]); previous.complete(); expect(component.history).toEqual([]);
    api.history.and.returnValue(of([appraisal({ status: 'APPROVED', employeeId: 'carol' })])); component.loadHistory(true);
    expect(component.historyError).toBe(''); expect(component.history.length).toBe(1);
  });
  it('keeps Closed and Pending submissions read-only regardless of local input', async () => {
    await setup(); api.periods.and.returnValue(of([{ ...period, status: 'CLOSED' }])); open(candidate({ appraisalId: null }));
    expect(component.readonly).toBeTrue(); component.saveDraft(); expect(api.create).not.toHaveBeenCalled();
    api.periods.and.returnValue(of([period])); api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: [] })));
    open(candidate()); expect(component.readonly).toBeTrue(); expect(component.frozen).toBeTrue();
  });
  it('loads HR submissions only, shows frozen scores, and honours server allowedActions', async () => {
    await setup(true); expect(api.team).not.toHaveBeenCalled();
    const pending = appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['RETURN'], sourceChanged: true });
    pending.readiness.performance.finalScore = 10; api.get.and.returnValue(of(pending)); open(candidate());
    expect(component.performance?.finalScore).toBe(80); expect(component.can('APPROVE')).toBeFalse(); expect(component.can('RETURN')).toBeTrue();
    component.confirmDecision('APPROVE'); expect(modal.confirm).not.toHaveBeenCalled();
  });
  it('validates override decisions and reasons, preserving input on forbidden review', async () => {
    await setup(true); api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', allowedActions: ['APPROVE', 'RETURN', 'OVERRIDE_AND_APPROVE'] })));
    open(candidate()); component.beginDecision('OVERRIDE_AND_APPROVE'); component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(modal.confirm).not.toHaveBeenCalled(); component.finalDecision = 'SALARY_INCREMENT'; component.reason = 'Reason';
    component.confirmDecision('OVERRIDE_AND_APPROVE'); expect(modal.confirm).not.toHaveBeenCalled();
    component.finalDecision = 'NO_RECOMMENDATION';
    api.overrideApprove.and.returnValue(throwError(() => ({ status: 403 }))); component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(modal.confirm).not.toHaveBeenCalled();
    expect(api.overrideApprove).toHaveBeenCalledWith(10, { expectedLockVersion: 1, finalDecision: 'NO_RECOMMENDATION', reason: 'Reason' });
    expect(component.reason).toBe('Reason'); expect(component.accessChanged).toBeTrue();
  });
  it('requires a return reason and sends the return without changing the recommendation', async () => {
    await setup(true); api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', allowedActions: ['RETURN'] })));
    open(candidate()); component.beginDecision('RETURN'); component.confirmDecision('RETURN'); expect(modal.confirm).not.toHaveBeenCalled();
    component.reason = 'Please explain'; api.returnForRevision.and.returnValue(of(appraisal({ status: 'RETURNED', allowedActions: [] })));
    component.confirmDecision('RETURN'); expect(modal.confirm).not.toHaveBeenCalled();
    expect(api.returnForRevision).toHaveBeenCalledWith(10, { expectedLockVersion: 1, reason: 'Please explain' });
    expect(component.reason).toBe(''); expect(component.selected?.status).toBe('RETURNED');
  });
  it('approves the submitted recommendation and switches to final read-only results', async () => {
    await setup(true);
    const pending = appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['APPROVE', 'RETURN', 'OVERRIDE_AND_APPROVE'] });
    api.get.and.returnValue(of(pending)); open(candidate());
    api.approve.and.returnValue(of({ ...pending, status: 'APPROVED', finalDecision: 'SALARY_INCREMENT', allowedActions: [], lockVersion: 2 }));
    component.beginDecision('APPROVE'); component.confirmDecision('APPROVE'); expect(modal.confirm).not.toHaveBeenCalled();
    expect(api.approve).toHaveBeenCalledOnceWith(10, { expectedLockVersion: 1 });
    expect(component.selected?.finalDecision).toBe('SALARY_INCREMENT'); expect(component.selected?.justification).toBe('Consistent delivery');
    expect(component.can('APPROVE')).toBeFalse(); expect(component.frozen).toBeTrue();
  });
  it('permits a late HR override with a different final decision and preserves the Superior recommendation', async () => {
    await setup(true);
    const pending = appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), hrReviewOverdue: true, allowedActions: ['OVERRIDE_AND_APPROVE'] });
    api.get.and.returnValue(of(pending)); open(candidate()); component.beginDecision('OVERRIDE_AND_APPROVE');
    component.finalDecision = 'NO_RECOMMENDATION'; component.reason = 'Budget constraints';
    api.overrideApprove.and.returnValue(of({ ...pending, status: 'APPROVED', finalDecision: 'NO_RECOMMENDATION', overrideReason: 'Budget constraints', allowedActions: [] }));
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(document.querySelector('.decision-modal')?.textContent).toContain('ANNUAL_APPRAISAL.REVIEW_LATE_HELP');
    component.confirmDecision('OVERRIDE_AND_APPROVE'); expect(modal.confirm).not.toHaveBeenCalled();
    expect(component.selected?.recommendation).toBe('SALARY_INCREMENT'); expect(component.selected?.finalDecision).toBe('NO_RECOMMENDATION');
    expect(component.selected?.overrideReason).toBe('Budget constraints');
  });
  it('keeps approval read-only, orders activity chronologically and does not expose actor IDs', async () => {
    await setup(true); api.get.and.returnValue(of(appraisal({ status: 'APPROVED', snapshot: performance(), allowedActions: [],
      activity: [2, 1, 3].map(id => ({ id, action: 'RETURNED', actorId: 'recorded-id', occurredAt: `2026-10-${id + 10}T10:00:00Z`,
        reason: 'Return ' + id, recommendation: 'SALARY_INCREMENT', justification: 'Saved text', finalDecision: null, snapshot: null })) })));
    open(candidate()); expect(component.events.map(e => e.id)).toEqual([1, 2, 3]);
    expect(component.actor(component.events[0])).toBe('ANNUAL_APPRAISAL.ACTIVITY_HR_REVIEWER');
    component.confirmDecision('APPROVE'); expect(modal.confirm).not.toHaveBeenCalled();
  });
  it('rejects malformed identifiers and participants outside the scoped queue without loading a record', async () => {
    await setup(); route.snapshot.paramMap = convertToParamMap({ reviewPeriodId: '2', participantId: '-1' }); params.next(route.snapshot.paramMap);
    expect(api.periods).not.toHaveBeenCalled(); expect(component.candidate).toBeNull();
    expect(component.error).toBe('ANNUAL_APPRAISAL.DETAIL_UNAVAILABLE');
    route.snapshot.paramMap = convertToParamMap({ reviewPeriodId: '2', participantId: '999' }); params.next(route.snapshot.paramMap);
    expect(api.get).not.toHaveBeenCalled(); expect(api.history).not.toHaveBeenCalled(); expect(component.candidate).toBeNull();
  });
  it('does not substitute another period or employee on mismatched responses and can retry loading', async () => {
    await setup(); api.get.and.returnValue(of(appraisal({ participantId: 999 }))); open();
    expect(component.candidate).toBeNull(); expect(api.history).not.toHaveBeenCalled();
    api.get.and.returnValue(of(appraisal())); await component.refreshDetail();
    expect(component.candidate?.participantId).toBe(7); expect(component.error).toBe(''); expect(api.create).not.toHaveBeenCalled();
  });
  it('renders four native radio choices on the page with no drawer or duplicate primary actions', async () => {
    await setup(); open(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const radios = root.querySelectorAll<HTMLInputElement>('.recommendation-options input[type="radio"]');
    expect(radios.length).toBe(4); radios[3].click(); fixture.detectChanges();
    expect([...radios].every(r => r.name === 'annual-recommendation')).toBeTrue();
    expect(component.recommendation).toBe('NO_RECOMMENDATION');
    radios[3].focus(); expect(document.activeElement).toBe(radios[3]);
    expect(getComputedStyle(radios[3].closest('label')!).outlineStyle).toBe('solid');
    expect(root.querySelector('.ant-drawer')).toBeNull();
    expect(root.querySelector('.recommendation-options')?.getAttribute('aria-labelledby')).toBe('annual-recommendation-label');
    expect([...root.querySelectorAll('button')].filter(b => b.textContent?.trim() === 'ANNUAL_APPRAISAL.SUBMIT').length).toBe(1);
  });
  it('keeps approved history collapsed and opens a read-only drawer without losing parent input', async () => {
    await setup(); api.history.and.returnValue(of([appraisal({ status: 'APPROVED', snapshot: performance(), finalDecision: 'SALARY_INCREMENT' })]));
    open(); fixture.detectChanges(); expect(component.historyVisible).toBeFalse(); expect(component.history.length).toBe(1);
    component.toggleHistory(); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('.appraisal-history tbody > tr').length).toBe(1);
    const history = root.querySelector('.appraisal-history')!;
    expect(history.compareDocumentPosition(root.querySelector('.recommendation-options')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    component.justification = 'Unsaved parent recommendation';
    const button = root.querySelector<HTMLButtonElement>('.appraisal-history tbody button')!;
    button.focus(); button.click(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const drawer = document.querySelector<HTMLElement>('.ant-drawer-body')!;
    expect(drawer).not.toBeNull(); expect(drawer.textContent).toContain('Consistent delivery');
    expect(drawer.textContent).toContain('ANNUAL_APPRAISAL.HR_FINAL_DECISION');
    expect(drawer.querySelector('textarea')).toBeNull(); expect(drawer.querySelector('canvas')).toBeNull();
    expect(root.querySelectorAll('.appraisal-history tbody > tr').length).toBe(1);
    document.querySelector<HTMLButtonElement>('.ant-drawer-close')!.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(component.historySelection).toBeNull(); expect(component.justification).toBe('Unsaved parent recommendation');
    expect(document.activeElement).toBe(button);
    expect(api.history).toHaveBeenCalledTimes(1);
  });

  it('loads performance history independently and ignores stale employee responses without blocking forms', async () => {
    await setup(); const previous = new Subject<AnnualPerformanceHistory[]>(); api.performanceHistory.and.returnValue(previous);
    open(); expect(api.performanceHistory).toHaveBeenCalledOnceWith(7); expect(component.performanceHistoryLoading).toBeTrue();
    api.get.and.returnValue(of(appraisal({ participantId: 8, employeeId: 'carol' })));
    api.performanceHistory.and.returnValue(throwError(() => ({ status: 503 })));
    open(candidate({ participantId: 8, employeeId: 'carol' }));
    expect(component.performanceHistoryError).toBeTruthy(); expect(component.readonly).toBeFalse(); expect(component.historyError).toBe('');
    previous.next([]); previous.complete(); expect(component.performanceHistoryError).toBeTruthy();
    api.performanceHistory.and.returnValue(of([])); component.loadPerformanceHistory();
    expect(component.performanceHistoryError).toBe(''); expect(component.performanceHistoryLoading).toBeFalse();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('keeps HR reasons intact when historical details close with Escape and contains the drawer on mobile', async () => {
    await setup(true);
    api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['RETURN'] })));
    api.history.and.returnValue(of([appraisal({ id: 11, status: 'APPROVED', snapshot: performance(), finalDecision: 'NO_RECOMMENDATION', overrideReason: 'Recorded HR reason' })]));
    open(); component.reason = 'Unsaved current HR reason'; component.toggleHistory(); fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('.appraisal-history tbody button') as HTMLButtonElement;
    button.focus(); button.click(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const overlay = document.querySelector<HTMLElement>('.ant-drawer')!;
    expect(overlay.textContent).toContain('Recorded HR reason');
    const frame = document.createElement('iframe'); frame.title = 'Responsive historical appraisal';
    frame.style.cssText = 'position:absolute;left:-10000px;top:0;height:1000px;border:0'; document.body.appendChild(frame);
    try {
      const doc = frame.contentDocument!;
      for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
      doc.body.style.margin = '0'; doc.body.appendChild(overlay.cloneNode(true));
      for (const width of [1440, 768, 360]) {
        frame.style.width = width + 'px';
        const wrapper = doc.querySelector<HTMLElement>('.ant-drawer-content-wrapper')!;
        expect(wrapper.getBoundingClientRect().width).withContext(String(width)).toBeLessThanOrEqual(width);
        const scores = doc.querySelector<HTMLElement>('.score-grid')!;
        expect(frame.contentWindow!.getComputedStyle(scores).gridTemplateColumns.split(' ').length).toBe(width < 600 ? 1 : 3);
      }
    } finally { frame.remove(); }
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    fixture.detectChanges(); await fixture.whenStable();
    expect(component.historySelection).toBeNull(); expect(component.reason).toBe('Unsaved current HR reason');
    expect(component.selected?.status).toBe('PENDING_REVIEW'); expect(api.returnForRevision).not.toHaveBeenCalled();
  });

  it('does not mix approved appraisal records with unapproved decisions or another employee', async () => {
    await setup(); api.history.and.returnValue(of([appraisal(), appraisal({ status: 'PENDING_REVIEW' }),
      appraisal({ id: 11, status: 'APPROVED', employeeId: 'carol' }), appraisal({ status: 'APPROVED' })]));
    open(); expect(component.history.map(row => row.id)).toEqual([10]);
    component.openHistory(11); expect(component.historySelection).toBeNull();
    component.openHistory(10); expect(component.historySelection).toBe(10);
    await component.refreshDetail(); expect(component.historySelection).toBeNull();
  });
  it('keeps details spacious and contained at desktop, tablet and narrow-mobile widths', async () => {
    await setup(); open(); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    const frame = document.createElement('iframe'); frame.title = 'Responsive appraisal detail';
    frame.style.cssText = 'position:absolute;left:-10000px;top:0;height:1000px;border:0'; document.body.appendChild(frame);
    try {
      const doc = frame.contentDocument!;
      for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
      doc.body.style.margin = '0'; doc.body.appendChild(fixture.nativeElement.cloneNode(true));
      for (const width of [1440, 768, 360]) {
        frame.style.width = width + 'px';
        const view = frame.contentWindow!;
        const page = doc.querySelector<HTMLElement>('.appraisal-detail-page')!;
        const radios = doc.querySelector<HTMLElement>('.recommendation-options')!;
        const scores = doc.querySelector<HTMLElement>('.score-grid')!;
        expect(view.getComputedStyle(radios).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(width < 600 ? 1 : 2);
        expect(view.getComputedStyle(scores).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(width < 600 ? 1 : 3);
        expect(page.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(page.clientWidth + 1);
        expect(view.getComputedStyle(doc.querySelector('.appraisal-detail-heading')!).position).toBe('sticky');
        const tools = doc.querySelector<HTMLElement>('.detail-tools')!;
        expect(tools).not.toBeNull();
        if (tools) expect(view.getComputedStyle(tools).alignItems).toBe(width > 900 ? 'flex-end' : 'flex-start');
        if (tools && width > 900) {
          const header = doc.querySelector<HTMLElement>('.appraisal-detail-heading')!;
          expect(Math.abs(tools.getBoundingClientRect().right - header.getBoundingClientRect().right)).toBeLessThanOrEqual(1);
        }
        expect(view.getComputedStyle(doc.querySelector('.detail-deadline')!).textAlign).toBe(width > 900 ? 'right' : 'left');
      }
    } finally { frame.remove(); }
  });

  it('places HR decisions after activity, with no duplicate header actions or inline form', async () => {
    await openHr(); const root: HTMLElement = fixture.nativeElement;
    const section = root.querySelector<HTMLElement>('.hr-decisions')!;
    expect(root.querySelectorAll('.detail-actions button').length).toBe(1);
    expect(section.querySelectorAll('.hr-action-button').length).toBe(3);
    expect(section.querySelectorAll('.decision-info').length).toBe(3);
    expect(root.querySelector('.appraisal-secondary')!.compareDocumentPosition(section) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(getComputedStyle(section).position).toBe('static');
    expect(root.querySelector('#annual-decision')).toBeNull(); expect(document.querySelector('.decision-modal')).toBeNull();
    expect(root.querySelector('.detail-deadline')).not.toBeNull();
  });
  it('shows only server-permitted decisions and retains blockers when none are permitted', async () => {
    await openHr({ allowedActions: ['RETURN'], reviewBlockers: ['Results changed'] });
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('.hr-decision-tile').length).toBe(1);
    expect(root.querySelector('.hr-decision-tile')?.getAttribute('data-action')).toBe('RETURN');
    component.beginDecision('APPROVE'); expect(component.decision).toBeNull();
    api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', allowedActions: [], reviewBlockers: ['Results changed'] })));
    await component.refreshDetail(); fixture.detectChanges();
    expect(root.querySelectorAll('.hr-decision-tile').length).toBe(0);
    expect(root.querySelector('.hr-decisions')?.textContent).toContain('ANNUAL_APPRAISAL.DECISION.UNAVAILABLE');
    expect(root.textContent).toContain('Results changed');
  });
  for (const status of ['RETURNED', 'APPROVED'] as const) {
    it(`keeps ${status} read-only even if stale allowedActions contain decisions`, async () => {
      await openHr({ status }); component.beginDecision('RETURN'); fixture.detectChanges();
      expect(component.decision).toBeNull(); expect(fixture.nativeElement.querySelector('.hr-decisions')).toBeNull();
    });
  }
  it('opens a single Approve modal with submitted scores, preserves zero, and confirms once', async () => {
    await openHr({ snapshot: { ...performance(), finalScore: 0 } });
    const trigger = fixture.nativeElement.querySelector('.hr-action-button[data-action="APPROVE"]') as HTMLButtonElement;
    trigger.focus(); trigger.click(); await renderModal();
    const dialog = document.querySelector<HTMLElement>('.decision-modal')!;
    expect(dialog.textContent).toContain('0.00 / 100'); expect(dialog.textContent).toContain('ANNUAL_APPRAISAL.RECOMMENDATIONS.SALARY_INCREMENT');
    expect(dialog.querySelector('input, textarea, nz-select')).toBeNull();
    const cancel = document.querySelector<HTMLButtonElement>('.decision-cancel')!;
    const overlay = document.querySelector<HTMLElement>('.ant-modal-wrap')!;
    expect(overlay.contains(document.activeElement)).toBeTrue();
    cancel.focus(); expect(document.activeElement).toBe(cancel); expect(getComputedStyle(cancel).outlineStyle).toBe('solid');
    expect(document.querySelectorAll('.ant-modal').length).toBe(1);
    const response = new Subject<AnnualAppraisal>(); api.approve.and.returnValue(response);
    const confirm = document.querySelector<HTMLButtonElement>('.decision-confirm')!;
    confirm.click(); fixture.detectChanges(); confirm.click(); component.confirmDecision('APPROVE'); component.cancelDecision();
    expect(api.approve).toHaveBeenCalledOnceWith(10, { expectedLockVersion: 1 });
    expect(component.decision).toBe('APPROVE'); expect(component.canLeave()).toBeFalse();
    expect(confirm.disabled).toBeTrue(); expect(document.querySelector('.ant-modal-close')).toBeNull();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    expect(component.decision).toBe('APPROVE');
    response.next(appraisal({ status: 'APPROVED', snapshot: { ...performance(), finalScore: 0 }, allowedActions: [] })); response.complete();
    await renderModal(); expect(component.decision).toBeNull(); expect(component.selected?.snapshot?.finalScore).toBe(0);
    expect(modal.confirm).not.toHaveBeenCalled(); expect(fixture.nativeElement.querySelector('.hr-decisions')).toBeNull();
  });
  it('does not replace a missing submitted score with a current calculated score', async () => {
    await openHr({ snapshot: null }); component.beginDecision('APPROVE'); await renderModal();
    expect(document.querySelector('.decision-score')?.textContent).toBe('ANNUAL_APPRAISAL.PENDING');
    expect(document.querySelector('.decision-score')?.textContent).not.toContain('80');
  });
  it('disables the original decision and validates both reason rules without a new minimum', async () => {
    await openHr(); component.beginDecision('OVERRIDE_AND_APPROVE'); await renderModal();
    const input = document.querySelector<HTMLInputElement>('#annual-final-decision')!;
    expect(document.querySelector('.ant-modal-wrap')!.contains(document.activeElement)).toBeTrue();
    input.click(); await renderModal();
    const original = [...document.querySelectorAll('.ant-select-item-option')].find(e => e.textContent?.includes('SALARY_INCREMENT'));
    expect(original?.classList.contains('ant-select-item-option-disabled')).toBeTrue();
    component.finalDecision = 'PROMOTION'; component.reason = ' '; component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(api.overrideApprove).not.toHaveBeenCalled(); expect(component.reasonError).toBe('ANNUAL_APPRAISAL.REQUIRED');
    component.reason = 'a'.repeat(10001); component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(api.overrideApprove).not.toHaveBeenCalled(); expect(component.reasonError).toBe('ANNUAL_APPRAISAL.TOO_LONG');
    component.reason = 'OK'; const response = new Subject<AnnualAppraisal>(); api.overrideApprove.and.returnValue(response);
    component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(api.overrideApprove).toHaveBeenCalledOnceWith(10, { expectedLockVersion: 1, reason: 'OK', finalDecision: 'PROMOTION' });
    response.next(appraisal({ status: 'APPROVED', allowedActions: [], finalDecision: 'PROMOTION', snapshot: performance() })); response.complete();
    expect(component.selected?.recommendation).toBe('SALARY_INCREMENT'); expect(component.selected?.snapshot?.finalScore).toBe(80);
    expect(modal.confirm).not.toHaveBeenCalled();
  });
  for (const method of ['cancel', 'close', 'escape'] as const) {
    it(`discards only modal input on ${method}, preserves the page, and restores focus`, async () => {
      await openHr(); const trigger = fixture.nativeElement.querySelector('.hr-action-button[data-action="RETURN"]') as HTMLButtonElement;
      trigger.focus(); trigger.click(); await renderModal();
      component.reason = 'Modal-only notes'; fixture.detectChanges();
      expect(document.querySelector('.ant-modal-wrap')!.contains(document.activeElement)).toBeTrue();
      const reason = document.querySelector<HTMLTextAreaElement>('#annual-decision-reason')!;
      reason.focus(); expect(document.activeElement).toBe(reason); expect(getComputedStyle(reason).outlineStyle).toBe('solid');
      document.querySelector<HTMLElement>('.ant-modal-wrap')!.click(); fixture.detectChanges();
      expect(component.decision).toBe('RETURN');
      if (method === 'cancel') document.querySelector<HTMLButtonElement>('.decision-cancel')!.click();
      else if (method === 'close') document.querySelector<HTMLButtonElement>('.ant-modal-close')!.click();
      else document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
      await renderModal(); expect(component.decision).toBeNull(); expect(component.reason).toBe('');
      expect(component.selected?.status).toBe('PENDING_REVIEW'); expect(component.justification).toBe('Consistent delivery');
      expect(api.returnForRevision).not.toHaveBeenCalled(); expect(document.activeElement).toBe(trigger);
    });
  }
  it('preserves the decision modal and input when navigation or refresh is cancelled', async () => {
    await openHr(); component.beginDecision('RETURN'); component.reason = 'Please clarify'; await renderModal();
    const guard = component.canLeave(); answerConfirmation(false); expect(await guard).toBeFalse();
    const refresh = component.refreshDetail(); answerConfirmation(false); await refresh; await renderModal();
    expect(component.decision).toBe('RETURN'); expect(component.reason).toBe('Please clarify');
    expect(document.querySelector<HTMLTextAreaElement>('#annual-decision-reason')?.value).toBe('Please clarify');
    expect(component.selected?.employeeId).toBe('bob'); expect(api.get).toHaveBeenCalledTimes(1);
  });
  for (const status of [400, 403, 409, 500]) {
    it(`keeps failed decision input in the modal after HTTP ${status}, without retrying`, async () => {
      await openHr(); component.beginDecision('RETURN'); component.reason = 'Please clarify';
      api.returnForRevision.and.returnValue(throwError(() => ({ status })));
      component.confirmDecision('RETURN'); await renderModal();
      expect(component.reason).toBe('Please clarify'); expect(component.decision).toBe('RETURN'); expect(component.error).toBe('');
      expect(document.querySelector('.decision-modal [role="alert"]')?.textContent).toContain(status === 403 || status === 409 ? 'ANNUAL_APPRAISAL.DECISION.ACCESS_CHANGED' : 'ANNUAL_APPRAISAL.ERROR');
      expect(document.querySelector<HTMLButtonElement>('.decision-confirm')?.disabled).toBe(status === 403 || status === 409);
      expect(api.returnForRevision).toHaveBeenCalledTimes(1);
      if (status === 403 || status === 409) {
        component.confirmDecision('RETURN'); expect(api.returnForRevision).toHaveBeenCalledTimes(1);
        component.cancelDecision(); await component.refreshDetail(); expect(component.accessChanged).toBeFalse();
      }
    });
  }
  it('offers clickable, keyboard-focusable info popovers without permanent descriptions', async () => {
    await openHr(); const root: HTMLElement = fixture.nativeElement;
    const info = root.querySelector<HTMLButtonElement>('.decision-info')!;
    expect(info.getAttribute('aria-label')).toContain('ANNUAL_APPRAISAL.DECISION.ACTION_INFO');
    expect(root.querySelector('.hr-decision-tiles')?.textContent).not.toContain('ANNUAL_APPRAISAL.DECISION.INFO.');
    info.focus(); expect(document.activeElement).toBe(info); expect(getComputedStyle(info).outlineStyle).toBe('solid');
    info.click(); await renderModal();
    expect(info.getAttribute('aria-expanded')).toBe('true');
    expect(document.querySelector('.ant-popover-inner-content')?.textContent).toContain('ANNUAL_APPRAISAL.DECISION.INFO.APPROVE');
    info.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); await renderModal();
    expect(info.getAttribute('aria-expanded')).toBe('false');
    expect(document.querySelector('.ant-popover-inner-content')).toBeNull(); expect(api.approve).not.toHaveBeenCalled();
  });
  it('hides only duplicate latest genuine submission details in HR, retaining earlier snapshots and all events', async () => {
    const events = [submission(1), submission(2, 'RETURNED'), submission(3, 'RESUBMITTED'), submission(4, 'RETURNED'), submission(5, 'RESUBMITTED'), submission(6, 'OVERRIDDEN')];
    const { finalScore, ...rest } = performance(); events[4].snapshot = { finalScore: 80.00, ...rest };
    events[4].occurredAt = '2026-10-19T12:00:00Z';
    await openHr({ status: 'APPROVED', activity: events });
    expect(component.showRecordedSubmission(events[0])).toBeTrue();
    expect(events.slice(1).map(e => component.showRecordedSubmission(e))).toEqual([false, true, false, false, false]);
    component.activityVisible = true; fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.activity-timeline > li').length).toBe(6);
    expect(fixture.nativeElement.querySelectorAll('.activity-timeline button').length).toBe(2);
    expect(component.selected?.activity).toEqual(events);
  });
  for (const changes of ['recommendation', 'justification', 'both', 'neither']) {
    it(`integrates ${changes} resubmission changes without repeating current fields or the audit timeline`, async () => {
      const value = resubmittedAppraisal();
      if (changes === 'justification' || changes === 'neither') value.recommendation = 'SALARY_INCREMENT';
      if (changes === 'recommendation' || changes === 'neither') value.justification = 'Consistent delivery';
      value.activity[3] = { ...value.activity[3], recommendation: value.recommendation, justification: value.justification };
      const recorded = structuredClone(value); await openHr(value);
      const root: HTMLElement = fixture.nativeElement; const card = root.querySelector('.appraisal-recommendation')!;
      const comparison = card.querySelector('.return-comparison')!;
      const recommendationChanged = changes === 'recommendation' || changes === 'both';
      const justificationChanged = changes === 'justification' || changes === 'both';
      expect(comparison.querySelector('h4')?.textContent).toBe('ANNUAL_APPRAISAL.RETURN_COMPARISON.TITLE');
      expect(comparison.querySelector('.previous-return-reason p')?.textContent).toBe(value.activity[1].reason);
      expect(root.textContent).not.toContain(value.latestReturnReason!);
      expect(card.querySelector('.recommendation-comparison') !== null).toBe(recommendationChanged);
      expect(card.querySelector('.justification-comparison') !== null).toBe(justificationChanged);
      expect(card.querySelectorAll('.summary-grid > div').length).toBe(recommendationChanged ? 1 : 2);
      expect(card.querySelector('.justification-heading') !== null).toBe(!justificationChanged);
      expect(comparison.textContent?.includes('ANNUAL_APPRAISAL.RETURN_COMPARISON.UNCHANGED')).toBe(changes === 'neither');
      if (recommendationChanged) {
        expect([...comparison.querySelectorAll('.recommendation-comparison dd')].map(node => node.textContent))
          .toEqual(['ANNUAL_APPRAISAL.RECOMMENDATIONS.SALARY_INCREMENT', 'ANNUAL_APPRAISAL.RECOMMENDATIONS.PROMOTION']);
      }
      if (justificationChanged) {
        const details = comparison.querySelector<HTMLDetailsElement>('details')!;
        expect(details.open).toBeFalse();
        expect([...details.querySelectorAll('dd')].map(node => node.textContent)).toEqual(['Consistent delivery', 'Updated delivery evidence']);
        expect([...card.querySelectorAll('.preserve-lines')].filter(node => node.textContent === value.justification).length).toBe(1);
      }
      expect(card.querySelector('.summary-grid')?.textContent).toContain('ANNUAL_APPRAISAL.SUBMITTED_ON');
      expect(card.querySelector('.summary-grid .status.overdue')?.textContent).toBe('ANNUAL_APPRAISAL.LATE');
      expect(root.querySelector('.activity-timeline')).toBeNull(); expect(root.querySelectorAll('.hr-action-button').length).toBe(3);
      component.activityVisible = true; fixture.detectChanges();
      expect(root.querySelectorAll('.activity-timeline > li').length).toBe(3);
      component.showAllActivity = true; fixture.detectChanges();
      expect(root.querySelectorAll('.activity-timeline > li').length).toBe(4);
      expect(root.querySelector('.event-reason')?.textContent).toBe(value.activity[1].reason);
      expect(component.showRecordedSubmission(value.activity[0])).toBeTrue();
      expect(component.showRecordedSubmission(value.activity[3])).toBeFalse();
      expect(component.selected).toEqual(recorded); expect(api.update).not.toHaveBeenCalled(); expect(api.approve).not.toHaveBeenCalled();
    });
  }
  for (const status of ['RETURNED', 'APPROVED'] as const) {
    it(`keeps ${status} HR views free of the resubmission comparison`, async () => {
      await openHr({ ...resubmittedAppraisal(), status });
      expect(fixture.nativeElement.querySelector('.return-comparison')).toBeNull();
      expect(status === 'APPROVED' ? fixture.nativeElement.querySelector('.appraisal-decision-summary') : fixture.nativeElement.querySelector('.current-return-reason')).not.toBeNull();
    });
  }
  it('keeps Superior resubmission views unchanged', async () => {
    await setup(); api.get.and.returnValue(of(resubmittedAppraisal())); open(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.return-comparison')).toBeNull();
  });
  it('keeps initial submissions unchanged even if a current return-reason field exists', async () => {
    await openHr({ latestReturnReason: 'Not proof of a resubmission', activity: [submission(1)] });
    expect(fixture.nativeElement.querySelector('.return-comparison')).toBeNull();
    expect(fixture.nativeElement.querySelector('.justification-heading')).not.toBeNull();
  });
  it('retains accurate comparison access in a Closed period without enabling HR writes', async () => {
    await openHr({ ...resubmittedAppraisal(), reviewPeriodStatus: 'CLOSED', allowedActions: [] });
    expect(fixture.nativeElement.querySelector('.return-comparison')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.hr-action-button')).toBeNull();
    component.beginDecision('APPROVE'); expect(component.decision).toBeNull(); expect(api.approve).not.toHaveBeenCalled();
  });
  it('shows unavailable history honestly while preserving current content and authorised approval', async () => {
    const value = resubmittedAppraisal(); value.activity[1].reason = null; await openHr(value);
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.comparison-unavailable')?.textContent).toContain('ANNUAL_APPRAISAL.RETURN_COMPARISON.UNAVAILABLE');
    expect(root.querySelector('.return-comparison-grid')).toBeNull();
    expect(root.querySelector('.justification-heading + p')?.textContent).toBe(value.justification);
    expect(root.querySelectorAll('.hr-action-button').length).toBe(3);
    api.approve.and.returnValue(of({ ...value, status: 'APPROVED', allowedActions: [] }));
    component.beginDecision('APPROVE'); component.confirmDecision('APPROVE'); fixture.detectChanges();
    expect(api.approve).toHaveBeenCalledOnceWith(10, { expectedLockVersion: value.lockVersion });
    expect(root.querySelector('.return-comparison')).toBeNull();
  });
  it('supports full-text disclosure, keyboard focus and cancellation without losing HR input or altering history', async () => {
    const value = resubmittedAppraisal(); value.justification = 'Full updated justification\n' + '<script>not executable</script> '.repeat(150);
    value.activity[3].justification = value.justification; await openHr(value);
    const details = fixture.nativeElement.querySelector('.justification-comparison details') as HTMLDetailsElement;
    const summary = details.querySelector('summary')!;
    summary.focus(); expect(document.activeElement).toBe(summary); expect(getComputedStyle(summary).outlineStyle).toBe('solid');
    summary.click(); fixture.detectChanges(); expect(details.open).toBeTrue();
    expect(details.querySelectorAll('dd')[1].textContent).toBe(value.justification); expect(details.querySelector('script')).toBeNull();
    component.reason = 'Keep this HR reason';
    const leaving = component.canLeave(); answerConfirmation(false); expect(await leaving).toBeFalse();
    expect(component.reason).toBe('Keep this HR reason'); expect(details.open).toBeTrue();
    const page = fixture.nativeElement.querySelector('.appraisal-detail-page') as HTMLElement;
    for (const width of [1440, 768, 360]) {
      const layout = reviewLayout(page, width);
      try {
        expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width);
        expect(layout.window.getComputedStyle(layout.root.querySelector('.return-comparison-grid')!).gridTemplateColumns.split(' ').length).toBe(width <= 600 ? 1 : 2);
      } finally { layout.remove(); }
    }
    expect(component.selected?.activity).toEqual(value.activity); expect(api.update).not.toHaveBeenCalled(); expect(api.returnForRevision).not.toHaveBeenCalled();
  });
  it('hides an identical initial submission but retains differences within a nested snapshot', async () => {
    const event = submission(1);
    const snapshot: AnnualAppraisalSnapshot = { ...performance(), attitude: {
      assessmentId: 1, configurationId: 2, format: 'OTHERS', status: 'REVIEWED', reviewedAt: '2026-10-19T10:00:00Z',
      criteria: [{ criterionId: 3, name: 'Teamwork', description: null, superiorPoint: 4 }]
    } };
    event.snapshot = structuredClone(snapshot);
    await openHr({ activity: [event], snapshot });
    expect(component.showRecordedSubmission(event)).toBeFalse();
    snapshot.attitude!.criteria[0].superiorPoint = 3;
    expect(component.showRecordedSubmission(event)).toBeTrue();
  });
  it('keeps latest and repeated submissions when returned, unmatched, missing or uncertain', async () => {
    const first = submission(1), latest = submission(3, 'RESUBMITTED');
    await openHr({ status: 'RETURNED', activity: [first, latest] });
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    component.selected!.status = 'PENDING_REVIEW'; component.selected!.justification = 'A later edit';
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    component.selected!.justification = latest.justification; component.selected!.snapshot = { ...performance(), finalScore: 81 };
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    component.selected!.snapshot = performance(); component.selected!.activity.push({ ...submission(5, 'RESUBMITTED'), snapshot: null });
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    component.selected!.activity.pop(); component.selected!.submittedAt = null;
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    component.selected!.submittedAt = '2026-10-20T10:00:00Z'; latest.snapshot!.employeeId = 'carol';
    expect(component.showRecordedSubmission(latest)).toBeTrue();
    latest.snapshot = null; expect(component.showRecordedSubmission(latest)).toBeTrue();
  });
  it('offers Superior submission details only for genuine submissions, without duplicating other event snapshots', async () => {
    await setup(); const events = ['SUBMITTED', 'RESUBMITTED', 'RETURNED', 'APPROVED', 'OVERRIDDEN', 'DRAFT_SAVED'] as const;
    api.get.and.returnValue(of(appraisal({ activity: events.map((action, i) => submission(i + 1, action)) })));
    open(); expect(component.events.map(event => component.showRecordedSubmission(event))).toEqual([true, true, false, false, false, false]);
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('.hr-decisions')).toBeNull();
  });
  for (const hr of [false, true]) {
    it(`shows meaningful ${hr ? 'HR' : 'Superior'} activity by default and retains Drafts behind Show All Activity`, async () => {
      await setup(hr);
      const activity = [submission(1, 'DRAFT_CREATED'), submission(2), { ...submission(3, 'RETURNED'), reason: 'Clarify your recommendation' },
        submission(4, 'DRAFT_SAVED'), submission(5, 'RESUBMITTED'), { ...submission(6, 'OVERRIDDEN'), reason: 'Recorded HR reason' }];
      const recorded = structuredClone(activity);
      api.get.and.returnValue(of(appraisal({ status: 'APPROVED', snapshot: performance(), submittedAt: '2026-10-20T12:00:00Z',
        allowedActions: [], activity: [...activity].reverse() }))); open(); fixture.detectChanges();
      const root: HTMLElement = fixture.nativeElement;
      expect(component.activityVisible).toBeFalse(); expect(component.showAllActivity).toBeFalse();
      root.querySelector<HTMLButtonElement>('.appraisal-secondary > button')!.click(); fixture.detectChanges();
      expect(component.visibleEvents.map(event => event.id)).toEqual([2, 3, 5, 6]);
      expect(root.querySelectorAll('.activity-timeline > li').length).toBe(4);
      expect(root.querySelector('.activity-submission')).toBeNull();
      const toggle = root.querySelector<HTMLButtonElement>('.activity-controls button')!;
      expect(toggle.textContent).toContain('ANNUAL_APPRAISAL.SHOW_ALL_ACTIVITY');
      toggle.focus(); expect(document.activeElement).toBe(toggle); expect(toggle.getAttribute('aria-pressed')).toBe('false');
      toggle.click(); fixture.detectChanges();
      expect(component.visibleEvents.map(event => event.id)).toEqual([1, 2, 3, 4, 5, 6]);
      expect(toggle.getAttribute('aria-pressed')).toBe('true'); expect(root.querySelectorAll('.activity-timeline > li').length).toBe(6);
      expect(root.querySelectorAll('.activity-submission-toggle').length).toBe(1);
      expect(root.querySelector('.activity-timeline')?.textContent).toContain('Recorded HR reason');
      toggle.click(); fixture.detectChanges(); expect(component.visibleEvents.map(event => event.id)).toEqual([2, 3, 5, 6]);
      expect(activity).toEqual(recorded); expect(component.selected?.activity).toEqual([...recorded].reverse());
      expect(api.update).not.toHaveBeenCalled(); expect(api.approve).not.toHaveBeenCalled();
    });
    it(`opens only recorded text before optional performance for ${hr ? 'HR' : 'Superior'} historical submissions`, async () => {
      await setup(hr);
      const event = { ...submission(1), justification: 'Recorded text\n<script>plain text only</script>' };
      const activity = [event, { ...submission(2, 'RETURNED'), reason: 'More evidence' }, { ...submission(3, 'RESUBMITTED'), justification: 'Revised recommendation' }];
      api.get.and.returnValue(of(appraisal({ status: 'APPROVED', snapshot: performance(), submittedAt: '2026-10-20T12:00:00Z',
        justification: 'Revised recommendation', allowedActions: [], activity }))); open(); component.toggleActivity(); fixture.detectChanges();
      const root: HTMLElement = fixture.nativeElement;
      const toggle = root.querySelector<HTMLButtonElement>('.activity-submission-toggle')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      toggle.focus(); expect(document.activeElement).toBe(toggle);
      toggle.click(); fixture.detectChanges();
      const detail = root.querySelector<HTMLElement>('.activity-submission')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(detail.querySelectorAll('dd')[0].textContent).toBe('ANNUAL_APPRAISAL.RECOMMENDATIONS.SALARY_INCREMENT');
      expect(detail.querySelectorAll('dd')[1].textContent).toBe(event.justification);
      expect(detail.querySelector('script')).toBeNull();
      expect(detail.textContent).not.toContain('ANNUAL_APPRAISAL.HR_FINAL_DECISION');
      expect(detail.querySelector('app-annual-appraisal-performance')).toBeNull();
      const performanceToggle = detail.querySelector<HTMLButtonElement>('.activity-performance-toggle')!;
      performanceToggle.click(); fixture.detectChanges();
      const preserved = detail.querySelector('app-annual-appraisal-performance'); expect(preserved).not.toBeNull();
      const view = fixture.debugElement.queryAll(By.directive(AnnualAppraisalPerformanceComponent))
        .map(element => element.componentInstance as AnnualAppraisalPerformanceComponent).find(value => value.contextId === 'event-1');
      expect(view?.performance).toBe(event.snapshot!); expect(view?.frozen).toBeTrue();
      performanceToggle.click(); fixture.detectChanges(); expect(detail.querySelector('app-annual-appraisal-performance')).toBeNull();
      toggle.click(); fixture.detectChanges(); toggle.click(); fixture.detectChanges();
      expect(root.querySelector('.activity-performance')).toBeNull();
      component.toggleActivity(); component.toggleActivity(); fixture.detectChanges();
      expect(root.querySelector('.activity-submission')).toBeNull();
      for (const width of [1440, 768, 360]) {
        const layout = reviewLayout(root.querySelector('.appraisal-detail-page')!, width);
        try { expect(layout.root.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width); } finally { layout.remove(); }
      }
      expect(component.selected?.activity).toEqual(activity); expect(api.update).not.toHaveBeenCalled();
    });
  }
  it('uses available names or human-readable actor roles without rendering UUIDs or guessing identities', async () => {
    const uuid = 'd4d4d4d4-0000-0000-0000-000000000001';
    await openHr({ activity: [
      { ...submission(1), actorId: 'alice' },
      { ...submission(2, 'RETURNED'), actorId: uuid, reason: 'Clarify' },
      { ...submission(3, 'RESUBMITTED'), actorId: 'david' },
      { ...submission(4, 'APPROVED'), actorId: 'bob' }
    ] });
    component.toggleActivity(); fixture.detectChanges();
    expect([...fixture.nativeElement.querySelectorAll('.event-actor')].map(node => (node as HTMLElement).textContent))
      .toEqual(['ANNUAL_APPRAISAL.ACTIVITY_SUPERIOR', 'ANNUAL_APPRAISAL.ACTIVITY_HR_REVIEWER', 'ANNUAL_APPRAISAL.YOU', 'Bob']);
    expect(fixture.nativeElement.textContent).not.toContain(uuid);
    expect(fixture.nativeElement.querySelector('.event-actor[title]')).toBeNull();
  });
  it('allows recorded text without a performance snapshot and resets activity choices on refresh', async () => {
    const event = { ...submission(1), snapshot: null };
    await openHr({ activity: [submission(2, 'DRAFT_SAVED'), event] });
    component.toggleActivity(); component.showAllActivity = true; component.toggleSubmission(1); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.activity-submission')?.textContent).toContain('Consistent delivery');
    expect(fixture.nativeElement.querySelector('.activity-performance-toggle')).toBeNull();
    await component.refreshDetail(); fixture.detectChanges();
    expect(component.activityVisible).toBeFalse(); expect(component.showAllActivity).toBeFalse(); expect(component.expandedEvent).toBeNull();
    expect(component.expandedPerformanceEvent).toBeNull(); expect(api.update).not.toHaveBeenCalled();
  });
  it('explains Draft-only activity without hiding the optional full timeline', async () => {
    await setup(); api.get.and.returnValue(of(appraisal({ activity: [submission(1, 'DRAFT_CREATED')] }))); open(); component.toggleActivity(); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.activity-empty')?.textContent).toContain('ANNUAL_APPRAISAL.NO_KEY_ACTIVITY');
    root.querySelector<HTMLButtonElement>('.activity-controls button')!.click(); fixture.detectChanges();
    expect(root.querySelector('.activity-empty')).toBeNull(); expect(root.querySelector('.activity-timeline')?.textContent).toContain('ANNUAL_APPRAISAL.EVENT.DRAFT_CREATED');
  });
  it('wraps decision tiles and modals at desktop, tablet and narrow-mobile sizes with readable action colours', async () => {
    await openHr(); component.beginDecision('OVERRIDE_AND_APPROVE'); await renderModal();
    const frame = document.createElement('iframe'); frame.title = 'Responsive HR decisions';
    frame.style.cssText = 'position:absolute;left:-10000px;top:0;height:900px;border:0'; document.body.appendChild(frame);
    try {
      const doc = frame.contentDocument!;
      for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
      doc.body.style.margin = '0'; doc.body.appendChild(fixture.nativeElement.cloneNode(true));
      doc.body.appendChild(document.querySelector('.ant-modal-wrap')!.cloneNode(true));
      for (const width of [1440, 768, 360]) {
        frame.style.width = width + 'px'; const view = frame.contentWindow!;
        expect(view.getComputedStyle(doc.querySelector('.hr-decision-tiles')!).gridTemplateColumns.split(' ').length).toBe(width > 900 ? 3 : 1);
        expect(doc.querySelector<HTMLElement>('.appraisal-detail-page')!.scrollWidth).toBeLessThanOrEqual(width);
        expect(doc.querySelector('.ant-modal')!.getBoundingClientRect().width).toBeLessThanOrEqual(width);
        expect(view.getComputedStyle(doc.querySelector('.decision-modal')!).overflowY).toBe('auto');
        expect(view.getComputedStyle(doc.querySelector('.decision-summary')!).gridTemplateColumns.split(' ').length).toBe(width < 600 ? 1 : 2);
      }
    } finally { frame.remove(); }
    component.cancelDecision(); await renderModal();
    const luminance = (colour: string) => {
      const values = colour.match(/\d+/g)!.slice(0, 3).map(v => Number(v) / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
      return .2126 * values[0] + .7152 * values[1] + .0722 * values[2];
    };
    for (const button of fixture.nativeElement.querySelectorAll('.hr-action-button')) {
      const style = getComputedStyle(button); const text = luminance(style.color), background = luminance(style.backgroundColor);
      expect((Math.max(text, background) + .05) / (Math.min(text, background) + .05)).toBeGreaterThanOrEqual(4.5);
    }
  });
  for (const status of ['RETURNED', 'APPROVED'] as const) {
    it(`contains long ${status === 'RETURNED' ? 'return reasons' : 'decision summaries'} across desktop, tablet and mobile`, async () => {
      const text = 'Recorded explanation\n' + 'Detailed feedback. '.repeat(40) + 'A'.repeat(250);
      await openHr({ status, latestReturnReason: text, justification: text, overrideReason: text,
        finalDecision: 'NO_RECOMMENDATION', allowedActions: [] });
      if (status === 'RETURNED') expect(getComputedStyle(fixture.nativeElement.querySelector('.current-return-reason')).borderLeftWidth).toBe('4px');
      const frame = document.createElement('iframe'); frame.title = 'Responsive appraisal hierarchy';
      frame.style.cssText = 'position:absolute;left:-10000px;top:0;height:1000px;border:0'; document.body.appendChild(frame);
      try {
        const doc = frame.contentDocument!;
        for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
        const theme = getComputedStyle(document.documentElement);
        for (const name of Array.from(theme)) {
          if (name.startsWith('--tbm-')) doc.documentElement.style.setProperty(name, theme.getPropertyValue(name));
        }
        doc.body.style.margin = '0'; doc.body.appendChild(fixture.nativeElement.cloneNode(true));
        for (const width of [1440, 768, 360]) {
          frame.style.width = width + 'px'; const view = frame.contentWindow!;
          const page = doc.querySelector<HTMLElement>('.appraisal-detail-page')!;
          expect(page.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(page.clientWidth + 1);
          const prose = doc.querySelector<HTMLElement>(status === 'RETURNED' ? '.current-return-reason .preserve-lines' : '.recorded-justification')!;
          expect(prose.textContent).toBe(text); expect(view.getComputedStyle(prose).whiteSpace).toBe('pre-wrap');
          expect(prose.scrollWidth).toBeLessThanOrEqual(prose.clientWidth + 1);
          if (status === 'APPROVED') {
            const grid = doc.querySelector<HTMLElement>('.appraisal-decision-columns')!;
            expect(view.getComputedStyle(grid).gridTemplateColumns.split(' ').length).toBe(width < 600 ? 1 : 2);
          } else {
            expect(view.getComputedStyle(doc.querySelector('.current-return-reason')!).borderLeftWidth).toBe('4px');
          }
        }
      } finally { frame.remove(); }
    });
  }
});
