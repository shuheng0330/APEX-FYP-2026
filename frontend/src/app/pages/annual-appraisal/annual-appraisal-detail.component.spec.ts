import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { CloseOutline, DownOutline, LeftOutline, RightOutline, LoadingOutline } from '@ant-design/icons-angular/icons';
import { Observable, of, Subject, throwError } from 'rxjs';
import { AnnualAppraisalService } from '../../services/annual-appraisal.service';
import { AuthService } from '../../services/auth.service';
import { AnnualAppraisal, AnnualAppraisalCandidate, AnnualAppraisalReadiness, AnnualAppraisalSnapshot, AnnualPerformanceHistory } from '../../models/annual-appraisal.model';
import { KpiPeriodContext } from '../../models/kpi-plan.model';
import { AnnualAppraisalDetailComponent } from './annual-appraisal-detail.component';

import { appraisal, candidate, performance, period, readiness, clearAppraisalNavigationState } from './annual-appraisal-test-fixtures';

describe('Full-page annual appraisal details', () => {
  let fixture: ComponentFixture<AnnualAppraisalDetailComponent>;
  let component: AnnualAppraisalDetailComponent;
  let api: jasmine.SpyObj<AnnualAppraisalService>;
  let modal: jasmine.SpyObj<NzModalService>;
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
    modal = jasmine.createSpyObj('NzModalService', ['confirm']);
    await TestBed.configureTestingModule({ imports: [AnnualAppraisalDetailComponent, TranslateModule.forRoot()], providers: [
      provideRouter([]), provideNoopAnimations(), { provide: ActivatedRoute, useValue: route },
      { provide: AnnualAppraisalService, useValue: api }, { provide: AuthService, useValue: { userId: hr ? 'david' : 'alice', hasRole: (p: string) => permissions.includes(p) } },
      { provide: NZ_ICONS, useValue: [CloseOutline, DownOutline, LeftOutline, RightOutline, LoadingOutline] }
    ] }).compileComponents();
    TestBed.overrideProvider(NzModalService, { useValue: modal });
    sessionStorage.removeItem(`annual-appraisal-period:${hr ? 'hr:david' : 'team:alice'}`);
    fixture = TestBed.createComponent(AnnualAppraisalDetailComponent); component = fixture.componentInstance; fixture.detectChanges();
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
  });
  it('saves incomplete Drafts before readiness, including after a validation attempt', async () => {
    await setup(); open(candidate({ appraisalId: null, status: null }));
    component.readiness = readiness(false); component.showValidation = true;
    component.recommendation = null; component.justification = ''; component.saveDraft();
    expect(api.create).toHaveBeenCalledWith({ participantId: 7, recommendation: null, justification: '' });
    expect(api.submit).not.toHaveBeenCalled();
  });
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
    component.finalDecision = 'NO_RECOMMENDATION'; component.confirmDecision('OVERRIDE_AND_APPROVE');
    api.overrideApprove.and.returnValue(throwError(() => ({ status: 403 }))); answerConfirmation(true);
    expect(api.overrideApprove).toHaveBeenCalledWith(10, { expectedLockVersion: 1, finalDecision: 'NO_RECOMMENDATION', reason: 'Reason' });
    expect(component.reason).toBe('Reason'); expect(component.accessChanged).toBeTrue();
  });
  it('requires a return reason and sends the return without changing the recommendation', async () => {
    await setup(true); api.get.and.returnValue(of(appraisal({ status: 'PENDING_REVIEW', allowedActions: ['RETURN'] })));
    open(candidate()); component.beginDecision('RETURN'); component.confirmDecision('RETURN'); expect(modal.confirm).not.toHaveBeenCalled();
    component.reason = 'Please explain'; api.returnForRevision.and.returnValue(of(appraisal({ status: 'RETURNED', allowedActions: [] })));
    component.confirmDecision('RETURN'); answerConfirmation(true);
    expect(api.returnForRevision).toHaveBeenCalledWith(10, { expectedLockVersion: 1, reason: 'Please explain' });
    expect(component.reason).toBe(''); expect(component.selected?.status).toBe('RETURNED');
  });
  it('approves the submitted recommendation and switches to final read-only results', async () => {
    await setup(true);
    const pending = appraisal({ status: 'PENDING_REVIEW', snapshot: performance(), allowedActions: ['APPROVE', 'RETURN', 'OVERRIDE_AND_APPROVE'] });
    api.get.and.returnValue(of(pending)); open(candidate());
    api.approve.and.returnValue(of({ ...pending, status: 'APPROVED', finalDecision: 'SALARY_INCREMENT', allowedActions: [], lockVersion: 2 }));
    component.confirmDecision('APPROVE'); expect(modal.confirm.calls.mostRecent().args[0]?.nzContent).toBe('ANNUAL_APPRAISAL.REVIEW_CONFIRM');
    answerConfirmation(true);
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
    component.confirmDecision('OVERRIDE_AND_APPROVE');
    expect(modal.confirm.calls.mostRecent().args[0]?.nzContent).toBe('ANNUAL_APPRAISAL.REVIEW_LATE_HELP'); answerConfirmation(true);
    expect(component.selected?.recommendation).toBe('SALARY_INCREMENT'); expect(component.selected?.finalDecision).toBe('NO_RECOMMENDATION');
    expect(component.selected?.overrideReason).toBe('Budget constraints');
  });
  it('keeps approval read-only, renders repeated activity chronologically and does not infer actors', async () => {
    await setup(true); api.get.and.returnValue(of(appraisal({ status: 'APPROVED', snapshot: performance(), allowedActions: [],
      activity: [2, 1, 3].map(id => ({ id, action: 'RETURNED', actorId: 'recorded-id', occurredAt: `2026-10-${id + 10}T10:00:00Z`,
        reason: 'Return ' + id, recommendation: 'SALARY_INCREMENT', justification: 'Saved text', finalDecision: null, snapshot: null })) })));
    open(candidate()); expect(component.events.map(e => e.id)).toEqual([1, 2, 3]); expect(component.actor('recorded-id')).toBe('recorded-id');
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
      }
    } finally { frame.remove(); }
  });
});
