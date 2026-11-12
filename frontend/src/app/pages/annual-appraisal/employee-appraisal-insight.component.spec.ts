import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpHeaders } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { EmployeeInsight, EmployeeInsightLimits, EmployeeInsightState, EmployeeInsightResult } from '../../models/appraisal-insight.model';
import { AppraisalInsightService } from '../../services/appraisal-insight.service';
import { AuthService } from '../../services/auth.service';
import { EmployeeAppraisalInsightComponent } from './employee-appraisal-insight.component';
import { reviewLayout } from '../kpi-plan/review-navigation.test-layout';

const ID = '00000000-0000-4000-8000-000000000001', OTHER = '00000000-0000-4000-8000-000000000002';
function insight(patch: Partial<EmployeeInsight> = {}): EmployeeInsight {
  const statement = { text: 'KPI performance is 80.25%. Rating 4 / 5. <img src=x onerror=alert(1)>\nPromotion readiness has insufficient evidence.', evidenceIds: ['S1'] };
  return { id: ID, version: 1, status: 'COMPLETED', outdated: false, fictional: true, model: 'test', templateVersion: 'v1',
    createdAt: new Date().toISOString(), completedAt: '2026-11-07T02:00:00Z', retryAt: null, errorCode: null,
    evidence: { periodName: 'Selected fictional period', startDate: '2022-01-01', endDate: '2022-12-31', requiredResultsComplete: true,
      frequency: 'QUARTERLY', consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50,
      scores: { kpi: 0, attitude: null, finalAppraisal: 80.25 }, facts: [], history: [], informationGaps: [] },
    summary: { performanceSummary: [statement], keyStrengths: [statement], areasForImprovement: [statement], performanceTrend: [statement],
      appraisalConsiderations: { promotionDiscussion: statement, salaryIncrementDiscussion: statement }, informationGaps: [statement] },
    disclaimer: 'AI is advisory. Review recorded performance before deciding.', ...patch };
}
function limits(patch: Partial<EmployeeInsightLimits> = {}): EmployeeInsightLimits {
  return { serverTime: new Date().toISOString(), dailyAllowance: 5, remainingAttempts: 5,
    resetAt: new Date(Date.now() + 86400000).toISOString(), cooldownUntil: null, providerRetryAt: null, ...patch };
}
function state(patch: Partial<EmployeeInsightState> = {}): EmployeeInsightState {
  return { participantId: 7, eligible: true, unavailableReason: null, currentInsight: null, latestAttempt: null, history: [], limits: limits(), ...patch };
}
function result(generation = insight(), disposition: EmployeeInsightResult['disposition'] = 'NEWLY_QUEUED'): EmployeeInsightResult {
  return { generation, disposition, limits: limits() };
}

