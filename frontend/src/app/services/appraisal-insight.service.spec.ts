import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AppraisalInsightService } from './appraisal-insight.service';

describe('Synthetic appraisal preview API', () => {
  let api: AppraisalInsightService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/annual-appraisal-insights/synthetic`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AppraisalInsightService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('reads scenarios and status without triggering generation', () => {
    api.scenarios().subscribe(); const scenarios = http.expectOne(`${base}/scenarios`);
    expect(scenarios.request.method).toBe('GET'); scenarios.flush([]);
    api.get('example-id').subscribe(); const status = http.expectOne(`${base}/generations/example-id`);
    expect(status.request.method).toBe('GET'); expect(status.request.withCredentials).toBeTrue();
    expect(status.request.headers.get('X-Skip-Loading')).toBe('true');
    expect(status.request.headers.get('X-Skip-Error-Handler')).toBe('true'); status.flush({});
  });
  it('sends no employee data or request body and preserves the supplied idempotency key', () => {
    api.generate('LIMITED_EVIDENCE', 'request-key').subscribe();
    const request = http.expectOne(`${base}/scenarios/LIMITED_EVIDENCE/generations`);
    expect(request.request.method).toBe('POST'); expect(request.request.body).toBeNull();
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.headers.get('Idempotency-Key')).toBe('request-key'); request.flush({});
  });
  it('uses the scoped employee endpoints with no evidence body or automatic generation', () => {
    const root = `${environment.apiBaseUrl}/annual-appraisals/participants/7/ai-insights`;
    api.employeeState(7).subscribe(); const state = http.expectOne(root);
    expect(state.request.method).toBe('GET'); state.flush({});
    for (const regenerate of [false, true]) {
      api.generateEmployee(7, 'same-request-key', regenerate).subscribe();
      const request = http.expectOne(root + (regenerate ? '/regenerate' : ''));
      expect(request.request.method).toBe('POST'); expect(request.request.body).toBeNull();
      expect(request.request.headers.get('Idempotency-Key')).toBe('same-request-key');
      expect(request.request.headers.get('X-Skip-Error-Handler')).toBe('true'); request.flush({});
    }
    api.employeeGeneration(7, 'generation-id').subscribe();
    const status = http.expectOne(`${root}/generations/generation-id`);
    expect(status.request.method).toBe('GET'); status.flush({});
  });
  it('reads the exact selected submitted insight without using employee generation endpoints', () => {
    api.submitted(10, 42).subscribe();
    const request = http.expectOne(`${environment.apiBaseUrl}/annual-appraisals/10/ai-insight?submissionActivityId=42`);
    expect(request.request.method).toBe('GET'); expect(request.request.withCredentials).toBeTrue(); request.flush({});
    api.submitted(10).subscribe(); http.expectOne(`${environment.apiBaseUrl}/annual-appraisals/10/ai-insight`).flush({});
  });
});
