import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AnnualAppraisalService } from './annual-appraisal.service';

describe('Annual appraisal API', () => {
  let api: AnnualAppraisalService;
  let http: HttpTestingController;
  const base = `${environment.apiBaseUrl}/annual-appraisals`;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AnnualAppraisalService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads each workspace through the appropriate scoped read endpoints', () => {
    api.periods(false).subscribe(); http.expectOne(`${base}/periods?hrReview=false`).flush([]);
    api.periods(true).subscribe(); http.expectOne(`${base}/periods?hrReview=true`).flush([]);
    api.team(2).subscribe(); http.expectOne(`${base}/team?reviewPeriodId=2`).flush([]);
    api.reviews(2).subscribe(); http.expectOne(`${base}/reviews?reviewPeriodId=2`).flush([]);
    api.readiness(7).subscribe(); const readiness = http.expectOne(`${base}/participants/7/readiness`);
    expect(readiness.request.method).toBe('GET'); expect(readiness.request.withCredentials).toBeTrue();
    expect(readiness.request.headers.get('X-Skip-Error-Handler')).toBe('true'); readiness.flush({});
    api.get(10).subscribe(); http.expectOne(`${base}/10`).flush({});
    api.history(7).subscribe(); http.expectOne(`${base}/participants/7/history`).flush([]);
    api.performanceHistory(7).subscribe(); const trend = http.expectOne(`${base}/participants/7/performance-history`);
    expect(trend.request.method).toBe('GET'); expect(trend.request.withCredentials).toBeTrue();
    expect(trend.request.headers.get('X-Skip-Error-Handler')).toBe('true'); trend.flush([]);
  });
  it('sends only editable input and optimistic tokens, not calculated scores', () => {
    const input = { participantId: 7, recommendation: null, justification: null };
    api.create(input).subscribe(); const create = http.expectOne(base);
    expect(create.request.method).toBe('POST'); expect(create.request.body).toEqual(input); create.flush({});
    const update = { ...input, expectedLockVersion: 3 };
    api.update(10, update).subscribe(); const save = http.expectOne(`${base}/10`);
    expect(save.request.method).toBe('PUT'); expect(save.request.body).toEqual(update); save.flush({});
  });
  it('uses dedicated transition endpoints with the supplied concurrency token', () => {
    const token = { expectedLockVersion: 4 };
    api.submit(10, token).subscribe(); const submit = http.expectOne(`${base}/10/submit`);
    expect(submit.request.body).toEqual(token); expect(submit.request.method).toBe('POST'); submit.flush({});
    api.approve(10, token).subscribe(); const approve = http.expectOne(`${base}/10/approve`);
    expect(approve.request.body).toEqual(token); approve.flush({});
    api.overrideApprove(10, { ...token, finalDecision: 'NO_RECOMMENDATION', reason: 'Budget' }).subscribe();
    const override = http.expectOne(`${base}/10/override-approve`);
    expect(override.request.body.finalDecision).toBe('NO_RECOMMENDATION'); expect(override.request.body.reason).toBe('Budget'); override.flush({});
    api.returnForRevision(10, { ...token, reason: 'Explain delivery' }).subscribe(); const returned = http.expectOne(`${base}/10/return`);
    expect(returned.request.body).toEqual({ ...token, reason: 'Explain delivery' }); returned.flush({});
  });
});