describe('Employee appraisal inline AI insight', () => {
  let fixture: ComponentFixture<EmployeeAppraisalInsightComponent>;
  let component: EmployeeAppraisalInsightComponent;
  let api: jasmine.SpyObj<AppraisalInsightService>;
  let roles: string[];
  let auth: { userId: string; hasRole: (p: string) => boolean };
  beforeEach(() => {
    for (const user of ['alice', 'david']) for (const id of [7, 8]) sessionStorage.removeItem(`ai-appraisal-employee:${user}:${id}`);
    roles = ['CAN_SUBMIT_ANNUAL_APPRAISAL']; auth = { userId: 'alice', hasRole: p => roles.includes(p) };
    api = jasmine.createSpyObj('AppraisalInsightService', ['employeeState', 'generateEmployee', 'employeeGeneration', 'submitted']);
    api.employeeState.and.callFake(id => of(state({ participantId: id })));
    api.generateEmployee.and.returnValue(of(result())); api.employeeGeneration.and.returnValue(of(result()));
    api.submitted.and.returnValue(of({ appraisalId: 10, submissionActivityId: 42, insight: insight() }));
    TestBed.configureTestingModule({ imports: [EmployeeAppraisalInsightComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: AuthService, useValue: auth }, { provide: AppraisalInsightService, useValue: api }
    ] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { AI_INSIGHT: { TITLE: 'AI Appraisal Insight', SUMMARY: 'Performance Summary', STRENGTHS: 'Key Strengths',
      IMPROVEMENTS: 'Areas for Improvement', TREND: 'Performance Trend', CONSIDERATIONS: 'Appraisal Considerations',
      PROMOTION: 'Promotion', SALARY: 'Salary Increment', GAPS: 'Information Gaps', NOT_AVAILABLE: 'Not available', FINAL: 'Final Performance Score' },
      EMPLOYEE_AI: { FICTIONAL: 'Fictional UAT Data', OUTDATED: 'Performance information has changed.', RECORDED: 'Exact submitted insight',
        INTRO: 'AI supports your review, not the final decision.', GENERATE: 'Generate Insight', REGENERATE: 'Regenerate' } });
    translate.use('en');
  });
  afterEach(() => { fixture?.destroy(); for (const user of ['alice', 'david']) for (const id of [7, 8]) sessionStorage.removeItem(`ai-appraisal-employee:${user}:${id}`); });
  function mount(inputs: Record<string, unknown> = {}) {
    fixture = TestBed.createComponent(EmployeeAppraisalInsightComponent); component = fixture.componentInstance;
    for (const [key, value] of Object.entries({ participantId: 7, employeeName: 'Fictional Bob', reviewPeriodName: '2022 UAT', ...inputs })) fixture.componentRef.setInput(key, value);
    fixture.detectChanges();
  }
  function root(): HTMLElement { fixture.detectChanges(); return fixture.nativeElement; }
  it('loads eligibility only, with no automatic generation or form-saving side effects', () => {
    mount(); expect(api.employeeState).toHaveBeenCalledOnceWith(7); expect(api.generateEmployee).not.toHaveBeenCalled();
    expect(root().textContent).toContain('Fictional Bob'); expect(root().textContent).toContain('2022 UAT'); expect(component.canGenerate).toBeTrue();
    component.generate(); expect(api.generateEmployee).toHaveBeenCalledOnceWith(7, jasmine.stringMatching(/^[0-9a-f-]{36}$/), false);
  });
  it('renders a cached six-section insight, with safe numeric emphasis and no Supporting Evidence', () => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight(), history: [insight()] }))); mount();
    expect(api.generateEmployee).not.toHaveBeenCalled(); expect(root().querySelectorAll('app-appraisal-insight-summary section').length).toBe(6);
    expect(root().querySelector('app-appraisal-insight-summary strong')?.textContent).toBe('80.25%');
    expect(root().querySelector('app-appraisal-insight-summary img')).toBeNull(); expect(root().textContent).toContain('<img');
    expect(root().textContent).toContain('Promotion readiness has insufficient evidence.');
    expect(root().querySelector('app-appraisal-insight-summary p')?.textContent).toContain('80.25%. Rating 4 / 5.');
    expect(root().querySelector('.supporting, .evidence')).toBeNull();
    expect(root().textContent).toContain('0.00%'); expect(root().textContent).toContain('Not available');
    expect(root().textContent).toContain('Final Performance Score'); expect(root().querySelector('time')?.getAttribute('datetime')).toBe('2026-11-07T02:00:00Z');
    expect(root().querySelector('.ai-disclaimer')?.textContent).toContain('AI is advisory');
  });
  it('uses explicit Regenerate for a fresh version and prevents duplicate clicks', () => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight(), history: [insight()] }))); const pending = new Subject<EmployeeInsightResult>();
    api.generateEmployee.and.returnValue(pending); mount(); component.generate(); component.generate();
    expect(api.generateEmployee.calls.count()).toBe(1); expect(api.generateEmployee.calls.mostRecent().args[2]).toBeTrue();
    expect(root().querySelector<HTMLButtonElement>('.employee-ai-actions button')?.disabled).toBeTrue();
    pending.next(result(insight({ status: 'RUNNING', summary: null, version: null }), 'ALREADY_IN_PROGRESS')); pending.complete();
    expect(component.displayed?.version).toBe(1); expect(component.pending).toBeTrue();
  });
  it('retains the previous valid version after a failed regeneration', () => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight(), history: [insight()] }))); mount();
    api.generateEmployee.and.returnValue(of(result(insight({ id: OTHER, status: 'FAILED', summary: null, version: null, errorCode: 'PROVIDER_UNAVAILABLE' }))));
    component.generate(); expect(component.displayed?.id).toBe(ID); expect(component.message).toBe('FAILED'); expect(component.canGenerate).toBeTrue();
  });
  it('shows outdated evidence without presenting it as the current valid insight', () => {
    api.employeeState.and.returnValue(of(state({ history: [insight({ outdated: true })] }))); mount();
    expect(root().textContent).toContain('Performance information has changed.'); component.generate();
    expect(api.generateEmployee.calls.mostRecent().args[2]).toBeFalse();
  });
  it('honours backend eligibility, permissions and fictional scope rejection', () => {
    api.employeeState.and.returnValue(of(state({ eligible: false, unavailableReason: 'RESULTS_INCOMPLETE' }))); mount();
    component.generate(); expect(api.generateEmployee).not.toHaveBeenCalled(); expect(component.canGenerate).toBeFalse();
    api.employeeState.and.returnValue(throwError(() => ({ status: 403 }))); component.load(); expect(root().querySelector('section')).toBeNull();
  });
  it('hides disabled environments and never infers authority from dashboard or role names', () => {
    roles = ['CAN_VIEW_ORGANISATION_PERFORMANCE', 'ROLE_SUPER_ADMIN']; mount();
    expect(api.employeeState).not.toHaveBeenCalled(); expect(root().querySelector('section')).toBeNull();
  });
  it('does not show a fictional card when the backend feature is disabled', () => {
    api.employeeState.and.returnValue(of(state({ eligible: false, unavailableReason: 'DISABLED' }))); mount();
    expect(root().querySelector('section')).toBeNull();
  });
  it('uses server time for cooldown, refreshes on expiry and never generates automatically', fakeAsync(() => {
    const server = Date.now() + 7200000;
    api.employeeState.and.returnValue(of(state({ limits: limits({ serverTime: new Date(server).toISOString(), cooldownUntil: new Date(server + 60000).toISOString() }) })));
    mount(); expect(component.remainingSeconds).toBe(60); expect(component.canGenerate).toBeFalse();
    api.employeeState.and.returnValue(of(state())); tick(59000); expect(component.remainingSeconds).toBe(1); tick(1000);
    expect(api.employeeState.calls.count()).toBe(2); expect(api.generateEmployee).not.toHaveBeenCalled(); expect(component.canGenerate).toBeTrue();
  }));
  it('preserves quota and provider backoff on reload and returns 429 timing in the card', () => {
    api.generateEmployee.and.returnValue(throwError(() => ({ status: 429, error: { retryAt: new Date(Date.now() + 120000).toISOString(),
      limits: limits({ remainingAttempts: 0, resetAt: new Date(Date.now() + 300000).toISOString() }) } })));
    mount(); component.generate(); expect(component.remainingSeconds).toBe(300); expect(component.limits?.remainingAttempts).toBe(0);
    expect(component.ticket).toBeNull(); expect(component.canGenerate).toBeFalse(); expect(component.message).toBe('LIMITED');
    api.employeeState.and.returnValue(of(state({ limits: limits({ remainingAttempts: 0, resetAt: new Date(Date.now() + 300000).toISOString() }) })));
    component.load(); expect(component.remainingSeconds).toBe(300); expect(component.canGenerate).toBeFalse();
  });
  it('handles queue saturation without an automatic retry or new idempotency key', () => {
    api.generateEmployee.and.returnValue(throwError(() => ({ status: 429, error: {}, headers: new HttpHeaders({ 'Retry-After': '5' }) })));
    mount(); component.generate(); expect(component.remainingSeconds).toBe(5); expect(component.message).toBe('LIMITED'); expect(api.generateEmployee.calls.count()).toBe(1);
  });
  it('recovers an uncertain POST with its original key, including after page return', () => {
    api.generateEmployee.and.returnValue(throwError(() => ({ status: 0 }))); mount(); component.generate();
    const key = api.generateEmployee.calls.mostRecent().args[1]; const stored = JSON.parse(sessionStorage.getItem(component.storageKey)!);
    expect(Object.keys(stored).sort()).toEqual(['generationId', 'operation', 'requestKey']); expect(stored.requestKey).toBe(key);
    fixture.destroy(); mount(); expect(component.paused).toBeTrue(); expect(api.generateEmployee.calls.count()).toBe(1);
    api.generateEmployee.and.returnValue(of(result())); component.check(); expect(api.generateEmployee.calls.mostRecent().args[1]).toBe(key);
  });
  it('polls for two minutes, offers Check Status, and cleans up polling on destroy', fakeAsync(() => {
    const running = insight({ status: 'RUNNING', summary: null, version: null });
    api.generateEmployee.and.returnValue(of(result(running))); api.employeeGeneration.and.callFake(() => of(result(running)));
    mount(); component.generate(); tick(119999); expect(component.paused).toBeFalse(); tick(1); expect(component.paused).toBeTrue();
    const count = api.employeeGeneration.calls.count(); tick(30000); expect(api.employeeGeneration.calls.count()).toBe(count);
    component.check(); expect(api.employeeGeneration.calls.count()).toBe(count + 1); fixture.destroy(); tick(6000);
    expect(api.employeeGeneration.calls.count()).toBe(count + 1); expect(api.generateEmployee.calls.count()).toBe(1);
  }));
  it('resumes an active generation from the state response without another POST', fakeAsync(() => {
    api.employeeState.and.returnValue(of(state({ latestAttempt: insight({ status: 'QUEUED', summary: null, version: null }) })));
    mount(); tick(3000); expect(api.employeeGeneration).toHaveBeenCalledOnceWith(7, ID); expect(api.generateEmployee).not.toHaveBeenCalled();
  }));
  it('ignores delayed responses from an earlier employee context', () => {
    const delayed = new Subject<EmployeeInsightState>(); api.employeeState.and.returnValue(delayed); mount();
    api.employeeState.and.returnValue(of(state({ participantId: 8 }))); fixture.componentRef.setInput('participantId', 8); fixture.detectChanges();
    delayed.next(state({ currentInsight: insight() })); expect(component.displayed).toBeNull(); expect(api.employeeState.calls.mostRecent().args).toEqual([8]);
  });
  it('reads only the exact submitted version for HR, across later record changes', () => {
    roles = ['CAN_REVIEW_ANNUAL_APPRAISAL']; auth.userId = 'david';
    mount({ readOnly: true, hrReview: true, appraisalId: 10, submissionActivityId: 42 });
    expect(api.submitted).toHaveBeenCalledOnceWith(10, 42); expect(api.employeeState).not.toHaveBeenCalled(); component.generate(); component.check();
    expect(api.generateEmployee).not.toHaveBeenCalled(); expect(root().querySelector('.employee-ai-actions')).toBeNull();
    expect(root().textContent).toContain('Exact submitted insight'); expect(component.displayed?.version).toBe(1);
    api.submitted.and.returnValue(of({ appraisalId: 10, submissionActivityId: 55, insight: insight({ id: OTHER, version: 2 }) }));
    fixture.componentRef.setInput('submissionActivityId', 55); fixture.detectChanges(); expect(component.displayed?.version).toBe(2);
  });
  it('keeps Superior submitted/approved views read-only and hides absent submitted insights', () => {
    api.submitted.and.returnValue(of({ appraisalId: 10, submissionActivityId: 42, insight: null }));
    mount({ readOnly: true, submittedOnly: true, appraisalId: 10, submissionActivityId: 42 }); expect(root().querySelector('section')).toBeNull();
    expect(api.employeeState).not.toHaveBeenCalled(); component.generate(); expect(api.generateEmployee).not.toHaveBeenCalled();
  });
  it('rejects mismatched submission/context responses and non-fictional content', () => {
    api.employeeState.and.returnValue(of(state({ participantId: 8, currentInsight: insight() }))); mount();
    expect(component.displayed).toBeNull(); expect(component.message).toBe('INVALID');
    api.employeeState.and.returnValue(of(state({ currentInsight: insight({ fictional: false }) }))); component.load(); expect(component.displayed).toBeNull();
  });
  it('does not expose content after an account switch', () => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight() }))); mount(); auth.userId = 'david';
    expect(root().querySelector('section')).toBeNull(); component.generate(); expect(api.generateEmployee).not.toHaveBeenCalled();
  });
  it('keeps a closed Draft insight viewable without mistaking it for a submitted version', () => {
    api.employeeState.and.returnValue(of(state({ eligible: false, unavailableReason: 'NOT_EDITABLE', currentInsight: insight(), history: [insight()] })));
    mount({ readOnly: true, appraisalId: 10 }); expect(component.displayed?.id).toBe(ID); expect(api.submitted).not.toHaveBeenCalled();
    component.generate(); expect(api.generateEmployee).not.toHaveBeenCalled(); expect(root().querySelector('.employee-ai-actions')).toBeNull();
    expect(root().querySelector('.submitted-insight-note')).toBeNull();
  });
  it('refreshes outdated evidence after an interrupted correction, retaining the historical text', fakeAsync(() => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight(), history: [insight()] }))); mount();
    api.generateEmployee.and.returnValue(of(result(insight({ id: OTHER, status: 'FAILED', summary: null, version: null, outdated: true, errorCode: 'EVIDENCE_CHANGED' }))));
    api.employeeState.and.returnValue(of(state({ history: [insight({ outdated: true })] }))); component.generate(); tick(0);
    expect(component.displayed?.id).toBe(ID); expect(component.displayed?.outdated).toBeTrue(); expect(root().textContent).toContain('Performance information has changed.');
  }));
  it('uses a new request key for explicit retries and leaves loading failures local to AI', () => {
    api.generateEmployee.and.returnValue(of(result(insight({ status: 'FAILED', summary: null, version: null, errorCode: 'TIMEOUT' })))); mount();
    component.generate(); const first = api.generateEmployee.calls.mostRecent().args[1]; component.generate();
    expect(api.generateEmployee.calls.mostRecent().args[1]).not.toBe(first);
    api.employeeState.and.returnValue(throwError(() => ({ status: 500 }))); component.load();
    expect(component.message).toBe('LOAD_ERROR'); expect(root().querySelector('[role="status"]')).not.toBeNull(); expect(component.canGenerate).toBeFalse();
  });
  it('resumes identifier-only stored polling and isolates it from other employees', fakeAsync(() => {
    sessionStorage.setItem('ai-appraisal-employee:alice:7', JSON.stringify({ operation: 'GENERATE', requestKey: ID, generationId: OTHER }));
    api.employeeGeneration.and.returnValue(of(result(insight({ id: OTHER })))); mount(); tick(3000);
    expect(api.employeeGeneration).toHaveBeenCalledOnceWith(7, OTHER);
    fixture.componentRef.setInput('participantId', 8); fixture.detectChanges(); expect(component.displayed).toBeNull(); expect(component.ticket).toBeNull();
    expect(api.generateEmployee).not.toHaveBeenCalled();
  }));
  it('rejects a different submitted appraisal or submission version', () => {
    roles = ['CAN_REVIEW_ANNUAL_APPRAISAL']; api.submitted.and.returnValue(of({ appraisalId: 11, submissionActivityId: 42, insight: insight() }));
    mount({ hrReview: true, readOnly: true, appraisalId: 10, submissionActivityId: 42 }); expect(component.displayed).toBeNull(); expect(component.message).toBe('INVALID');
    api.submitted.and.returnValue(of({ appraisalId: 10, submissionActivityId: 41, insight: insight() })); component.load(); expect(component.displayed).toBeNull();
  });
  for (const width of [1440, 768, 360]) it(`wraps the inline insight at ${width}px with accessible native controls and clear headings`, () => {
    api.employeeState.and.returnValue(of(state({ currentInsight: insight(), history: [insight()] }))); mount();
    const layout = reviewLayout(root(), width);
    try {
      expect(layout.root.scrollWidth).toBeLessThanOrEqual(width);
      const scores = layout.root.querySelector<HTMLElement>('.insight-scores')!;
      const columns = layout.window.getComputedStyle(scores).gridTemplateColumns.split(' ').length;
      expect(columns).toBe(width < 600 ? 1 : 3);
      const button = layout.root.querySelector<HTMLButtonElement>('.employee-ai-actions button')!;
      expect(button.type).toBe('button'); expect(button.disabled).toBeFalse();
      expect(layout.window.getComputedStyle(layout.root.querySelector('h3')!).fontWeight).toBe('700');
      expect(layout.root.querySelectorAll('app-appraisal-insight-summary h4').length).toBe(6);
      expect(layout.root.querySelector('[aria-live="polite"]')).not.toBeNull();
    } finally { layout.remove(); }
  });
});
