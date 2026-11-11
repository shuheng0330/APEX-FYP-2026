import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { DownOutline } from '@ant-design/icons-angular/icons';
import { of, Subject, throwError } from 'rxjs';
import { AppraisalInsight } from '../../models/appraisal-insight.model';
import { AuthService } from '../../services/auth.service';
import { AppraisalInsightService } from '../../services/appraisal-insight.service';
import { AppraisalInsightPreviewComponent } from './appraisal-insight-preview.component';

const generationId = '11111111-1111-4111-8111-111111111111';
const otherId = '22222222-2222-4222-8222-222222222222';
const requestKey = '33333333-3333-4333-8333-333333333333';
function result(patch: Partial<AppraisalInsight> = {}): AppraisalInsight {
  const statement = { text: 'Evidence is insufficient to assess promotion readiness.', evidenceIds: ['GAPS'] };
  return {
    id: generationId, scenario: 'LIMITED_EVIDENCE', status: 'COMPLETED', synthetic: true,
    templateVersion: 'appraisal-v1', model: 'test-model', createdAt: '2026-11-05T10:00:00Z', completedAt: '2026-11-05T10:00:01Z', errorCode: null,
    disclaimer: 'Review the supporting evidence before making an appraisal decision.',
    evidence: { periodName: 'Fictional period', startDate: '2025-01-01', endDate: '2025-12-31', requiredResultsComplete: false,
      frequency: 'QUARTERLY', consolidationMethod: 'FINAL_CHECKPOINT', kpiWeight: 50, attitudeWeight: 50,
      scores: { kpi: 0, attitude: null, finalAppraisal: null }, facts: [{ id: 'FACT1', scope: 'INDIVIDUAL', text: '<script>untrusted()</script>' }],
      history: [{ id: 'H1', periodName: 'Previous fictional period', startDate: '2024-01-01', endDate: '2024-12-31',
        scores: { kpi: 0, attitude: null, finalAppraisal: null }, comparable: false, comparisonNote: 'Different assessment basis.' }], informationGaps: ['Limited evidence.'] },
    summary: { performanceSummary: [statement], keyStrengths: [statement], areasForImprovement: [statement], performanceTrend: [statement],
      appraisalConsiderations: { promotionDiscussion: statement, salaryIncrementDiscussion: statement }, informationGaps: [statement] }, ...patch
  };
}

describe('Inline fictional AI appraisal preview', () => {
  let fixture: ComponentFixture<AppraisalInsightPreviewComponent>;
  let component: AppraisalInsightPreviewComponent;
  let api: jasmine.SpyObj<AppraisalInsightService>;
  let auth: { userId: string; hasRole: (p: string) => boolean };
  const storageKey = 'ai-appraisal-preview:alice';
  beforeEach(() => {
    sessionStorage.removeItem(storageKey); sessionStorage.removeItem('ai-appraisal-preview:david');
    api = jasmine.createSpyObj('AppraisalInsightService', ['scenarios', 'generate', 'get']);
    api.scenarios.and.returnValue(of([{ id: 'LIMITED_EVIDENCE', title: 'Limited evidence' }, { id: 'MIXED_RESULTS', title: 'Mixed results' }]));
    api.generate.and.returnValue(of(result())); api.get.and.returnValue(of(result()));
    auth = { userId: 'alice', hasRole: p => p === 'CAN_SUBMIT_ANNUAL_APPRAISAL' };
    TestBed.configureTestingModule({ imports: [AppraisalInsightPreviewComponent, TranslateModule.forRoot()], providers: [
      provideNoopAnimations(), { provide: AppraisalInsightService, useValue: api }, { provide: AuthService, useValue: auth },
      { provide: NZ_ICONS, useValue: [DownOutline] }
    ] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { AI_INSIGHT: { TITLE: 'AI Appraisal Insight', FICTIONAL: 'Fictional Preview',
      SUMMARY: 'Performance Summary', STRENGTHS: 'Key Strengths', IMPROVEMENTS: 'Areas for Improvement', TREND: 'Performance Trend',
      CONSIDERATIONS: 'Appraisal Considerations', PROMOTION: 'Promotion', SALARY: 'Salary Increment', GAPS: 'Information Gaps',
      NOT_AVAILABLE: 'Not available', NOT_COMPARABLE: 'Not comparable', GENERATED: 'Generated',
      KPI: 'KPI Score', ATTITUDE: 'Attitude Score', FINAL: 'Final Performance Score',
      VIEW_SUPPORT: 'View Supporting Evidence', HIDE_SUPPORT: 'Hide Supporting Evidence',
      SOURCE_GAPS: 'Evidence Limitations', FICTIONAL_NOTICE: 'Fictional data only. Not saved to an employee appraisal.' } }); translate.use('en');
  });
  afterEach(() => { fixture?.destroy(); sessionStorage.removeItem(storageKey); sessionStorage.removeItem('ai-appraisal-preview:david'); });
  function mount() { fixture = TestBed.createComponent(AppraisalInsightPreviewComponent); component = fixture.componentInstance; fixture.detectChanges(); }
  function generate() { component.scenario = 'LIMITED_EVIDENCE'; component.generate(); fixture.detectChanges(); }
  it('requires an explicit generation and renders six readable sections with a visible disclaimer', () => {
    mount(); expect(api.generate).not.toHaveBeenCalled(); expect(component.scenario).toBeNull(); generate();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('app-appraisal-insight-summary section').length).toBe(6);
    expect(root.textContent).toContain('Fictional Preview'); expect(root.textContent).toContain('Promotion'); expect(root.textContent).toContain('Salary Increment');
    expect(root.querySelector('.ai-disclaimer')?.textContent).toContain('Review the supporting evidence');
    expect(root.querySelector('#ai-insight-evidence')).toBeNull();
  });
  it('uses bold titles and headings while keeping narrative text at normal weight', () => {
    mount(); generate(); component.supporting = true; fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const headings = root.querySelectorAll('.insight-heading h3, app-appraisal-insight-summary h4, app-appraisal-insight-summary h5, .evidence h4');
    expect(headings.length).toBe(13);
    for (const heading of headings) {
      expect(getComputedStyle(heading).fontWeight).withContext(heading.textContent ?? '').toBe('700');
    }
    expect(Number(getComputedStyle(root.querySelector('app-appraisal-insight-summary p')!).fontWeight)).toBeLessThan(700);
  });
  it('shows the returned scores once, above the summary, with zero distinct from missing results', () => {
    mount(); generate(); const root: HTMLElement = fixture.nativeElement;
    const scores = root.querySelector<HTMLElement>('.insight-scores');
    expect(scores).not.toBeNull();
    expect(scores?.textContent).toContain('KPI Score'); expect(scores?.textContent).toContain('0.00%');
    expect(scores?.textContent).toContain('Attitude Score'); expect(scores?.textContent).toContain('Final Performance Score');
    expect(scores?.querySelectorAll('dd').length).toBe(3);
    expect(scores?.querySelectorAll('dd')[1].textContent).toContain('Not available');
    expect(scores?.querySelectorAll('dd')[2].textContent).toContain('Not available');
    expect(scores && (scores.compareDocumentPosition(root.querySelector('app-appraisal-insight-summary')!) & Node.DOCUMENT_POSITION_FOLLOWING)).toBeTruthy();
    component.supporting = true; fixture.detectChanges();
    expect(root.querySelectorAll('.insight-scores').length).toBe(1);
    expect(root.querySelector('#ai-insight-evidence .insight-scores, #ai-insight-evidence .evidence-scores')).toBeNull();
    expect(root.textContent).not.toContain('Final Appraisal Score');
  });
  it('formats backend scores without recomputing them and labels the backend completion timestamp', () => {
    const supplied = result(); supplied.evidence.scores = { kpi: 61.234, attitude: 72.5, finalAppraisal: 12.34 };
    api.generate.and.returnValue(of(supplied)); mount(); generate();
    const root: HTMLElement = fixture.nativeElement;
    const values = [...root.querySelectorAll('.insight-scores dd')].map(value => value.textContent?.trim());
    expect(values).toEqual(['61.23%', '72.50%', '12.34%']);
    const time = root.querySelector('time');
    expect(time?.getAttribute('datetime')).toBe(supplied.completedAt);
    expect(time?.textContent?.trim()).toBeTruthy();
    expect(root.querySelector('.generated-at')?.textContent).toContain('Generated');
    expect(root.querySelector('.fictional-note')?.textContent).toContain('Fictional data only');
  });
  it('keeps evidence collapsed after regeneration and preserves all supporting facts, history and limitations', () => {
    mount(); generate(); const root: HTMLElement = fixture.nativeElement;
    const toggle: HTMLButtonElement = root.querySelector('.text-action')!;
    toggle.focus(); expect(document.activeElement).toBe(toggle);
    expect(toggle.textContent).toContain('View Supporting Evidence');
    toggle.click(); fixture.detectChanges();
    expect(toggle.textContent).toContain('Hide Supporting Evidence');
    expect(root.querySelector('#ai-insight-evidence')?.textContent).toContain('Evidence Limitations');
    expect(root.querySelector('#ai-insight-evidence')?.textContent).toContain('Limited evidence.');
    expect(root.querySelector('#ai-insight-evidence')?.textContent).toContain('Previous fictional period');
    expect(root.querySelector('#ai-insight-evidence')?.textContent).toContain('Different assessment basis.');
    component.generate(); fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(root.querySelector('#ai-insight-evidence')).toBeNull();
  });
  it('preserves full plain-text summary content and separate appraisal consideration subsections', () => {
    const supplied = result();
    supplied.summary!.keyStrengths = [{ text: 'First line\nSecond line <img src=x onerror=alert(1)>', evidenceIds: ['FACT1'] }];
    api.generate.and.returnValue(of(supplied)); mount(); generate();
    const summary: HTMLElement = fixture.nativeElement.querySelector('app-appraisal-insight-summary');
    expect(summary.textContent).toContain('First line\nSecond line <img src=x onerror=alert(1)>');
    expect(summary.querySelector('img')).toBeNull();
    expect([...summary.querySelectorAll('.consideration-grid h5')].map(h => h.textContent?.trim())).toEqual(['Promotion', 'Salary Increment']);
    expect(getComputedStyle(summary.querySelector('li')!).whiteSpace).toBe('pre-wrap');
  });
  it('reveals evidence by keyboard-operable button, preserving zeroes and rendering plain text', () => {
    mount(); generate(); const button: HTMLButtonElement = fixture.nativeElement.querySelector('.text-action');
    expect(button.type).toBe('button'); expect(button.getAttribute('aria-expanded')).toBe('false');
    button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    const evidence: HTMLElement = fixture.nativeElement.querySelector('#ai-insight-evidence');
    expect(evidence.textContent).toContain('0.00%'); expect(evidence.textContent).toContain('Not available');
    expect(evidence.textContent).toContain('Not comparable'); expect(evidence.textContent).toContain('<script>untrusted()</script>');
    expect(evidence.querySelector('script')).toBeNull(); button.click(); fixture.detectChanges(); expect(fixture.nativeElement.querySelector('#ai-insight-evidence')).toBeNull();
  });
  it('does not expose the preview to dashboard-only viewers', () => {
    auth.hasRole = p => p === 'CAN_VIEW_ORGANISATION_PERFORMANCE'; mount();
    expect(api.scenarios).not.toHaveBeenCalled(); expect(fixture.nativeElement.querySelector('.insight-card')).toBeNull();
  });
  it('allows HR and displays a local disabled-environment state without generating', () => {
    auth.hasRole = p => p === 'CAN_REVIEW_ANNUAL_APPRAISAL'; api.scenarios.and.returnValue(throwError(() => ({ status: 400 })));
    mount(); expect(component.unavailable).toBeTrue(); expect(api.generate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.notice')).not.toBeNull(); expect(fixture.nativeElement.querySelector('.ai-disclaimer')).not.toBeNull();
  });
  it('prevents duplicate clicks, polls only reads, and stops after completion', fakeAsync(() => {
    const pending = new Subject<AppraisalInsight>(); api.generate.and.returnValue(pending); mount(); generate(); component.generate();
    expect(api.generate).toHaveBeenCalledTimes(1); pending.next(result({ status: 'QUEUED', summary: null })); pending.complete();
    tick(3000); expect(api.get).toHaveBeenCalledOnceWith(generationId); tick(9000); expect(api.get).toHaveBeenCalledTimes(1);
    expect(component.current?.status).toBe('COMPLETED');
  }));
  it('pauses after two minutes, allows a status check and cleans up on departure', fakeAsync(() => {
    api.generate.and.returnValue(of(result({ status: 'QUEUED', summary: null })));
    api.get.and.returnValue(of(result({ status: 'RUNNING', summary: null }))); mount(); generate(); tick(120000);
    expect(component.paused).toBeTrue(); const count = api.get.calls.count(); tick(6000); expect(api.get.calls.count()).toBe(count);
    component.check(); expect(api.get.calls.count()).toBe(count + 1); fixture.destroy(); tick(6000); expect(api.get.calls.count()).toBe(count + 1);
  }));
  it('recovers an uncertain POST with its original key and stores identifiers only', () => {
    api.generate.and.returnValue(throwError(() => ({ status: 0 }))); mount(); generate();
    const key = api.generate.calls.first().args[1]; expect(component.paused).toBeTrue();
    api.generate.and.returnValue(of(result())); component.check();
    expect(api.generate.calls.mostRecent().args).toEqual(['LIMITED_EVIDENCE', key]);
    expect(JSON.parse(sessionStorage.getItem(storageKey)!)).toEqual({ scenario: 'LIMITED_EVIDENCE', requestKey: key, generationId });
  });
  it('resumes a saved generation by GET and keeps accounts isolated', () => {
    sessionStorage.setItem(storageKey, JSON.stringify({ scenario: 'LIMITED_EVIDENCE', requestKey, generationId }));
    mount(); expect(api.get).toHaveBeenCalledOnceWith(generationId); expect(api.generate).not.toHaveBeenCalled();
    fixture.destroy(); auth.userId = 'david'; api.get.calls.reset(); mount(); expect(api.get).not.toHaveBeenCalled(); expect(component.displayed).toBeNull();
  });
  it('does not automatically repeat a request whose response was lost on departure', () => {
    sessionStorage.setItem(storageKey, JSON.stringify({ scenario: 'LIMITED_EVIDENCE', requestKey, generationId: null }));
    mount(); expect(component.message).toBe('RECOVER'); expect(api.generate).not.toHaveBeenCalled();
    component.check(); expect(api.generate).toHaveBeenCalledOnceWith('LIMITED_EVIDENCE', requestKey);
  });
  it('keeps the previous successful example through a failed new generation and uses a new key', () => {
    mount(); generate(); const firstKey = api.generate.calls.first().args[1];
    component.scenario = 'MIXED_RESULTS'; api.generate.and.returnValue(of(result({ id: otherId, scenario: 'MIXED_RESULTS', status: 'FAILED', summary: null, errorCode: 'INTERRUPTED' })));
    component.generate(); fixture.detectChanges(); expect(api.generate.calls.mostRecent().args[1]).not.toBe(firstKey);
    expect(component.message).toBe('INTERRUPTED'); expect(component.displayed?.scenario).toBe('LIMITED_EVIDENCE');
    expect(fixture.nativeElement.querySelector('.generated-heading').textContent).toContain('Limited evidence');
    expect(component.evidence).toEqual(result().evidence);
    expect(fixture.nativeElement.querySelector('.insight-scores').textContent).toContain('0.00%');
    expect(fixture.nativeElement.querySelector('time').getAttribute('datetime')).toBe(result().completedAt);
  });
  it('rejects non-fictional or mismatched responses without rendering their content', () => {
    api.generate.and.returnValue(of(result({ synthetic: false }))); mount(); generate();
    expect(component.unavailable).toBeTrue(); expect(component.displayed).toBeNull();
    expect(fixture.nativeElement.querySelector('app-appraisal-insight-summary')).toBeNull();
  });
  it('recovers a failed status read with GET and never generates a replacement automatically', () => {
    sessionStorage.setItem(storageKey, JSON.stringify({ scenario: 'LIMITED_EVIDENCE', requestKey, generationId }));
    api.get.and.returnValue(throwError(() => ({ status: 503 }))); mount();
    expect(component.message).toBe('STATUS_ERROR'); api.get.and.returnValue(of(result())); component.check();
    expect(api.get).toHaveBeenCalledTimes(2); expect(api.generate).not.toHaveBeenCalled();
  });
  it('ignores malformed saved requests and refuses inaccessible generations', () => {
    sessionStorage.setItem(storageKey, JSON.stringify({ scenario: 'LIMITED_EVIDENCE', requestKey: 'invalid', generationId }));
    mount(); expect(api.get).not.toHaveBeenCalled(); expect(component.ticket).toBeNull();
    fixture.destroy(); sessionStorage.setItem(storageKey, JSON.stringify({ scenario: 'LIMITED_EVIDENCE', requestKey, generationId }));
    api.get.and.returnValue(throwError(() => ({ status: 404 }))); mount();
    expect(component.unavailable).toBeTrue(); expect(api.generate).not.toHaveBeenCalled();
  });
  it('wraps the inline card within a narrow host and keeps long content readable', () => {
    mount(); generate(); const root: HTMLElement = fixture.nativeElement; root.style.width = '360px';
    component.supporting = true; fixture.detectChanges();
    const card: HTMLElement = root.querySelector('.insight-card')!;
    expect(card.getBoundingClientRect().width).toBeLessThanOrEqual(362);
    expect(getComputedStyle(card).overflowWrap).toBe('anywhere');
    expect(root.querySelector('.insight-controls nz-select')!.getBoundingClientRect().right).toBeLessThanOrEqual(card.getBoundingClientRect().right);
  });
  it('aligns controls and score summaries on desktop and stacks them without page overflow on mobile', () => {
    mount(); generate(); component.supporting = true; fixture.detectChanges();
    const frame = document.createElement('iframe'); frame.title = 'Responsive fictional AI insight';
    frame.style.cssText = 'position:absolute;left:-10000px;height:1600px;border:0'; document.body.appendChild(frame);
    try {
      const doc = frame.contentDocument!;
      for (const style of document.querySelectorAll('style')) doc.head.appendChild(style.cloneNode(true));
      doc.body.style.margin = '0'; doc.body.appendChild(fixture.nativeElement.cloneNode(true));
      for (const [width, scoreColumns, direction] of [[1280, 3, 'row'], [768, 3, 'row'], [360, 1, 'column']] as const) {
        frame.style.width = width + 'px';
        const scores = doc.querySelector<HTMLElement>('.insight-scores')!;
        expect(scores).withContext(String(width)).not.toBeNull();
        expect(frame.contentWindow!.getComputedStyle(scores).gridTemplateColumns.split(' ').length).withContext(String(width)).toBe(scoreColumns);
        const controls = doc.querySelector<HTMLElement>('.insight-controls')!;
        expect(frame.contentWindow!.getComputedStyle(controls).flexDirection).withContext(String(width)).toBe(direction);
        const card = doc.querySelector<HTMLElement>('.insight-card')!;
        expect(card.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(card.clientWidth + 1);
        expect(doc.body.scrollWidth).withContext(String(width)).toBeLessThanOrEqual(width + 1);
      }
    } finally { frame.remove(); }
  });
  it('clears visible results after access is lost and does not continue polling', fakeAsync(() => {
    api.generate.and.returnValue(of(result({ status: 'QUEUED', summary: null }))); mount(); generate();
    auth.userId = 'another-account'; tick(6000); fixture.detectChanges();
    expect(api.get).not.toHaveBeenCalled(); expect(fixture.nativeElement.querySelector('.insight-card')).toBeNull();
  }));
});
